// 车辆数据服务（消费端接口，开源 SDK）：订阅车辆数据变化或主动拉取当前快照。
// 数据由宿主侧采集端上报（上报通道为宿主内部接口，不在开源 SDK 中暴露），
// 服务端存储最新全量快照并在字段变化时向所有消费端播报。
// 客户端封装见 java/com/vc2manager/vehicle/VehicleDataManager.java。
package com.automanager.vehicle;

import com.automanager.vehicle.VehicleData;
import com.automanager.vehicle.IVehicleDataListener;

interface IVehicleDataService {
    /** 获取当前车辆数据快照（validMask 标识有效字段）；尚无数据上报时字段为空快照。 */
    VehicleData getVehicleData();

    /**
     * 注册数据监听：注册成功后服务端立即回调一次当前全量快照，
     * 之后字段变化时持续播报；重复注册同一 listener 无效。
     */
    void registerListener(IVehicleDataListener listener);

    /** 注销数据监听；客户端销毁/解绑前必须调用，避免服务端持有死引用。 */
    void unregisterListener(IVehicleDataListener listener);

    String getCustomConfig(String key);

    void putCustomConfig(String key, String value);

    // ---------------- 宿主内部模拟控制（仪表盘「模拟数据」功能） ----------------

    /**
     * 开启模拟会话：服务端清空模拟覆盖层，此后的 reportMockData 仅叠加到模拟覆盖层，
     * 不会写入真实数据快照（真实采集数据由 reporter 通道持续维护，不受影响）。
     */
    void beginMock();

    /**
     * 注入一帧模拟数据：仅影响消费端观察到的快照（真实快照 ⊕ 模拟覆盖），
     * 不写入真实快照；结束会话后这些覆盖字段会自动恢复为真实值。
     */
    void reportMockData(in VehicleData data);

    /**
     * 结束模拟会话：丢弃模拟覆盖层，立即向所有消费端重播被覆盖字段对应的真实值，
     * 避免续航/油耗等慢更新字段停留在模拟值。
     */
    void endMock();

    /** 当前是否处于模拟会话中 */
    boolean isMockActive();
}
