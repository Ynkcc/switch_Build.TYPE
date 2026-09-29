#!/system/bin/sh
# late_start service: 兜底再设一次，防止 post-fs-data 阶段环境不可用。
# 只做幂等设置，不改动其它状态。

MODDIR=${0%/*}

CONF=/data/adb/switch_build_type.conf
[ -f "$CONF" ] || CONF="$MODDIR/config.conf"

[ -f "$CONF" ] || exit 0

enabled=$(sed -n 's/^[[:space:]]*enabled[[:space:]]*=[[:space:]]*\([0-9]*\).*$/\1/p' "$CONF" | head -n1 | tr -d '\r')
val=$(sed -n 's/^[[:space:]]*TYPE[[:space:]]*=[[:space:]]*\(.*\)$/\1/p' "$CONF" | head -n1 | tr -d '\r' | tr -d ' ')

[ "$enabled" = "1" ] || exit 0

case "$val" in
    user|userdebug|eng) "$MODDIR/switch" "$val" >> "$MODDIR/boot.log" 2>&1 ;;
esac
exit 0
