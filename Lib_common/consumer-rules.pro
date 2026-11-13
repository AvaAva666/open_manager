# Lib_common consumer ProGuard rules：随库自动应用到宿主 app。
# VehicleData AIDL 接口/Stub 与 Parcelable 经 Binder 反射访问，混淆会导致跨进程调用失败。
-keep class com.automanager.vehicle.** { *; }
-keep interface com.automanager.vehicle.** { *; }
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}