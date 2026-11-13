package com.automanager.dashboard.utils;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static com.automanager.Const.VEHICLE_MODEL_TBAA;
import static com.automanager.Const.VEHICLE_MODEL_TEAA;
import static com.automanager.Const.VEHICLE_MODEL_THAA;
import static com.automanager.Const.VEHICLE_MODEL_TRNA;
import static com.automanager.Const.VEHICLE_MODEL_TLAA;
import static com.automanager.Const.VEHICLE_MODEL_TRTA;
import static com.automanager.Const.VEHICLE_MODEL_TRVA;
import static com.automanager.Const.VEHICLE_MODEL_TRWA;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.display.DisplayManager;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import java.io.ByteArrayOutputStream;

public class Util {

    /** 仪表盘服务发现 action：实现 IDashboardService 的导出 Service 需在 manifest 的 intent-filter 中声明此 action */
    public static final String ACTION_DASHBOARD_SERVICE = "com.automanager.dashboard.action.DASHBOARD_SERVICE";

    /** bind 仪表服务时通过 intent extra 传递的车型代码（VEHICLE_MODEL_*），服务端在 onBind 中读取用于确定窗口边界 */
    public static final String EXTRA_VEHICLE_MODEL = "vehicleModel";

    /** 缩略图编码：JPEG 比 PNG 压缩速度快数倍、体积更小，预览场景足够 */
    private static final Bitmap.CompressFormat THUMB_FORMAT = Bitmap.CompressFormat.JPEG;
    private static final int THUMB_QUALITY = 80;
    /** 缩略图最长边：按预览卡片尺寸预缩放，减小 binder 传输量与客户端解码开销 */
    private static final int THUMB_MAX_LEN = 480;

