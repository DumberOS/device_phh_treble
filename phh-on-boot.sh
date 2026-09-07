#!/system/bin/sh

vndk="$(getprop persist.sys.vndk)"
[ -z "$vndk" ] && vndk="$(getprop ro.vndk.version |grep -oE '^[0-9]+')"

[ "$(getprop vold.decrypt)" = "trigger_restart_min_framework" ] && exit 0

setprop ctl.start media.swcodec

for i in wpa p2p;do
	if [ ! -f /data/misc/wifi/${i}_supplicant.conf ];then
		cp /vendor/etc/wifi/wpa_supplicant.conf /data/misc/wifi/${i}_supplicant.conf
	fi
	chmod 0660 /data/misc/wifi/${i}_supplicant.conf
	chown wifi:system /data/misc/wifi/${i}_supplicant.conf
done

if [ -f /vendor/bin/mtkmal ]; then
	if [ "$(getprop sys.phh.stock_mtk_ims)" = true ]; then
		if [ "$(getprop persist.mtk_ims_support)" != 1 ] || \
				[ "$(getprop persist.mtk_epdg_support)" != 1 ]; then
			setprop persist.mtk_ims_support 1
			setprop persist.mtk_epdg_support 1
			reboot
		fi
	elif [ "$(getprop persist.mtk_ims_support)" = 1 ] || \
			[ "$(getprop persist.mtk_epdg_support)" = 1 ]; then
		setprop persist.mtk_ims_support 0
		setprop persist.mtk_epdg_support 0
		reboot
	fi
fi

if grep -qF android.hardware.boot /vendor/manifest.xml || grep -qF android.hardware.boot /vendor/etc/vintf/manifest.xml ;then
	bootctl mark-boot-successful
fi

setprop ctl.restart sec-light-hal-2-0
if find /sys/firmware -name support_fod |grep -qE .;then
	setprop ctl.restart vendor.fps_hal
fi

setprop ctl.stop storageproxyd

sleep 10

minijailSrc=/system/system_ext/apex/com.android.vndk.v28/lib/libminijail.so
minijailSrc64=/system/system_ext/apex/com.android.vndk.v28/lib64/libminijail.so
if [ "$vndk" = 27 ];then
    mount $minijailSrc64 /vendor/lib64/libminijail_vendor.so
    mount $minijailSrc /vendor/lib/libminijail_vendor.so
fi

if [ "$vndk" = 28 ];then
    mount $minijailSrc64 /vendor/lib64/libminijail_vendor.so
    mount $minijailSrc /vendor/lib/libminijail_vendor.so
    mount $minijailSrc64 /system/lib64/vndk-28/libminijail.so
    mount $minijailSrc /system/lib/vndk-28/libminijail.so
    mount $minijailSrc64 /vendor/lib64/libminijail.so
    mount $minijailSrc /vendor/lib/libminijail.so
fi

# Remove Play Store/Play Services updates when the installed vending update
# looks like a different distro than the preinstalled one. Compare the
# preinstalled vending APK size with the updated /data payload size and reset
# both packages if either side is at least 8x larger than the other.
vending_system_apk() {
    for apk in \
        /product/priv-app/FakeStore/FakeStore.apk \
        /system/product/priv-app/FakeStore/FakeStore.apk \
        /product/priv-app/Phonesky/Phonesky.apk \
        /system/product/priv-app/Phonesky/Phonesky.apk
    do
        if [ -f "$apk" ]; then
            echo "$apk"
            return 0
        fi
    done
    return 1
}

pkg_update_dir() {
    code_path="$(pm path "$1" 2>/dev/null | sed -n 's#^package:##p' | head -n1)"
    case "$code_path" in
        /data/app/*)
            echo "${code_path%/*}"
            return 0
            ;;
    esac
    return 1
}

size_kb() {
    du -sk "$1" 2>/dev/null | awk 'NR == 1 { print $1 }'
}

apk_payload_size_kb() {
    target="$1"

    if [ -f "$target" ]; then
        size_kb "$target"
        return 0
    fi

    if [ -d "$target" ]; then
        find "$target" -maxdepth 1 -name '*.apk' -type f -exec stat -c '%s' {} \; 2>/dev/null | \
            awk '{ sum += $1 } END { if (sum > 0) print int((sum + 1023) / 1024) }'
        return 0
    fi

    return 1
}

bootlog() {
    log -t phh-on-boot "$*"
}

reset_pkg_to_system() {
    pkg="$1"

    if ! pm path "$pkg" 2>/dev/null | grep -q '^package:/data/'; then
        bootlog "skip reset for $pkg: not on /data"
        return 0
    fi

    bootlog "resetting $pkg from /data update"
    pm uninstall-system-updates "$pkg" >/dev/null 2>&1

    if pm path "$pkg" 2>/dev/null | grep -q '^package:/data/'; then
        bootlog "uninstall-system-updates did not remove $pkg, trying pm uninstall"
        pm uninstall "$pkg" >/dev/null 2>&1
    fi

    pm install-existing --user 0 "$pkg" >/dev/null 2>&1
    bootlog "final path for $pkg: $(pm path "$pkg" 2>/dev/null | head -n1)"
}

system_vending_apk="$(vending_system_apk)"
updated_vending_dir="$(pkg_update_dir com.android.vending)"
bootlog "system vending apk: ${system_vending_apk:-<none>}"
bootlog "updated vending dir: ${updated_vending_dir:-<none>}"

if [ -n "$system_vending_apk" ] && [ -n "$updated_vending_dir" ]; then
    system_vending_size_kb="$(apk_payload_size_kb "$system_vending_apk")"
    updated_vending_size_kb="$(apk_payload_size_kb "$updated_vending_dir")"
    bootlog "vending apk payload sizes kb: system=${system_vending_size_kb:-<none>} updated=${updated_vending_size_kb:-<none>}"

    if [ -n "$system_vending_size_kb" ] && [ -n "$updated_vending_size_kb" ] && \
        [ "$system_vending_size_kb" -gt 0 ] && [ "$updated_vending_size_kb" -gt 0 ]; then
        if [ "$updated_vending_size_kb" -ge $((system_vending_size_kb * 8)) ] || \
            [ "$system_vending_size_kb" -ge $((updated_vending_size_kb * 8)) ]; then
            bootlog "vending size mismatch matched 8x rule, resetting vending and gms"
            for pkg in com.android.vending com.google.android.gms; do
                reset_pkg_to_system "$pkg"
            done
        else
            bootlog "vending size mismatch did not match 8x rule"
        fi
    else
        bootlog "vending size check skipped: missing or zero sizes"
    fi
else
    bootlog "vending size check skipped: missing system apk or updated dir"
fi

#Clear looping services
sleep 30
getprop | \
    grep restarting | \
    sed -nE -e 's/\[([^]]*).*/\1/g'  -e 's/init.svc.(.*)/\1/p' |
    while read -r svc ;do
        setprop ctl.stop "$svc"
    done
