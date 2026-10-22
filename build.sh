#!/data/data/com.termux/files/usr/bin/sh
set -eu
cd "$(dirname "$0")"
gspace_sdk="${GSPACE_SDK_DIR:-../pulse-player/tools}"
gspace_key="${GSPACE_KEYSTORE:-tools/g-space.keystore}"
gspace_password="${GSPACE_SIGNING_PASSWORD_FILE:-tools/signing-password}"
for gspace_file in "$gspace_sdk/android.jar" "$gspace_sdk/android-res.jar" "$gspace_sdk/r8.jar" "$gspace_key" "$gspace_password" signing-certificate.sha256; do
    if [ ! -f "$gspace_file" ]; then echo "缺少构建文件：$gspace_file" >&2; exit 1; fi
done
gspace_cert=$(keytool -exportcert -keystore "$gspace_key" -storepass:file "$gspace_password" -alias gspace 2>/dev/null | sha256sum | cut -d ' ' -f 1)
if [ "$gspace_cert" != "$(cat signing-certificate.sha256)" ]; then echo '签名指纹不匹配，停止构建。' >&2; exit 1; fi
mkdir -p build/generated build/classes build/dex
find build/classes -type f -name '*.class' -delete
aapt2 compile --dir res -o build/res.zip
aapt2 link -I "$gspace_sdk/android-res.jar" --manifest AndroidManifest.xml --java build/generated -o build/resources.apk build/res.zip
javac --release 8 -encoding UTF-8 -classpath "$gspace_sdk/android.jar" -d build/classes src/cn/g/space/*.java build/generated/cn/g/space/R.java
jar cf build/classes.jar -C build/classes .
java -Xmx512m -cp "$gspace_sdk/r8.jar" com.android.tools.r8.D8 --lib "$gspace_sdk/android.jar" --min-api 26 --output build/dex build/classes.jar
cp build/resources.apk build/unsigned.apk
jar uf build/unsigned.apk -C build/dex classes.dex
zipalign -f 4 build/unsigned.apk build/aligned.apk
apksigner sign --ks "$gspace_key" --ks-pass "file:$gspace_password" --out build/G-space-0.5.apk build/aligned.apk
apksigner verify --verbose build/G-space-0.5.apk
zipalign -c 4 build/G-space-0.5.apk
