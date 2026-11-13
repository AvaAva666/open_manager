package com.automanager.vehicle;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;

import com.manager.utils.CLog;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 车辆数据消费端封装（开源 SDK）：绑定宿主提供的车辆数据服务，
 * 注册监听接收变化播报、缓存最新快照，并把 Binder 线程的播报切到主线程分发给业务监听者。
 *
 * 数据从哪来：宿主侧采集端通过内部上报通道把车辆信号写入服务，消费端只读不写；
 * 本 SDK 不包含、也不暴露任何上报接口。
 *
 * 用法：
 * <pre>
 *   VehicleDataManager mgr = VehicleDataManager.getInstance();
 *   mgr.connect(context);   // 按 action 隐式绑定宿主服务
 *   // 跨应用消费时显式指定宿主包名：
 *   // mgr.connect(context, "com.automanager", null);
 *   mgr.addListener(new OnVehicleDataListener() {
 *       public void onVehicleDataChanged(VehicleData data) {
 *           if (data.hasField(VehicleData.FIELD_VEHICLE_SPEED)) {
 *               // data.vehicleSpeed ...
 *           }
 *       }
 *   });
 *   // 不再需要时（如 Application/页面销毁）：
 *   mgr.removeListener(listener);
 *   mgr.disconnect();
 * </pre>
 *
 * 连接保活：BIND_AUTO_CREATE 绑定后，服务进程被杀/重启时系统会自动重连，
 * {@link #onServiceConnected} 每次都会重新注册监听，无需业务关心。
 */
public class VehicleDataManager {

    private static final String TAG = "VehicleDataManager";

    /** 宿主车辆数据服务的绑定 action（消费通道） */
    public static final String ACTION_VEHICLE_DATA_SERVICE =
            "com.automanager.vehicle.action.VEHICLE_DATA_SERVICE";

    /** 断连自动重连：首次延迟 1s，指数退避翻倍，封顶 30s；连接成功后重置 */
    private static final long RECONNECT_BASE_DELAY_MS = 1000L;
    private static final long RECONNECT_MAX_DELAY_MS = 30000L;

    private static volatile VehicleDataManager sInstance;

    public static VehicleDataManager getInstance() {
        if (sInstance == null) {
            synchronized (VehicleDataManager.class) {
                if (sInstance == null) {
                    sInstance = new VehicleDataManager();
                }
            }
        }
        return sInstance;
    }

    private VehicleDataManager() {
    }

    /** 业务监听者：回调始终在主线程 */
    public interface OnVehicleDataListener {
        void onVehicleDataChanged(VehicleData data);
    }

    private Context mAppContext;
    /** 显式绑定组件；null 时按 action 隐式绑定 */
    private ComponentName mTargetComponent;
    /** 隐式绑定时 setPackage 指定的包名；null 表示用自身包名 */
    private String mTargetPackage;
    private boolean mBound;
    /** 业务是否已主动 disconnect：为 true 后不再自动重连 */
    private boolean mUserDisconnected;
    /** 自动重连累计尝试次数（指数退避，连接成功后重置） */
    private int mReconnectAttempts;
    /** Binder 回调线程写、业务线程读，需可见性保证 */
    private volatile IVehicleDataService mRemote;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<OnVehicleDataListener> mListeners =
            new CopyOnWriteArrayList<OnVehicleDataListener>();

    private final Object mCacheLock = new Object();
    private VehicleData mCache;

    // ---------------- 连接管理 ----------------

    /** 按 action 隐式绑定宿主车辆数据服务 */
    public void connect(Context context) {
        connect(context, null, null);
    }

    /**
     * 显式指定宿主包名绑定（跨应用消费时使用；服务类名由宿主 action 暴露，className 可传 null）。
     *
     * @param packageName 宿主包名；null 表示按 action 隐式绑定
     * @param className   服务类全限定名；null 表示仅按包名 + action
     */
    public void connect(Context context, String packageName, String className) {
        if (context == null) {
            CLog.w(TAG, "connect: context is null");
            return;
        }
        synchronized (this) {
            mAppContext = context.getApplicationContext();
            if (packageName != null && className != null) {
                mTargetComponent = new ComponentName(packageName, className);
                mTargetPackage = null;
            } else {
                mTargetComponent = null;
                mTargetPackage = packageName;
            }
            mUserDisconnected = false;
            cancelReconnectLocked();
            if (mBound) {
                return;
            }
            bindOnceLocked();
        }
    }

    /** 按已记录的绑定目标发起一次 bindService（调用方需持有 this 锁） */
    private void bindOnceLocked() {
        Intent intent;
        if (mTargetComponent != null) {
            intent = new Intent();
            intent.setComponent(mTargetComponent);
        } else {
            intent = new Intent(ACTION_VEHICLE_DATA_SERVICE);
            if (mTargetPackage != null) {
                intent.setPackage(mTargetPackage);
            } else {
                // 同应用消费时限定本包，Android 5.0+ 隐式 bindService 要求可解析
                intent.setPackage(mAppContext.getPackageName());
            }
        }
        boolean ok;
        try {
            ok = mAppContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            CLog.e(TAG, "bindService failed", e);
            ok = false;
        }
        if (ok) {
            mBound = true;
            CLog.i(TAG, "bindService requested: "
                    + (mTargetComponent != null ? mTargetComponent : ACTION_VEHICLE_DATA_SERVICE));
        } else {
            CLog.e(TAG, "bindService returned false (is the host service installed?)");
            // 绑定失败（服务未就绪等）同样视为断连，安排重试
            if (!mUserDisconnected) {
                scheduleReconnectLocked();
            }
        }
    }

    /** 安排下一次自动重连（指数退避；调用方需持有 this 锁） */
    private void scheduleReconnectLocked() {
        mReconnectAttempts++;
        long shift = Math.min(mReconnectAttempts, 5);
        long delay = RECONNECT_BASE_DELAY_MS << shift;
        if (delay > RECONNECT_MAX_DELAY_MS) {
            delay = RECONNECT_MAX_DELAY_MS;
        }
        CLog.w(TAG, "schedule reconnect in " + delay + "ms (attempt " + mReconnectAttempts + ")");
        mMainHandler.removeCallbacks(mReconnectTask);
        mMainHandler.postDelayed(mReconnectTask, delay);
    }

    /** 取消待执行的自动重连并重置退避（调用方需持有 this 锁） */
    private void cancelReconnectLocked() {
        mReconnectAttempts = 0;
        mMainHandler.removeCallbacks(mReconnectTask);
    }

    /** 解绑服务；业务监听者不会自动清除，如需移除请先 {@link #removeListener} */
    public void disconnect() {
        synchronized (this) {
            // 标记主动断开，之后不再自动重连
            mUserDisconnected = true;
            cancelReconnectLocked();
            if (!mBound) {
                mRemote = null;
                return;
            }
            unregisterRemoteListener();
            try {
                mAppContext.unbindService(mConnection);
            } catch (Exception e) {
                CLog.e(TAG, "unbindService failed", e);
            }
            mBound = false;
            mRemote = null;
        }
        synchronized (mCacheLock) {
            mCache = null;
        }
    }

    /** 服务是否已连接（远端接口可用） */
    public boolean isConnected() {
        return mRemote != null;
    }

    // ---------------- 业务监听 ----------------

    /**
     * 添加业务监听者：回调在主线程。
     * 若当前已有缓存快照（服务已推送过），添加后立即在主线程回调一次最新数据。
     */
    public void addListener(OnVehicleDataListener listener) {
        if (listener == null || mListeners.contains(listener)) {
            return;
        }
        mListeners.add(listener);
        try {
            registerRemoteListener();
        } catch (VehicleDataServiceUnavailableException e) {
            // 尚未连接：监听已加入 mListeners，服务连接后（onServiceConnected）会统一重新注册
            CLog.w(TAG, "addListener: not connected yet, defer register", e);
        }

        VehicleData snapshot;
        synchronized (mCacheLock) {
            snapshot = mCache != null ? mCache.snapshot() : null;
        }
        if (snapshot != null) {
            final OnVehicleDataListener l = listener;
            final VehicleData d = snapshot;
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    l.onVehicleDataChanged(d);
                }
            });
        }
    }

    /** 移除业务监听者；全部移除后会自动反注册 AIDL 监听，断开服务端播报链路 */
    public void removeListener(OnVehicleDataListener listener) {
        if (listener == null) {
            return;
        }
        mListeners.remove(listener);
        if (mListeners.isEmpty()) {
            unregisterRemoteListener();
        }
    }

    /** 最近一次服务端推送的数据快照（主线程回调外的只读场景使用）；尚无数据时返回 null */
    public VehicleData getCachedVehicleData() {
        synchronized (mCacheLock) {
            return mCache != null ? mCache.snapshot() : null;
        }
    }

    /** 主动向服务端拉取一次最新快照（同步 Binder 调用，禁止主线程频繁调用）；
     *  服务未连接时抛出 {@link VehicleDataServiceUnavailableException}，调用方需处理。 */
    public VehicleData getVehicleData() throws VehicleDataServiceUnavailableException {
        IVehicleDataService remote = requireRemote();
        try {
            VehicleData data = remote.getVehicleData();
            if (data != null) {
                synchronized (mCacheLock) {
                    mCache = data.snapshot();
                }
            }
            return data;
        } catch (RemoteException e) {
            CLog.e(TAG, "getVehicleData failed", e);
            return null;
        }
    }

    /** 通过远端服务读取自定义配置（同步 Binder 调用，禁止主线程频繁调用）；
     *  服务未连接时抛出 {@link VehicleDataServiceUnavailableException}，调用方需处理。 */
    public String getCustomConfig(String key) throws VehicleDataServiceUnavailableException {
        IVehicleDataService remote = requireRemote();
        try {
            return remote.getCustomConfig(key);
        } catch (RemoteException e) {
            CLog.e(TAG, "getCustomConfig failed", e);
            return null;
        }
    }

    /** 通过远端服务写入自定义配置（同步 Binder 调用，禁止主线程频繁调用）；
     *  服务未连接时抛出 {@link VehicleDataServiceUnavailableException}，调用方需处理。 */
    public void putCustomConfig(String key, String value) throws VehicleDataServiceUnavailableException {
        IVehicleDataService remote = requireRemote();
        try {
            remote.putCustomConfig(key, value);
        } catch (RemoteException e) {
            CLog.e(TAG, "putCustomConfig failed", e);
        }
    }

    // ---------------- 宿主内部模拟控制 ----------------

    /** 开启模拟会话（服务端记录真实快照为基线，后续模拟帧只叠加在覆盖层，不写入真实数据）。 */
    public void beginMock() throws VehicleDataServiceUnavailableException {
        IVehicleDataService remote = requireRemote();
        try {
            remote.beginMock();
        } catch (RemoteException e) {
            CLog.e(TAG, "beginMock failed", e);
        }
    }

    /** 注入一帧模拟数据（仅影响消费端观察到的快照）。 */
    public void reportMockData(VehicleData data) throws VehicleDataServiceUnavailableException {
        IVehicleDataService remote = requireRemote();
        try {
            remote.reportMockData(data);
        } catch (RemoteException e) {
            CLog.e(TAG, "reportMockData failed", e);
        }
    }

    /** 结束模拟会话（服务端丢弃覆盖层并立即重播被覆盖字段的真实值）。 */
    public void endMock() throws VehicleDataServiceUnavailableException {
        IVehicleDataService remote = requireRemote();
        try {
            remote.endMock();
        } catch (RemoteException e) {
            CLog.e(TAG, "endMock failed", e);
        }
    }

    /** 当前是否处于模拟会话中；服务未连接时返回 false。 */
    public boolean isMockActive() {
        IVehicleDataService remote = mRemote;
        if (remote == null) {
            return false;
        }
        try {
            return remote.isMockActive();
        } catch (RemoteException e) {
            CLog.e(TAG, "isMockActive failed", e);
            return false;
        }
    }

    /** 返回可用的远端接口；未连接时抛出受检异常，避免调用方拿到空引用后静默失败。 */
    private IVehicleDataService requireRemote() throws VehicleDataServiceUnavailableException {
        IVehicleDataService remote = mRemote;
        if (remote == null) {
            throw new VehicleDataServiceUnavailableException("vehicle data service not connected");
        }
        return remote;
    }

    // ---------------- AIDL 回调 ----------------

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            CLog.i(TAG, "service connected: " + name);
            mRemote = IVehicleDataService.Stub.asInterface(service);
            // 连接成功，重置退避并取消待执行的重连
            synchronized (VehicleDataManager.this) {
                mReconnectAttempts = 0;
                mMainHandler.removeCallbacks(mReconnectTask);
            }
            // 重连后服务端监听者已清空，若仍有业务监听者需重新注册
            if (!mListeners.isEmpty()) {
                try {
                    registerRemoteListener();
                } catch (VehicleDataServiceUnavailableException e) {
                    // mRemote 已就绪，理论不会走到这里；容错记录日志
                    CLog.e(TAG, "registerListener failed on connect", e);
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // 服务进程被杀/崩溃：清引用并安排自动重连，保证连接可用性（除非业务已主动 disconnect）
            CLog.w(TAG, "service disconnected: " + name);
            mRemote = null;
            synchronized (VehicleDataManager.this) {
                mBound = false;
                if (!mUserDisconnected) {
                    scheduleReconnectLocked();
                }
            }
        }
    };

    /** 自动重连任务：清理失效连接后重新绑定拉起服务 */
    private final Runnable mReconnectTask = new Runnable() {
        @Override
        public void run() {
            synchronized (VehicleDataManager.this) {
                if (mUserDisconnected || mRemote != null || mAppContext == null) {
                    mReconnectAttempts = 0;
                    return;
                }
                // 兜底解绑失效连接（服务已死时为空操作），再重新绑定
                try {
                    mAppContext.unbindService(mConnection);
                } catch (Exception ignored) {
                }
                mBound = false;
                bindOnceLocked();
            }
        }
    };

    /** 服务端回调 Stub：Binder 线程到达，切主线程分发 */
    private final IVehicleDataListener.Stub mAidlListener = new IVehicleDataListener.Stub() {
        @Override
        public void onVehicleDataChanged(final VehicleData data) {
            if (data == null) {
                return;
            }
            synchronized (mCacheLock) {
                mCache = data.snapshot();
            }
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    for (OnVehicleDataListener l : mListeners) {
                        try {
                            l.onVehicleDataChanged(data);
                        } catch (Throwable t) {
                            CLog.e(TAG, "onVehicleDataChanged dispatch error", t);
                        }
                    }
                }
            });
        }
    };

    private void registerRemoteListener() throws VehicleDataServiceUnavailableException {
        if (mListeners.isEmpty()) {
            return;
        }
        IVehicleDataService remote = requireRemote();
        try {
            remote.registerListener(mAidlListener);
        } catch (RemoteException e) {
            CLog.e(TAG, "registerListener failed", e);
        }
    }

    private void unregisterRemoteListener() {
        IVehicleDataService remote = mRemote;
        if (remote == null) {
            return;
        }
        try {
            remote.unregisterListener(mAidlListener);
        } catch (RemoteException e) {
            // 服务已死，连接断开时无需处理
            CLog.e(TAG, "unregisterListener failed", e);
        }
    }
}
