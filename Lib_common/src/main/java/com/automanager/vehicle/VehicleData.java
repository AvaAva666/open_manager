package com.automanager.vehicle;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * 车辆数据快照（AIDL 跨进程传递）。
 *
 * 宿主侧采集端逐字段写入（零散上报，每帧只携带变化字段，setter 内置 valid 置位），
 * 服务端存储最新一份全量快照并在变化时播报给消费端。
 *
 * 两个掩码：
 * - {@link #validMask}：当前哪些字段已采集到有效值（FIELD_* 按位或）；
 *   消费端读取具体字段前应先用 {@link #hasField(int)} 判断，未置位字段不可用。
 * - {@link #changedMask}：本次播报中哪些字段发生了变化（FIELD_* 按位或）；
 *   注册后首次全量回调时 changedMask == validMask。
 *
 * 新增字段步骤：
 * 1) 增加 FIELD_* 位常量（顺延 bit，勿复用旧位）；
 * 2) 增加字段 + setter（setter 内置 valid 置位）；
 * 3) 在 Parcel 末尾（version 之后）追加读写，并同步 {@link #copyFrom(VehicleData)}。
 *
 * 向后兼容（Parcel 版本）：version 字段（{@link #PARCEL_VERSION}）追加在全部旧字段之后，
 * 新版本读取侧用 {@code dataAvail()} 守卫。老版本消费端只读旧字段即止、忽略尾部新字节；
 * 新版本消费端读旧字段后若无剩余字节则按 version 1 处理、新增字段保持初值，双方均不崩溃。
 */
public class VehicleData implements Parcelable {

    /** Parcel 数据版本号：追加在全部旧字段之后，读取侧用 dataAvail() 守卫以实现向后兼容。 */
    private static final int PARCEL_VERSION = 4;

    // ---------------- 字段位定义（validMask / changedMask 使用，勿复用旧位） ----------------

    /** 发动机转速（int，rpm） */
    public static final int FIELD_ENGINE_RPM           = 1 << 0;
    /** 冷却液温度（float，℃） */
    public static final int FIELD_COOLANT_TEMP         = 1 << 1;
    /** 车速（float，km/h） */
    public static final int FIELD_VEHICLE_SPEED        = 1 << 2;
    /** 方向盘转向角（float，度；向右/顺时针为正，向左为负，正中为 0） */
    public static final int FIELD_STEERING_ANGLE       = 1 << 3;
    /** 档位（int，GEAR_* 常量） */
    public static final int FIELD_GEAR                 = 1 << 4;
    /** 车架号 VIN（String） */
    public static final int FIELD_VIN                  = 1 << 5;
    /** 左转向灯开关（boolean，true=点亮/闪烁中） */
    public static final int FIELD_TURN_SIGNAL_LEFT     = 1 << 6;
    /** 右转向灯开关（boolean，true=点亮/闪烁中） */
    public static final int FIELD_TURN_SIGNAL_RIGHT    = 1 << 7;
    /** 机油寿命（float，%，0~100） */
    public static final int FIELD_OIL_LIFE_PERCENT     = 1 << 8;
    /** 保养剩余里程（int，km） */
    public static final int FIELD_MAINTENANCE_MILEAGE  = 1 << 9;
    /** 保养剩余天数（int，天） */
    public static final int FIELD_MAINTENANCE_DAYS     = 1 << 10;
    /** 油门开度（float，%，0~100） */
    public static final int FIELD_THROTTLE_OPENING     = 1 << 11;
    /** 油量（float，%，0~100） */
    public static final int FIELD_FUEL_LEVEL_PERCENT   = 1 << 12;
    /** 续航里程（int，km） */
    public static final int FIELD_RANGE_KM             = 1 << 13;
    /** A 里程（Trip A）平均油耗（float，L/100km） */
    public static final int FIELD_TRIP_A_AVG_FUEL      = 1 << 14;
    /** 瞬时油耗（float，L/100km） */
    public static final int FIELD_INSTANT_FUEL         = 1 << 15;
    /** 历史平均油耗（float，L/100km） */
    public static final int FIELD_HIST_AVG_FUEL        = 1 << 16;
    /** 蓄电瓶电压（float，V） */
    public static final int FIELD_BATTERY_VOLTAGE      = 1 << 17;
    /** 总里程/里程表读数（long，km） */
    public static final int FIELD_ODOMETER_KM          = 1 << 18;
    /** A 里程（Trip A）累计里程（float，km，0.1km 分辨率） */
    public static final int FIELD_TRIP_A_DISTANCE_KM   = 1 << 19;
    /** 车内温度（float，℃） */
    public static final int FIELD_IN_CAR_TEMP          = 1 << 20;
    /** 大灯状态（boolean，true=小灯/近光/远光任一开启） */
    public static final int FIELD_HEAD_LIGHT_ON        = 1 << 21;
    /** 双闪/危险警示灯（boolean，true=开启） */
    public static final int FIELD_HAZARD_ON            = 1 << 22;
    /** 车窗状态（boolean，true=任一车窗未关闭/开启） */
    public static final int FIELD_WINDOW_OPEN          = 1 << 23;
    /** 纵向加速度（float，g；来自 SRS 六轴 G 传感器） */
    public static final int FIELD_LONGITUDINAL_ACCEL   = 1 << 24;
    /** 横向加速度（float，g；来自 SRS 六轴 G 传感器） */
    public static final int FIELD_LATERAL_ACCEL        = 1 << 25;
    /** 车外温度（float，℃） */
    public static final int FIELD_OUTSIDE_TEMP         = 1 << 26;
    /** TPMS 胎压原始数据包（int；打包规则见 {@link #tpmsData}） */
    public static final int FIELD_TPMS_DATA            = 1 << 27;
    /** TPMS 胎温原始数据包（int；打包规则见 {@link #tpmsTempData}） */
    public static final int FIELD_TPMS_TEMP_DATA       = 1 << 28;

    // ---------------- 档位常量（gear 字段取值） ----------------

    /** 无效/未采集 */
    public static final int GEAR_UNKNOWN = -1;
    public static final int GEAR_P = 0;
    public static final int GEAR_R = 1;
    public static final int GEAR_N = 2;
    public static final int GEAR_D = 3;
    /** S 挡 / 运动 / 手动模式 */
    public static final int GEAR_S = 4;
    /** L 挡 / 低速挡 */
    public static final int GEAR_L = 5;

    // ---------------- 数据字段 ----------------

    /** 有效字段位掩码 */
    public int validMask;
    /** 本次播报变化字段位掩码（服务端在播报时填充） */
    public int changedMask;

    /** 发动机转速（int，rpm） */
    public int engineRpm;
    /** 冷却液温度（float，℃） */
    public float coolantTemp;
    /** 车速（float，km/h） */
    public float vehicleSpeed;
    /** 方向盘转向角（float，度） */
    public float steeringAngle;
    /** 档位（int，GEAR_* 常量） */
    public int gear = GEAR_UNKNOWN;
    /** 车架号 VIN（String） */
    public String vin;
    /** 左转向灯开关（boolean） */
    public boolean turnSignalLeft;
    /** 右转向灯开关（boolean） */
    public boolean turnSignalRight;
    /** 机油寿命（float，%，0~100） */
    public float oilLifePercent;
    /** 保养剩余里程（int，km） */
    public int maintenanceMileageKm;
    /** 保养剩余天数（int，天） */
    public int maintenanceDays;
    /** 油门开度（float，%，0~100） */
    public float throttleOpeningPercent;
    /** 油量（float，%，0~100） */
    public float fuelLevelPercent;
    /** 续航里程（int，km） */
    public int rangeKm;
    /** A 里程（Trip A）平均油耗（float，L/100km） */
    public float tripAAvgFuel;
    /** 瞬时油耗（float，L/100km） */
    public float instantFuel;
    /** 历史平均油耗（float，L/100km） */
    public float histAvgFuel;
    /** 蓄电瓶电压（float，V） */
    public float batteryVoltage;
    /** 总里程/里程表读数（long，km） */
    public long odometerKm;
    /** A 里程（Trip A）累计里程（float，km，0.1km 分辨率） */
    public float tripADistanceKm;
    /** 车内温度（float，℃） */
    public float inCarTemp;
    /** 大灯状态（boolean） */
    public boolean headLightOn;
    /** 双闪/危险警示灯（boolean） */
    public boolean hazardOn;
    /** 车窗状态（boolean） */
    public boolean windowOpen;
    /** 纵向加速度（float，g） */
    public float longitudinalAccel;
    /** 横向加速度（float，g） */
    public float lateralAccel;
    /** 车外温度（float，℃） */
    public float outsideTemp;
    /**
     * TPMS 胎压原始数据包（int）：
     * 取自 B-CAN 385438547（0x16F95353）帧数据 b[2..5]，按 b[2]<<24 | b[3]<<16 | b[4]<<8 | b[5] 打包，
     * 每字节为对应轮胎压原始值（*5/100 = bar，与 vc1 VC_RECV_BCAN_DATA 口径一致），
     * 胎压仪表消费端解析后映射到四轮显示。
     */
    public int tpmsData;
    /**
     * TPMS 胎温原始数据包（int）：
     * 取自 B-CAN 385438547（0x16F95353）帧数据 b[2..5]（0xBB 0x55 帧头温度帧），
     * 按 b[2]<<24 | b[3]<<16 | b[4]<<8 | b[5] 打包，每字节减 40 为 ℃（与 vc1 VC_RECV_BCAN_DATA
     * 温度口径一致），胎压仪表消费端解析后映射到四轮显示，0xFF 表示该轮无有效温度。
     */
    public int tpmsTempData;

    public VehicleData() {
    }

    protected VehicleData(Parcel in) {
        validMask = in.readInt();
        changedMask = in.readInt();
        engineRpm = in.readInt();
        coolantTemp = in.readFloat();
        vehicleSpeed = in.readFloat();
        steeringAngle = in.readFloat();
        gear = in.readInt();
        vin = in.readString();
        turnSignalLeft = in.readByte() != 0;
        turnSignalRight = in.readByte() != 0;
        oilLifePercent = in.readFloat();
        maintenanceMileageKm = in.readInt();
        maintenanceDays = in.readInt();
        throttleOpeningPercent = in.readFloat();
        fuelLevelPercent = in.readFloat();
        rangeKm = in.readInt();
        tripAAvgFuel = in.readFloat();
        instantFuel = in.readFloat();
        histAvgFuel = in.readFloat();
        batteryVoltage = in.readFloat();
        odometerKm = in.readLong();
        tripADistanceKm = in.readFloat();
        // 向后兼容：老版本 Parcel 到 tripADistanceKm 即结束（无 version 与新增字段）。
        // dataAvail()==0 时保持新增字段默认值（0 / false / 0.0f）。
        if (in.dataAvail() >= 4) {
            int parcelVersion = in.readInt();
            if (parcelVersion >= 2) {
                inCarTemp = in.readFloat();
                headLightOn = in.readByte() != 0;
                hazardOn = in.readByte() != 0;
                windowOpen = in.readByte() != 0;
                longitudinalAccel = in.readFloat();
                lateralAccel = in.readFloat();
                outsideTemp = in.readFloat();
            }
            if (parcelVersion >= 3) {
                tpmsData = in.readInt();
            }
            if (parcelVersion >= 4) {
                tpmsTempData = in.readInt();
            }
        }
    }

    /** 字段是否已采集到有效值 */
    public boolean hasField(int fieldBit) {
        return (validMask & fieldBit) != 0;
    }

    // ---------------- 采集端 setter（置值同时标记 valid） ----------------

    public void setEngineRpm(int value) {
        engineRpm = value;
        validMask |= FIELD_ENGINE_RPM;
    }

    public void setCoolantTemp(float value) {
        coolantTemp = value;
        validMask |= FIELD_COOLANT_TEMP;
    }

    public void setVehicleSpeed(float value) {
        vehicleSpeed = value;
        validMask |= FIELD_VEHICLE_SPEED;
    }

    public void setSteeringAngle(float value) {
        steeringAngle = value;
        validMask |= FIELD_STEERING_ANGLE;
    }

    public void setGear(int value) {
        gear = value;
        validMask |= FIELD_GEAR;
    }

    public void setVin(String value) {
        vin = value;
        validMask |= FIELD_VIN;
    }

    public void setTurnSignalLeft(boolean value) {
        turnSignalLeft = value;
        validMask |= FIELD_TURN_SIGNAL_LEFT;
    }

    public void setTurnSignalRight(boolean value) {
        turnSignalRight = value;
        validMask |= FIELD_TURN_SIGNAL_RIGHT;
    }

    public void setOilLifePercent(float value) {
        oilLifePercent = value;
        validMask |= FIELD_OIL_LIFE_PERCENT;
    }

    public void setMaintenanceMileageKm(int value) {
        maintenanceMileageKm = value;
        validMask |= FIELD_MAINTENANCE_MILEAGE;
    }

    public void setMaintenanceDays(int value) {
        maintenanceDays = value;
        validMask |= FIELD_MAINTENANCE_DAYS;
    }

    public void setThrottleOpeningPercent(float value) {
        throttleOpeningPercent = value;
        validMask |= FIELD_THROTTLE_OPENING;
    }

    public void setFuelLevelPercent(float value) {
        fuelLevelPercent = value;
        validMask |= FIELD_FUEL_LEVEL_PERCENT;
    }

    public void setRangeKm(int value) {
        rangeKm = value;
        validMask |= FIELD_RANGE_KM;
    }

    public void setTripAAvgFuel(float value) {
        tripAAvgFuel = value;
        validMask |= FIELD_TRIP_A_AVG_FUEL;
    }

    public void setInstantFuel(float value) {
        instantFuel = value;
        validMask |= FIELD_INSTANT_FUEL;
    }

    public void setHistAvgFuel(float value) {
        histAvgFuel = value;
        validMask |= FIELD_HIST_AVG_FUEL;
    }

    public void setBatteryVoltage(float value) {
        batteryVoltage = value;
        validMask |= FIELD_BATTERY_VOLTAGE;
    }

    public void setOdometerKm(long value) {
        odometerKm = value;
        validMask |= FIELD_ODOMETER_KM;
    }

    public void setTripADistanceKm(float value) {
        tripADistanceKm = value;
        validMask |= FIELD_TRIP_A_DISTANCE_KM;
    }

    public void setInCarTemp(float value) {
        inCarTemp = value;
        validMask |= FIELD_IN_CAR_TEMP;
    }

    public void setHeadLightOn(boolean value) {
        headLightOn = value;
        validMask |= FIELD_HEAD_LIGHT_ON;
    }

    public void setHazardOn(boolean value) {
        hazardOn = value;
        validMask |= FIELD_HAZARD_ON;
    }

    public void setWindowOpen(boolean value) {
        windowOpen = value;
        validMask |= FIELD_WINDOW_OPEN;
    }

    public void setLongitudinalAccel(float value) {
        longitudinalAccel = value;
        validMask |= FIELD_LONGITUDINAL_ACCEL;
    }

    public void setLateralAccel(float value) {
        lateralAccel = value;
        validMask |= FIELD_LATERAL_ACCEL;
    }

    public void setOutsideTemp(float value) {
        outsideTemp = value;
        validMask |= FIELD_OUTSIDE_TEMP;
    }

    public void setTpmsData(int value) {
        tpmsData = value;
        validMask |= FIELD_TPMS_DATA;
    }

    public void setTpmsTempData(int value) {
        tpmsTempData = value;
        validMask |= FIELD_TPMS_TEMP_DATA;
    }

    // ---------------- 快照工具 ----------------

    /** 深拷贝（String 不可变，直接引用即可） */
    public VehicleData snapshot() {
        VehicleData copy = new VehicleData();
        copy.copyFrom(this);
        return copy;
    }

    /** 将 src 的全部字段（含掩码）拷贝到本对象 */
    public void copyFrom(VehicleData src) {
        validMask = src.validMask;
        changedMask = src.changedMask;
        engineRpm = src.engineRpm;
        coolantTemp = src.coolantTemp;
        vehicleSpeed = src.vehicleSpeed;
        steeringAngle = src.steeringAngle;
        gear = src.gear;
        vin = src.vin;
        turnSignalLeft = src.turnSignalLeft;
        turnSignalRight = src.turnSignalRight;
        oilLifePercent = src.oilLifePercent;
        maintenanceMileageKm = src.maintenanceMileageKm;
        maintenanceDays = src.maintenanceDays;
        throttleOpeningPercent = src.throttleOpeningPercent;
        fuelLevelPercent = src.fuelLevelPercent;
        rangeKm = src.rangeKm;
        tripAAvgFuel = src.tripAAvgFuel;
        instantFuel = src.instantFuel;
        histAvgFuel = src.histAvgFuel;
        batteryVoltage = src.batteryVoltage;
        odometerKm = src.odometerKm;
        tripADistanceKm = src.tripADistanceKm;
        inCarTemp = src.inCarTemp;
        headLightOn = src.headLightOn;
        hazardOn = src.hazardOn;
        windowOpen = src.windowOpen;
        longitudinalAccel = src.longitudinalAccel;
        lateralAccel = src.lateralAccel;
        outsideTemp = src.outsideTemp;
        tpmsData = src.tpmsData;
        tpmsTempData = src.tpmsTempData;
    }

    /**
     * 将 src 中已置位（validMask 置位）的字段叠加到本对象：仅覆盖 src 已采集的字段值并置位对应
     * validMask，不清除、不重置本对象其它字段与掩码。用于服务端在模拟会话期间把模拟帧叠加在
     * 真实快照之上生成消费端视图（真实快照 ⊕ 模拟覆盖）。
     */
    public void overlayFrom(VehicleData src) {
        if (src == null) {
            return;
        }
        if (src.hasField(FIELD_ENGINE_RPM)) {
            engineRpm = src.engineRpm;
            validMask |= FIELD_ENGINE_RPM;
        }
        if (src.hasField(FIELD_COOLANT_TEMP)) {
            coolantTemp = src.coolantTemp;
            validMask |= FIELD_COOLANT_TEMP;
        }
        if (src.hasField(FIELD_VEHICLE_SPEED)) {
            vehicleSpeed = src.vehicleSpeed;
            validMask |= FIELD_VEHICLE_SPEED;
        }
        if (src.hasField(FIELD_STEERING_ANGLE)) {
            steeringAngle = src.steeringAngle;
            validMask |= FIELD_STEERING_ANGLE;
        }
        if (src.hasField(FIELD_GEAR)) {
            gear = src.gear;
            validMask |= FIELD_GEAR;
        }
        if (src.hasField(FIELD_VIN)) {
            vin = src.vin;
            validMask |= FIELD_VIN;
        }
        if (src.hasField(FIELD_TURN_SIGNAL_LEFT)) {
            turnSignalLeft = src.turnSignalLeft;
            validMask |= FIELD_TURN_SIGNAL_LEFT;
        }
        if (src.hasField(FIELD_TURN_SIGNAL_RIGHT)) {
            turnSignalRight = src.turnSignalRight;
            validMask |= FIELD_TURN_SIGNAL_RIGHT;
        }
        if (src.hasField(FIELD_OIL_LIFE_PERCENT)) {
            oilLifePercent = src.oilLifePercent;
            validMask |= FIELD_OIL_LIFE_PERCENT;
        }
        if (src.hasField(FIELD_MAINTENANCE_MILEAGE)) {
            maintenanceMileageKm = src.maintenanceMileageKm;
            validMask |= FIELD_MAINTENANCE_MILEAGE;
        }
        if (src.hasField(FIELD_MAINTENANCE_DAYS)) {
            maintenanceDays = src.maintenanceDays;
            validMask |= FIELD_MAINTENANCE_DAYS;
        }
        if (src.hasField(FIELD_THROTTLE_OPENING)) {
            throttleOpeningPercent = src.throttleOpeningPercent;
            validMask |= FIELD_THROTTLE_OPENING;
        }
        if (src.hasField(FIELD_FUEL_LEVEL_PERCENT)) {
            fuelLevelPercent = src.fuelLevelPercent;
            validMask |= FIELD_FUEL_LEVEL_PERCENT;
        }
        if (src.hasField(FIELD_RANGE_KM)) {
            rangeKm = src.rangeKm;
            validMask |= FIELD_RANGE_KM;
        }
        if (src.hasField(FIELD_TRIP_A_AVG_FUEL)) {
            tripAAvgFuel = src.tripAAvgFuel;
            validMask |= FIELD_TRIP_A_AVG_FUEL;
        }
        if (src.hasField(FIELD_INSTANT_FUEL)) {
            instantFuel = src.instantFuel;
            validMask |= FIELD_INSTANT_FUEL;
        }
        if (src.hasField(FIELD_HIST_AVG_FUEL)) {
            histAvgFuel = src.histAvgFuel;
            validMask |= FIELD_HIST_AVG_FUEL;
        }
        if (src.hasField(FIELD_BATTERY_VOLTAGE)) {
            batteryVoltage = src.batteryVoltage;
            validMask |= FIELD_BATTERY_VOLTAGE;
        }
        if (src.hasField(FIELD_ODOMETER_KM)) {
            odometerKm = src.odometerKm;
            validMask |= FIELD_ODOMETER_KM;
        }
        if (src.hasField(FIELD_TRIP_A_DISTANCE_KM)) {
            tripADistanceKm = src.tripADistanceKm;
            validMask |= FIELD_TRIP_A_DISTANCE_KM;
        }
        if (src.hasField(FIELD_IN_CAR_TEMP)) {
            inCarTemp = src.inCarTemp;
            validMask |= FIELD_IN_CAR_TEMP;
        }
        if (src.hasField(FIELD_HEAD_LIGHT_ON)) {
            headLightOn = src.headLightOn;
            validMask |= FIELD_HEAD_LIGHT_ON;
        }
        if (src.hasField(FIELD_HAZARD_ON)) {
            hazardOn = src.hazardOn;
            validMask |= FIELD_HAZARD_ON;
        }
        if (src.hasField(FIELD_WINDOW_OPEN)) {
            windowOpen = src.windowOpen;
            validMask |= FIELD_WINDOW_OPEN;
        }
        if (src.hasField(FIELD_LONGITUDINAL_ACCEL)) {
            longitudinalAccel = src.longitudinalAccel;
            validMask |= FIELD_LONGITUDINAL_ACCEL;
        }
        if (src.hasField(FIELD_LATERAL_ACCEL)) {
            lateralAccel = src.lateralAccel;
            validMask |= FIELD_LATERAL_ACCEL;
        }
        if (src.hasField(FIELD_OUTSIDE_TEMP)) {
            outsideTemp = src.outsideTemp;
            validMask |= FIELD_OUTSIDE_TEMP;
        }
        if (src.hasField(FIELD_TPMS_DATA)) {
            tpmsData = src.tpmsData;
            validMask |= FIELD_TPMS_DATA;
        }
        if (src.hasField(FIELD_TPMS_TEMP_DATA)) {
            tpmsTempData = src.tpmsTempData;
            validMask |= FIELD_TPMS_TEMP_DATA;
        }
    }

    // ---------------- Parcelable ----------------

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(validMask);
        dest.writeInt(changedMask);
        dest.writeInt(engineRpm);
        dest.writeFloat(coolantTemp);
        dest.writeFloat(vehicleSpeed);
        dest.writeFloat(steeringAngle);
        dest.writeInt(gear);
        dest.writeString(vin);
        dest.writeByte((byte) (turnSignalLeft ? 1 : 0));
        dest.writeByte((byte) (turnSignalRight ? 1 : 0));
        dest.writeFloat(oilLifePercent);
        dest.writeInt(maintenanceMileageKm);
        dest.writeInt(maintenanceDays);
        dest.writeFloat(throttleOpeningPercent);
        dest.writeFloat(fuelLevelPercent);
        dest.writeInt(rangeKm);
        dest.writeFloat(tripAAvgFuel);
        dest.writeFloat(instantFuel);
        dest.writeFloat(histAvgFuel);
        dest.writeFloat(batteryVoltage);
        dest.writeLong(odometerKm);
        dest.writeFloat(tripADistanceKm);
        // 向后兼容：version 与新增字段追加在末尾，老版本消费端只读旧字段即止、忽略尾部
        dest.writeInt(PARCEL_VERSION);
        dest.writeFloat(inCarTemp);
        dest.writeByte((byte) (headLightOn ? 1 : 0));
        dest.writeByte((byte) (hazardOn ? 1 : 0));
        dest.writeByte((byte) (windowOpen ? 1 : 0));
        dest.writeFloat(longitudinalAccel);
        dest.writeFloat(lateralAccel);
        dest.writeFloat(outsideTemp);
        dest.writeInt(tpmsData);
        dest.writeInt(tpmsTempData);
    }

    public static final Creator<VehicleData> CREATOR = new Creator<VehicleData>() {
        @Override
        public VehicleData createFromParcel(Parcel in) {
            return new VehicleData(in);
        }

        @Override
        public VehicleData[] newArray(int size) {
            return new VehicleData[size];
        }
    };
}