    /**
     * 读取资源图片并生成可跨进程传输的缩略图字节：
     * 解码时按最大边降到 THUMB_MAX_LEN（内存与耗时大幅下降），编码为 JPEG（比 PNG 快很多）。
     */
    public static byte[] readThumbBytes(android.content.res.Resources res, int resId) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeResource(res, resId, opts);
            if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                return null;
            }
            opts.inSampleSize = Math.max(1, Math.max(opts.outWidth, opts.outHeight) / THUMB_MAX_LEN);
            opts.inJustDecodeBounds = false;
            Bitmap bmp = BitmapFactory.decodeResource(res, resId, opts);
            if (bmp == null) {
                return null;
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            return bmp.compress(THUMB_FORMAT, THUMB_QUALITY, baos) ? baos.toByteArray() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static final int Size_CrvWidth = 583;
    public static final int Size_CrvHeight = 206;

    public static final int Size_GdWidth = 762;
    public static final int Size_GdHeight = 330;

    public static final int Size_CivicWidth = 553;
    public static final int Size_CivicHeight = 216;

    /**
     * 获取仪表盘的边界，仪表盘是异型仪表盘，不同车型宽高不一样
     * 屏幕是800x480 可见区域在 【上margin ~ (上margin+仪表盘高度) 之间】【左margin ~ (左margin+仪表盘宽度) 之间】
     * @param vehicleModel 仪表盘的类型
     * @return 仪表盘的边界
     * [2] 上margin
     * [3] 右margin
     * [4] 下margin
     * [5] 左margin
     */
    public static int[] getLayoutBound(final String vehicleModel) {
        int[] arr = new int[6];
        switch (vehicleModel){
            case VEHICLE_MODEL_TBAA:
            case VEHICLE_MODEL_TEAA: {
                arr[0] = Size_CivicWidth;
                arr[1] = Size_CivicHeight;
                arr[2] = 0; // Top
                arr[3] = 0; // Right
                arr[4] = 0; // Bottom
                arr[5] = 10;// Left
                break;
            }
            case VEHICLE_MODEL_THAA:
            case VEHICLE_MODEL_TRNA: {
                arr[0] = Size_GdWidth;
                arr[1] = Size_GdHeight;
                arr[2] = 0;
                arr[3] = 0;
                arr[4] = 0;
                arr[5] = 0;
                break;
            }
            case VEHICLE_MODEL_TLAA:
            case VEHICLE_MODEL_TRTA:
            case VEHICLE_MODEL_TRVA:
            case VEHICLE_MODEL_TRWA: {
                arr[0] = Size_CrvWidth;
                arr[1] = Size_CrvHeight;
                arr[2] = 0;
                arr[3] = 0;
                arr[4] = 0;
                arr[5] = 59;
                break;
            }
        }
        return arr;
    }

    public static int[] getContentBound(final String vehicleModel) {
        int[] arr = new int[6];
        switch (vehicleModel){
            case VEHICLE_MODEL_TBAA:
            case VEHICLE_MODEL_TEAA: {
                arr[0] = Size_CivicWidth;
                arr[1] = Size_CivicHeight;
                arr[2] = 20; // Top
                arr[3] = 20; // Right
                arr[4] = 0; // Bottom
                arr[5] = 20;// Left
                break;
            }
            case VEHICLE_MODEL_THAA:
            case VEHICLE_MODEL_TRNA: {
                arr[0] = Size_GdWidth;
                arr[1] = Size_GdHeight;
                arr[2] = 5;
                arr[3] = 5;
                arr[4] = 5;
                arr[5] = 5;
                break;
            }
            case VEHICLE_MODEL_TLAA:
            case VEHICLE_MODEL_TRTA:
            case VEHICLE_MODEL_TRVA:
            case VEHICLE_MODEL_TRWA: {
                arr[0] = Size_CrvWidth;
                arr[1] = Size_CrvHeight;
                arr[2] = 18; // Top
                arr[3] = 10; // Right
                arr[4] = 0; // Bottom
                arr[5] = 10; // Left
                break;
            }
        }
        return arr;
    }

    public static final int getBoundTopMargin(int[] bounds){
        return bounds[2];
    }
    public static final int getBoundBottomMargin(int[] bounds){
        return bounds[4];
    }
    public static final int getBoundLeftMargin(int[] bounds){
        return bounds[5];
    }
    public static final int getBoundRightMargin(int[] bounds){
        return bounds[3];
    }

    /**
     * 获取仪表盘的缩放比例，冠道等车型屏幕较大 可以相对CRV车型 元素放大一些
     * @param vehicleModel 车型代码（VEHICLE_MODEL_*）
     * @return 仪表盘的缩放比例
     */
    public static float getScale(final String vehicleModel) {
        switch (vehicleModel) {
            case VEHICLE_MODEL_THAA:
            case VEHICLE_MODEL_TRNA: {
                return 1.65f;
            }
            default: {
                // 思域（TBAA/TEAA）、CR-V 家族（TLAA/TRTA/TRVA/TRWA）等其余车型
                return 1.0f;
            }
        }
    }


    public static WindowManager.LayoutParams getRootViewLayoutParam(final String vehicleModel, boolean mockWindowManager) {
        int[] bound = getLayoutBound(vehicleModel);
        WindowManager.LayoutParams params;
        if (mockWindowManager) {
            params = new WindowManager.LayoutParams();
            params.x = 0;
            params.y = 0;
            params.width = bound[0];
            params.height = bound[1];
            params.type = 2006;
            params.format = -3;
            params.flags = 256;
            params.flags = params.flags | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
            params.flags = params.flags | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;                
        } else {
            params = new WindowManager.LayoutParams();            
            params.x = bound[5];
            params.y = bound[2];
            params.width = bound[0];
            params.height = bound[1];
            params.type = 2006;
            params.format = -3;
            params.flags = 256;
            params.gravity = Gravity.LEFT | Gravity.TOP;
            params.flags = params.flags | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
            params.flags = params.flags | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        return params;
    }

    public static WindowManager createWindowManager(Context sContext, boolean mockWindowManager) throws RuntimeException {
        WindowManager sWindowManager = null;
        if (mockWindowManager) {
            Display[] displays = ((DisplayManager) sContext.getSystemService(Context.DISPLAY_SERVICE)).getDisplays();
            Context createDisplayContext = sContext.createDisplayContext(displays[0]);
            if (createDisplayContext != null) {
                sWindowManager = (WindowManager) createDisplayContext.getSystemService(Context.WINDOW_SERVICE);
            }
        } else {
            Display[] displays = ((DisplayManager) sContext.getSystemService(Context.DISPLAY_SERVICE)).getDisplays();
            if (displays.length < 2) {
                throw new RuntimeException("Displays less 2");
            }
            Display sDisplay = displays[displays.length - 1];
            Log.d("CLog","DisplayLength:" + displays.length);
            Context createDisplayContext = sContext.createDisplayContext(sDisplay);
            if (createDisplayContext != null) {
                sWindowManager = (WindowManager) createDisplayContext.getSystemService(Context.WINDOW_SERVICE);
            }
        }
        if (sWindowManager == null) {
            throw new RuntimeException("createWindowManager failed");
        }
        return sWindowManager;
    }

    /**
     * 添加纯黑背景悬浮窗（作为仪表盘下方黑色底），布局参数与仪表盘 root 一致；
     * 必须先于仪表盘视图 addView，才能保证背景位于底层。
     * @return 背景 View，供 {@link #hideBlackBackground} 移除；添加失败返回 null
     */
    public static View showBlackBackground(Context context, WindowManager wm,
                                           String vehicleModel, boolean mockWindowManager) {
        if (wm == null) {
            return null;
        }
        try {
            View v = new View(context);
            v.setBackgroundColor(0xFF000000);
            WindowManager.LayoutParams lp = getRootViewLayoutParam(vehicleModel, mockWindowManager);
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.MATCH_PARENT;
            wm.addView(v, lp);
            return v;
        } catch (Throwable e) {
            Log.e("Util", "showBlackBackground failed", e);
            return null;
        }
    }

    /** 移除纯黑背景悬浮窗 */
    public static void hideBlackBackground(WindowManager wm, View blackBackgroundView) {
        if (wm == null || blackBackgroundView == null) {
            return;
        }
        try {
            wm.removeView(blackBackgroundView);
        } catch (Exception ignored) {
        }
    }
}
