#!/system/bin/sh
# post-fs-data: 在 zygote 启动之前把 sys.buildtype 写回内存属性区。
# sys.buildtype 是运行期由 resetprop 写入的，重启后属性区重建，值会丢失，
# 所以每次开机都必须重新设置，否则 Zygisk 模块读不到目标值、等于没生效。

MODDIR=${0%/*}
LOG="$MODDIR/boot.log"

# 外部配置优先，其次模块内 config.conf
CONF=/data/adb/switch_build_type.conf
[ -f "$CONF" ] || CONF="$MODDIR/config.conf"

log() { echo "[$(date '+%m-%d %H:%M:%S')] post-fs-data: $*" >> "$LOG"; }

[ -f "$CONF" ] || { log "no config, skip"; exit 0; }

enabled=$(sed -n 's/^[[:space:]]*enabled[[:space:]]*=[[:space:]]*\([0-9]*\).*$/\1/p' "$CONF" | head -n1 | tr -d '\r')
val=$(sed -n 's/^[[:space:]]*TYPE[[:space:]]*=[[:space:]]*\(.*\)$/\1/p' "$CONF" | head -n1 | tr -d '\r' | tr -d ' ')

[ "$enabled" = "1" ] || { log "enabled=$enabled, skip"; exit 0; }

case "$val" in
    user|userdebug|eng) ;;
    *) log "invalid TYPE='$val', skip"; exit 0 ;;
esac

out=$("$MODDIR/switch" "$val" 2>&1)
log "TYPE=$val -> $out"
exit 0
