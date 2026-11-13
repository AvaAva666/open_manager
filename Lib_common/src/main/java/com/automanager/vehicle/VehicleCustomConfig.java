package com.automanager.vehicle;

/**
 * 跨模块桥接：VehicleDataService（Lib_inner_common，闭源内部库）通过本类访问宿主侧
 * VcConfig 的 dbCustomConfigs（位于 Lib_config / app 模块），避免内部库反向依赖宿主业务模块。
 * 由宿主 Application 在启动时调用 {@link #setImplementation(Implementation)} 注册实现。
 */
public class VehicleCustomConfig {

    public interface Implementation {
        String getCustomConfig(String key);

        void putCustomConfig(String key, String value);
    }

    private static volatile Implementation sImpl;

    public static void setImplementation(Implementation impl) {
        sImpl = impl;
    }

    public static String getCustomConfig(String key) {
        return sImpl != null ? sImpl.getCustomConfig(key) : null;
    }

    public static void putCustomConfig(String key, String value) {
        if (sImpl != null) {
            sImpl.putCustomConfig(key, value);
        }
    }
}