#!/sbin/sh
abi=$(getprop ro.product.cpu.abi)
cp -f "$MODPATH/bin/$abi/switch" "$MODPATH/switch"
chmod 0755 "$MODPATH/switch"
rm -rf "$MODPATH/bin"
