#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-/opt/android-ndk}}"
OUT="$ROOT/out"
STAGE="$OUT/switch_build_type"

if [ "$#" -gt 0 ]; then ABIS=("$@"); else ABIS=(armeabi-v7a arm64-v8a x86 x86_64); fi

"$NDK/ndk-build" -C "$ROOT" \
    NDK_PROJECT_PATH="$ROOT" \
    APP_BUILD_SCRIPT="$ROOT/jni/Android.mk" \
    NDK_APPLICATION_MK="$ROOT/jni/Application.mk" \
    APP_ABI="$(printf '%s ' "${ABIS[@]}")" \
    -j"$(nproc)"

rm -rf "$OUT"
mkdir -p "$STAGE/zygisk"
cp -rf "$ROOT"/module/* "$STAGE/"
for abi in "${ABIS[@]}"; do
    mkdir -p "$STAGE/bin/$abi"
    cp -f "$ROOT/libs/$abi/libswitch_build_type.so" "$STAGE/zygisk/$abi.so"
    cp -f "$ROOT/libs/$abi/switch" "$STAGE/bin/$abi/switch"
done
chmod 0755 "$STAGE/customize.sh" "$STAGE/post-fs-data.sh" "$STAGE/service.sh" "$STAGE/bin"/*/switch
chmod 0644 "$STAGE/module.prop" "$STAGE/config.conf"

rm -f "$OUT/switch_build_type.zip"
(cd "$STAGE" && zip -qry ../switch_build_type.zip .)

echo "$STAGE"
echo "$OUT/switch_build_type.zip"
