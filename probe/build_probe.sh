#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
BT="$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)"
PLATFORM="$(ls -d "$SDK"/platforms/android-[0-9]* | sort -V | tail -1)"
ANDROID_JAR="$PLATFORM/android.jar"
OUT="$ROOT/build"
APK="${1:-$ROOT/../out/BuildTypeProbe.apk}"

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex" "$(dirname "$APK")"

echo "[1/6] aapt2 link  (build-tools $(basename "$BT"), platform $(basename "$PLATFORM"))"
"$BT/aapt2" compile --dir "$ROOT/res" -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/base.apk" \
    --manifest "$ROOT/AndroidManifest.xml" \
    -I "$ANDROID_JAR" --java "$OUT/gen" \
    --min-sdk-version 21 --target-sdk-version 28 \
    "$OUT/res.zip"

echo "[2/6] javac"
mapfile -t SRCS < <(find "$ROOT/src" "$OUT/gen" -name '*.java')
compiled=0
for v in 8 11 17; do
    if javac -source "$v" -target "$v" -Xlint:-options \
            -bootclasspath "$ANDROID_JAR" -classpath "$ANDROID_JAR" \
            -d "$OUT/classes" "${SRCS[@]}" 2>"$OUT/javac-$v.log"; then
        echo "      source/target $v"
        compiled=1
        break
    fi
done
if [ "$compiled" -ne 1 ]; then
    cat "$OUT"/javac-*.log >&2
    exit 1
fi

echo "[3/6] d8"
mapfile -t CLASSES < <(find "$OUT/classes" -name '*.class')
"$BT/d8" --min-api 21 --lib "$ANDROID_JAR" --output "$OUT/dex" "${CLASSES[@]}"

echo "[4/6] package"
cp -f "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q "$OUT/unsigned.apk" classes.dex)
"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "[5/6] sign"
KS="$ROOT/probe.keystore"
if [ ! -f "$KS" ]; then
    keytool -genkeypair -keystore "$KS" -alias probe -keyalg RSA -keysize 2048 \
        -validity 10000 -storepass android -keypass android -dname "CN=BuildTypeProbe"
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
    --out "$APK" "$OUT/aligned.apk"

echo "[6/6] verify"
"$BT/apksigner" verify --print-certs "$APK" | head -2
ls -l "$APK"
