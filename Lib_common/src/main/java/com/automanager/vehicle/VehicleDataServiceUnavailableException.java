package com.automanager.vehicle;

/**
 * 车辆数据服务未连接时抛出的受检异常：调用方在 {@code mRemote} 尚未就绪的情况下
 * 访问远端接口（如读取/写入自定义配置）时抛出，调用方需显式处理。
 */
public class VehicleDataServiceUnavailableException extends Exception {

    public VehicleDataServiceUnavailableException(String message) {
        super(message);
    }
}