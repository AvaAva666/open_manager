# open_manager

简体中文 | [English](README.en.md)

## 前置要求

- **适用环境**：仅支持 Android 4.2.2 车机（API 17）。
- **适用车型**：本田十代思域、冠道、URV、CRV、皓影，以及讴歌 CDX。
- **依赖宿主**：车机需已安装 `com.automanager` APP。该 APP 负责车辆数据采集，并提供在原厂仪表盘上显示自定义 View 的调用能力；未安装时，本仓库的库与 Demo 均无法运行。
- **如何使用**：将编译好的 Demo APK 安装到车机后，打开 `com.automanager`，点击「自定义仪表」，本地仪表页面会自动扫描到新实现的仪表服务。

## 简介

车机仪表（Dashboard）与车辆数据 SDK 开源模块集，面向 Android 4.2.2（API 17）车机，
供 com.automanager 生态内的应用以 Gradle 源码方式复用。

仓库包含两个库模块和一个可运行的仪表 Demo：

```
open_manager/
├── Lib_common/            # 公共基础库：车型常量、车辆数据快照与消费端 SDK、日志/控件工具
├── Lib_dashboard/         # 仪表 SDK：仪表服务基类、AIDL 契约、仪表屏窗口工具
└── demos/
    └── dashboard_demo1/   # HelloWorld 仪表盘示例 App（独立 Gradle 工程）
```

## 模块说明

### Lib_common

| 类型 | 说明 |
| --- | --- |
| `com.automanager.Const` | 车型代码常量（`TBAA`（思域）/ `TEAA`（思域）/ `TLAA`（皓影）/ `TRTA`（CRV）/ `TRVA`（CRV）/ `TRWA`（CRV）/ `THAA`（冠道/UR-V）/ `TRNA`（讴歌CDX）），用于仪表边界与布局适配 |
| `com.automanager.vehicle.VehicleData` | 车辆数据全量快照（`Parcelable`，AIDL 跨进程）。`validMask` 标记哪些字段有效，`changedMask` 标记本次播报变化的字段；读字段前先用 `hasField(int)` 判断。新增字段按 Parcel 尾部追加并带版本号，兼容旧消费端 |
| `com.automanager.vehicle.VehicleDataManager` | 车辆数据消费端单例：绑定宿主数据服务、注册监听、主线程分发、断连指数退避重连（1s → 30s） |
| `IVehicleDataService` / `IVehicleDataListener`（AIDL） | 消费端契约：获取快照、注册/注销监听、读写自定义配置、调试用模拟数据会话 |
| `VehicleDataServiceUnavailableException` | 服务未连接时访问远端接口抛出的受检异常 |
| `VehicleCustomConfig` | 宿主配置注入桥：宿主 `Application` 通过 `setImplementation` 注册读写实现 |
| `com.manager.utils.CLog` | 轻量日志封装，`isDebug` 由宿主开启；`dfile` 默认不输出，可由宿主注入 `DLogHandler` 转发 |
| `com.automanager.view.NoPaddingTextView` | 去除上下多余 padding 的 TextView，便于图标与数字对齐 |

### Lib_dashboard

| 类型 | 说明 |
| --- | --- |
| `BaseDashboardService` | 仪表 Service 抽象基类。统一创建仪表屏 `WindowManager`、维护纯黑背景悬浮窗，并把 AIDL 回调切到主线程；子类只实现 4 个钩子 |
| `IDashboardService`（AIDL） | `dbShow(vehicleModel)` / `dbHide()` / `dbOpenConfig()` / `dbInfo()`，由宿主仪表管理页跨进程调用 |
| `DashboardInfo` | 仪表元信息：`title`、`thumb`（预览图字节）、`withCustomConfig`（是否有配置页）、`rank`（默认排序权重，越大越靠前） |
| `utils.Util` | `ACTION_DASHBOARD_SERVICE` 服务发现 action、`EXTRA_VEHICLE_MODEL` 车型 extra、车型边界（`getLayoutBound` / `getContentBound` / `getScale`）、`getRootViewLayoutParam`、`createWindowManager`（取第二块仪表屏）、`readThumbBytes`（缩略图）、黑底 `showBlackBackground` / `hideBlackBackground` |

## 环境要求

- JDK 17（AGP 7.4.2 要求）
- Android SDK Platform 33、Build-Tools 33.x
- Gradle 7.5（Demo 自带 wrapper）
- `compileSdk 33`，`minSdk 17` / `targetSdk 17`，可运行于 Android 4.2.2 车机
- 全 support 体系，宿主工程需保持 `android.useAndroidX=false`、`android.enableJetifier=false`
- 仪表窗口依赖双屏环境：`Util.createWindowManager` 取最后一块 Display，单屏设备会抛异常（基类捕获并记录日志）

运行 Demo 前，在 `demos/dashboard_demo1/local.properties` 写入本机 SDK 路径（该文件不入库）：

```properties
sdk.dir=/path/to/Android/sdk
```

## 构建 Demo

```sh
cd demos/dashboard_demo1
./gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`。

