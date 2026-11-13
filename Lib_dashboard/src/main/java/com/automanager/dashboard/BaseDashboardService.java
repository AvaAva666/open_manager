package com.automanager.dashboard;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import com.automanager.dashboard.utils.Util;

/**
 * 自定义仪表 Service 抽象基类：收敛 dbShow/dbHide 的公共流程。
 *
 * <p>基类统一在主线程完成：创建仪表屏 WindowManager、添加/移除纯黑背景悬浮窗，
 * 再回调子类的 {@link #onDashboardShow()} / {@link #onDashboardHide()} 纯渲染逻辑。
 * 子类无需关心窗口创建与黑色背景生命周期，避免各仪表遗漏统一逻辑。</p>
 */
public abstract class BaseDashboardService extends Service {
    private static final String TAG = BaseDashboardService.class.getSimpleName();

    /** 主线程 handler：AIDL 方法运行在 Binder 线程，addView/removeView 必须切主线程 */
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** 仅保护请求入队与销毁，不在锁内调用 WindowManager 或子类。 */
    private final Object lifecycleLock = new Object();
    private boolean destroyed;

    /** 以下窗口状态只在主线程访问。 */
    private boolean dashboardShown;

    /** 仪表盘屏幕 WindowManager（dbShow 创建，供子类经 {@link #getWindowManager()} 使用） */
    protected WindowManager sWindowManager;

    /** 纯黑背景悬浮窗，基类在 dbShow/dbHide 自动管理 */
    private View blackBackgroundView;

    /** 车型代码（VEHICLE_MODEL_*），dbShow 传入 */
    protected String vehicleModel;

    /** 子类可覆盖：是否使用 mock WindowManager（默认 false，取第二块仪表屏） */
    protected boolean mockWindowManager() {
        return false;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new IDashboardService.Stub() {
            @Override
            public void dbShow(String vehicleModel) {
                postDashboardAction(new Runnable() {
                    @Override
                    public void run() {
                        showDashboard(vehicleModel);
                    }
                });
            }

            @Override
            public void dbHide() {
                postDashboardAction(new Runnable() {
                    @Override
                    public void run() {
                        hideDashboard();
                    }
                });
            }

            @Override
            public void dbOpenConfig() {
                onDashboardOpenConfig();
            }

            @Override
            public DashboardInfo dbInfo() {
                return onDashboardInfo();
            }
        };
    }

    private void postDashboardAction(Runnable action) {
        synchronized (lifecycleLock) {
            if (!destroyed) {
                mainHandler.post(action);
            }
        }
    }

    private void showDashboard(String requestedVehicleModel) {
        boolean sameModel = vehicleModel == null ? requestedVehicleModel == null
                : vehicleModel.equals(requestedVehicleModel);
        if (sWindowManager != null && !sameModel) {
            // 子类必须仍能使用旧车型和旧 WindowManager 清理自己的视图。
            hideDashboard();
            sWindowManager = null;
        }
        if (sWindowManager == null) {
            try {
                sWindowManager = Util.createWindowManager(getBaseContext(), mockWindowManager());
            } catch (Throwable e) {
                Log.e(TAG, "createWindowManager failed", e);
                return;
            }
        }
        vehicleModel = requestedVehicleModel;
        if (blackBackgroundView == null) {
            blackBackgroundView = Util.showBlackBackground(this,
                    sWindowManager, vehicleModel, mockWindowManager());
        }
        // 同车型重复显示仍须回调，子类依赖它重读配置；只复用公共窗口。
        dashboardShown = true;
        onDashboardShow();
    }

    private void hideDashboard() {
        try {
            if (dashboardShown) {
                dashboardShown = false;
                onDashboardHide();
            }
        } finally {
            Util.hideBlackBackground(sWindowManager, blackBackgroundView);
            blackBackgroundView = null;
        }
    }

    @Override
    public void onDestroy() {
        synchronized (lifecycleLock) {
            destroyed = true;
            // 与 Binder 入队共用锁，防止清队列后又追加旧服务的 show/hide。
            mainHandler.removeCallbacksAndMessages(null);
        }
        try {
            hideDashboard();
        } finally {
            sWindowManager = null;
            vehicleModel = null;
            super.onDestroy();
        }
    }

    /** 获取仪表屏 WindowManager（仅在 dbShow 之后可用） */
    protected WindowManager getWindowManager() throws RuntimeException {
        if (sWindowManager == null) {
            throw new RuntimeException("WindowManager is not created yet");
        }
        return sWindowManager;
    }

    /** dbShow 时创建并显示仪表视图（主线程回调） */
    protected abstract void onDashboardShow();

    /** dbHide 时移除仪表视图（主线程回调） */
    protected abstract void onDashboardHide();

    /** 打开仪表配置页 */
    protected abstract void onDashboardOpenConfig();

    /** 返回仪表信息（标题/缩略图/排序权重） */
    protected abstract DashboardInfo onDashboardInfo();
}
