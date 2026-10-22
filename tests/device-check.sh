#!/data/data/com.termux/files/usr/bin/sh
set -eu
cd "$(dirname "$0")/.."
gspace_sdk="${GSPACE_SDK_DIR:-../pulse-player/tools}"
mkdir -p build/device-tests build/device-dex
javac --release 8 -encoding UTF-8 -classpath "$gspace_sdk/android.jar:build/classes" -d build/device-tests tests/DeviceCheck.java
jar cf build/device-tests.jar -C build/device-tests .
[ ! -f build/device-dex/classes.dex ] || chmod 600 build/device-dex/classes.dex
java -Xmx512m -cp "$gspace_sdk/r8.jar" com.android.tools.r8.D8 --lib "$gspace_sdk/android.jar" --min-api 26 --output build/device-dex build/classes.jar build/device-tests.jar
chmod 400 build/device-dex/classes.dex
CLASSPATH="$PWD/build/device-dex/classes.dex" /system/bin/app_process /system/bin cn.g.space.DeviceCheck "$PWD/build"
