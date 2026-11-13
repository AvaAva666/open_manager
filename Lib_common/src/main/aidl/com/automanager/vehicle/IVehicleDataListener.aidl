// 车辆数据变化回调：由客户端实现并注册到 IVehicleDataService。
// oneway：服务端播报不阻塞、不因客户端耗时/卡死拖慢采集线程；
// 客户端死亡由服务端 RemoteCallbackList 自动清理。
package com.automanager.vehicle;

import com.automanager.vehicle.VehicleData;

interface IVehicleDataListener {
    /**
     * 车辆数据变化播报。
     * - registerListener() 后会立即回调一次全量快照（changedMask 等于 validMask）；
     * - 之后仅在字段变化时回调，data.changedMask 标记本次变化的字段位
     *   （VehicleData.FIELD_* 的按位或），data.validMask 标记当前所有有效字段；
     * - 回调运行在 Binder 线程，客户端需自行切主线程更新 UI。
     */
    oneway void onVehicleDataChanged(in VehicleData data);
}