Demo 包名为 `com.automanager.demo.dashboard`，核心实现见
`app/src/main/java/com/automanager/demo/dashboard/DashboardDemoService.java`。
安装后由宿主（Assistant 的本地仪表页）扫描 `ACTION_DASHBOARD_SERVICE` 自动发现，
选中后仪表屏输出 HelloWorld，并在下方显示通过 `VehicleDataManager` 订阅 `VehicleData`
（`FIELD_ENGINE_RPM`）得到的实时转速；预览卡片标题为「HelloWorld 演示」。

## 接入到自己的 App

以源码工程方式复用，不发布 Maven 产物。`settings.gradle`：

```groovy
include ':app', ':Lib_common', ':Lib_dashboard'
project(':Lib_common').projectDir = new File('../open_manager/Lib_common')
project(':Lib_dashboard').projectDir = new File('../open_manager/Lib_dashboard')
```

模块 `build.gradle`：

```groovy
dependencies {
    implementation project(':Lib_common')
    implementation project(':Lib_dashboard')
}
```

> `Lib_common` 不依赖任何宿主二进制 AAR。`CLog.dfile` 默认不输出，宿主可按需注入
> `CLog.dlogHandler`，将文件日志转发到自有通道。

## 开发自定义仪表

1. 继承 `BaseDashboardService`，实现 4 个钩子（均在主线程回调）；
2. 在 Manifest 中导出 Service 并声明发现 action；
3. 声明仪表屏悬浮窗权限。

```java
public class MyDashboardService extends BaseDashboardService {
    private View rootView;

    @Override
    protected void onDashboardShow() {
        // 同车型重复 dbShow 时先移除旧视图，保证幂等
        removeRootView();
        rootView = new TextView(this);
        ((TextView) rootView).setText("My Dashboard");
        WindowManager.LayoutParams lp =
                Util.getRootViewLayoutParam(vehicleModel, mockWindowManager());
        try {
            getWindowManager().addView(rootView, lp);
        } catch (Throwable e) {
            // addView 失败必须复位，否则残留状态会阻塞后续 dbShow
            rootView = null;
        }
    }

    @Override
    protected void onDashboardHide() {
        removeRootView();
    }

    private void removeRootView() {
        if (rootView == null) return;
        try {
            getWindowManager().removeView(rootView);
        } catch (Exception ignored) {
        }
        rootView = null;
    }

    @Override
    protected void onDashboardOpenConfig() {
        // DashboardInfo.withCustomConfig = false 时无需实现
    }

    @Override
    protected DashboardInfo onDashboardInfo() {
        DashboardInfo info = new DashboardInfo();
        info.title = "My Dashboard";
        info.thumb = Util.readThumbBytes(getResources(), R.drawable.my_preview);
        info.withCustomConfig = false;
        info.rank = 1900; // 宿主内置仪表为 2000+，数值越大越靠前
        return info;
    }
}
```

Manifest：

```xml
<uses-permission android:name="android.permission.INTERNAL_SYSTEM_WINDOW" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.SYSTEM_OVERLAY_WINDOW" />

<service
    android:name=".MyDashboardService"
    android:exported="true">
    <intent-filter>
        <action android:name="com.automanager.dashboard.action.DASHBOARD_SERVICE" />
    </intent-filter>
</service>
```

要点：

- `BaseDashboardService` 负责窗口创建、纯黑背景和主线程调度，不要自行操作 `WindowManager` 的添加顺序；
- `vehicleModel` 由宿主随 bind intent 传入（`Util.EXTRA_VEHICLE_MODEL`），切换车型时基类会先隐藏旧视图；
- `getWindowManager()` 仅在 `dbShow` 之后可用；`mockWindowManager()` 返回 `true` 时取主屏（仅调试用）。

## 订阅车辆数据

```java
VehicleDataManager mgr = VehicleDataManager.getInstance();
mgr.connect(context);                       // 隐式绑定宿主服务
// 跨应用消费时显式指定宿主包名：
// mgr.connect(context, "com.automanager", null);

mgr.addListener(new VehicleDataManager.OnVehicleDataListener() {
    @Override
    public void onVehicleDataChanged(VehicleData data) {
        if (data.hasField(VehicleData.FIELD_VEHICLE_SPEED)) {
            float speed = data.vehicleSpeed; // km/h
        }
    }
});

// 不再需要时（Application/页面销毁）：
mgr.removeListener(listener);
mgr.disconnect();
```

- 注册成功后服务端立即回调一次当前全量快照，之后按字段变化持续播报；
- 回调已切到主线程；服务进程重启后自动重连并重新注册监听；
- 本 SDK 为只读消费通道，不包含车辆数据采集/上报接口；
- `getVehicleData()` / `putCustomConfig()` 等远端调用在未连接时抛
  `VehicleDataServiceUnavailableException`；调试可用 `beginMock()` / `reportMockData()` / `endMock()` 注入模拟快照。

## 测试

```sh
python3 Lib_dashboard/tests/test_lifecycle.py
```

测试用 `javac` + 宿主窗口/队列 fixture 直接编译并运行真实的 `BaseDashboardService`，
验证 `dbShow` / `dbHide` 生命周期、主线程队列与黑底视图顺序，无需 Android 设备。
