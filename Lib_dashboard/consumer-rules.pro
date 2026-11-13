# Lib_dashboard consumer ProGuard rules：随库自动应用到宿主 app。
# DashboardInfo AIDL/Parcelable 经 Binder 反射访问，混淆会导致跨进程调用失败。
-keep class com.automanager.dashboard.** { *; }
-keep interface com.automanager.dashboard.** { *; }
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}