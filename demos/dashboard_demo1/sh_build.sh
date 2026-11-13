#!/bin/bash
# ============================================================
# sh_build.sh - 编译 dashboard_demo1 Debug APK，并通过 adb 安装到车机
#
# 用法：
#   ./sh_build.sh                      # 编译并安装到当前在线设备
#   ANDROID_SERIAL=xxx ./sh_build.sh   # 多台设备时指定目标
#   ./sh_build.sh --no-install         # 仅编译，不安装
#
# 产物：app/build/outputs/apk/debug/app-debug.apk
# 包名：com.automanager.demo.dashboard
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

INSTALL=1
if [ "${1:-}" = "--no-install" ]; then
    INSTALL=0
fi

# ---------- 1. 选择 JDK（AGP 7.4.2 需要 JDK 11~17，优先 17） ----------
JDK_HOME="${ASSISTANT_JDK:-$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11 2>/dev/null || true)}"
if [ -z "${JDK_HOME:-}" ]; then
    JDK_HOME="${JAVA_HOME:-}"
fi
if [ -z "${JDK_HOME:-}" ] || [ ! -d "$JDK_HOME" ]; then
    echo "错误：未找到可用的 JDK（AGP 7.4.2 需要 JDK 11~17）" >&2
    exit 1
fi
export JAVA_HOME="$JDK_HOME"
echo ">>>> 使用 JDK: $JAVA_HOME"

# ---------- 2. 定位 Android SDK（ANDROID_HOME 优先，其次 local.properties） ----------
SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [ -z "$SDK_DIR" ] && [ -f local.properties ]; then
    SDK_DIR=$(sed -n 's/^[[:space:]]*sdk\.dir *= *//p' local.properties | head -1 | tr -d '\r')
fi
SDK_DIR="${SDK_DIR:-$HOME/Library/Android/sdk}"
if [ ! -d "$SDK_DIR" ]; then
    echo "错误：找不到 Android SDK（$SDK_DIR），请在 local.properties 中配置 sdk.dir" >&2
    exit 1
fi
export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"
echo ">>>> 使用 SDK: $SDK_DIR"

# ---------- 3. 编译 Debug APK ----------
echo ">>>> gradlew :app:assembleDebug ..."
./gradlew :app:assembleDebug --console=plain

APK="$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK" ]; then
    echo "错误：未找到 APK（$APK）" >&2
    exit 1
fi
echo ""
echo "构建完成：$APK"

if [ "$INSTALL" -eq 0 ]; then
    exit 0
fi

# ---------- 4. 定位 adb（优先 PATH，其次 SDK/platform-tools） ----------
ADB="${ADB:-$(command -v adb || true)}"
if [ -z "$ADB" ] && [ -x "$SDK_DIR/platform-tools/adb" ]; then
    ADB="$SDK_DIR/platform-tools/adb"
fi
if [ -z "$ADB" ]; then
    echo "错误：未找到 adb，无法安装" >&2
    exit 1
fi

# ---------- 5. 选择设备并安装 ----------
DEVICE="${ANDROID_SERIAL:-$("$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')}"
if [ -z "$DEVICE" ]; then
    echo "错误：未检测到已连接的车机（可用 ANDROID_SERIAL 指定设备）" >&2
    exit 1
fi

echo ">>>> 安装到设备 $DEVICE ..."
if ! "$ADB" -s "$DEVICE" install -r "$APK"; then
    echo "错误：安装失败。若车机上已安装同包名的其他签名版本，请先执行：" >&2
    echo "  $ADB -s $DEVICE uninstall com.automanager.demo.dashboard" >&2
    exit 1
fi
echo "安装完成。"
