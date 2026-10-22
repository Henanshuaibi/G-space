#!/data/data/com.termux/files/usr/bin/sh
set -eu
cd "$(dirname "$0")/.."
gspace_sdk="${GSPACE_SDK_DIR:-../pulse-player/tools}"
mkdir -p build/tests
javac --release 8 -encoding UTF-8 -classpath "$gspace_sdk/android.jar:build/classes" -d build/tests tests/CoreCheck.java tests/DragCheck.java
java -cp "build/classes:build/tests:$gspace_sdk/android.jar" cn.g.space.CoreCheck
java -cp "build/classes:build/tests:$gspace_sdk/android.jar" cn.g.space.DragCheck
python3 tests/package-check.py
