package com.automanager.demo.dashboard;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.automanager.dashboard.BaseDashboardService;
import com.automanager.dashboard.DashboardInfo;
import com.automanager.dashboard.utils.Util;
import com.automanager.vehicle.VehicleData;
import com.automanager.vehicle.VehicleDataManager;

import java.io.ByteArrayOutputStream;

/**
 * HelloWorld 仪表盘演示。
 *
 * <p>实现约定：manifest 中声明 {@link Util#ACTION_DASHBOARD_SERVICE} 的导出 Service，
 * 宿主 App（DbManager / 本地仪表页）扫描绑定后调用
 * {@code dbShow(vehicleModel)}，本服务在仪表屏 WindowManager 上输出 HelloWorld，
 * 并在下方显示经 {@link VehicleDataManager} 订阅到的实时转速，作为 {@link VehicleData} 的接入示例。</p>
 *
 * <p>窗口创建、黑色背景、显示/隐藏调度均由 {@link BaseDashboardService} 统一处理，
 * 子类只需在 {@link #onDashboardShow()} / {@link #onDashboardHide()} 中增删自己的视图。</p>
 */
public class DashboardDemoService extends BaseDashboardService {
    private static final String TAG = "DashboardDemoService";

    /** 宿主（Assistant）包名：跨应用绑定车辆数据服务时用于限定目标 */
    private static final String HOST_PACKAGE = "com.automanager";

    /** 仪表盘内 HelloWorld 字号（像素）：按 800x480 仪表屏标定，绕开 sp 随系统字体缩放 */
    private static final float HELLO_TEXT_SIZE_PX = 64f;

    /** 转速字号（像素） */
    private static final float RPM_TEXT_SIZE_PX = 32f;

    /** 本地仪表页排序权重：排在宿主内置仪表（2000+）与 TPMS（1990）之后 */
    private static final int DB_LOCAL_RANK = 1900;

    /** 仅主线程访问：dbShow/dbHide 已由基类切到主线程 */
    private LinearLayout rootView;
    private TextView rpmView;

    private VehicleDataManager vehicleDataManager;
    private boolean listening;

    /** VehicleDataManager 保证回调在主线程，可直接刷新视图 */
    private final VehicleDataManager.OnVehicleDataListener dataListener =
            new VehicleDataManager.OnVehicleDataListener() {
                @Override
                public void onVehicleDataChanged(VehicleData data) {
                    updateRpm(data);
                }
            };

    private byte[] cachedThumb;

    @Override
    protected void onDashboardShow() {
        // 幂等：同车型重复 dbShow 时先移除旧视图再重建
        removeRootView();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);

        TextView hello = new TextView(this);
        hello.setText(R.string.hello_world);
        hello.setTextColor(Color.WHITE);
        hello.setTypeface(Typeface.DEFAULT_BOLD);
        hello.setGravity(Gravity.CENTER);
        hello.setTextSize(TypedValue.COMPLEX_UNIT_PX, HELLO_TEXT_SIZE_PX);
        root.addView(hello);

        TextView rpm = new TextView(this);
        rpm.setText(R.string.rpm_unavailable);
        rpm.setTextColor(0xFFCCCCCC);
        rpm.setGravity(Gravity.CENTER);
        rpm.setTextSize(TypedValue.COMPLEX_UNIT_PX, RPM_TEXT_SIZE_PX);
        rpm.setPadding(0, 12, 0, 0);
        root.addView(rpm);

        rootView = root;
        rpmView = rpm;

        try {
            WindowManager.LayoutParams lp = Util.getRootViewLayoutParam(vehicleModel, mockWindowManager());
            getWindowManager().addView(root, lp);
            Log.i(TAG, "addView ok, model=" + vehicleModel + " w=" + lp.width + " h=" + lp.height);
        } catch (Throwable e) {
            // addView 失败必须复位，否则残留视图会阻塞后续 dbShow
            Log.e(TAG, "addView failed", e);
            rootView = null;
            rpmView = null;
            return;
        }

        // 窗口就绪后再订阅车辆数据，注册后服务端会推送一次全量快照
        bindVehicleData();
    }

    @Override
    protected void onDashboardHide() {
        removeRootView();
        unbindVehicleData();
    }

    private void removeRootView() {
        if (rootView == null) {
            return;
        }
        try {
            getWindowManager().removeView(rootView);
        } catch (Exception ignored) {
        }
        rootView = null;
        rpmView = null;
    }

    /** 订阅宿主车辆数据：跨应用消费必须显式指定宿主包名 */
    private void bindVehicleData() {
        if (vehicleDataManager == null) {
            vehicleDataManager = VehicleDataManager.getInstance();
        }
        vehicleDataManager.connect(this, HOST_PACKAGE, null);
        vehicleDataManager.addListener(dataListener);
        listening = true;
        // 已有缓存快照时先回放，避免首帧显示占位符
        updateRpm(vehicleDataManager.getCachedVehicleData());
    }

    private void unbindVehicleData() {
        if (!listening || vehicleDataManager == null) {
            return;
        }
        vehicleDataManager.removeListener(dataListener);
        vehicleDataManager.disconnect();
        listening = false;
    }

    /** 用最新快照刷新转速；无有效转速时显示占位符 */
    private void updateRpm(VehicleData data) {
        if (rpmView == null) {
            return;
        }
        if (data != null && data.hasField(VehicleData.FIELD_ENGINE_RPM)) {
            rpmView.setText(getString(R.string.rpm_format, data.engineRpm));
        } else {
            rpmView.setText(R.string.rpm_unavailable);
        }
    }

    @Override
    protected void onDashboardOpenConfig() {
        // 演示仪表没有配置项（DashboardInfo.withCustomConfig = false）
    }

    @Override
    protected DashboardInfo onDashboardInfo() {
        DashboardInfo info = new DashboardInfo();
        info.title = getString(R.string.dashboard_title);
        if (cachedThumb == null) {
            cachedThumb = buildThumb();
        }
        info.thumb = cachedThumb;
        info.withCustomConfig = false;
        info.rank = DB_LOCAL_RANK;
        return info;
    }

    /** 生成仪表预览缩略图：黑底白字，JPEG 编码与宿主其他仪表保持一致 */
    private byte[] buildThumb() {
        final int width = 480;
        final int height = 240;
        Bitmap bmp = null;
        try {
            bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            canvas.drawColor(Color.BLACK);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.WHITE);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(HELLO_TEXT_SIZE_PX);
            paint.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics metrics = paint.getFontMetrics();
            float baseline = height / 2f - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(getString(R.string.hello_world), width / 2f, baseline, paint);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            return bmp.compress(Bitmap.CompressFormat.JPEG, 80, out) ? out.toByteArray() : null;
        } catch (Exception e) {
            Log.e(TAG, "buildThumb failed", e);
            return null;
        } finally {
            if (bmp != null) {
                bmp.recycle();
            }
        }
    }
}
