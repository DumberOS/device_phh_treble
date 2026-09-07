#!/system/bin/sh

PHH_STATE_DIR=/metadata/phh
mkdir -p "$PHH_STATE_DIR"
chown system:system "$PHH_STATE_DIR" 2>/dev/null || true
chmod 0775 "$PHH_STATE_DIR" 2>/dev/null || true

if [ -z "$debug" ] && [ -f /cache/phh-log ];then
	mkdir -p /cache/phh
	debug=1 exec sh -x "$(readlink -f -- "$0")" > /cache/phh/logs 2>&1
else
    # Allow accessing logs from system app
    # Protected via SELinux for other apps
    chmod 0755 /cache/phh
    chmod 0644 /cache/phh/logs
fi

if [ -f /cache/phh-adb ];then
    setprop ctl.stop adbd
    setprop ctl.stop adbd_apex
    mount -t configfs none /config
    rm -Rf /config/usb_gadget
    mkdir -p /config/usb_gadget/g1

    echo 0x12d1 > /config/usb_gadget/g1/idVendor
    echo 0x103A > /config/usb_gadget/g1/idProduct
    mkdir -p /config/usb_gadget/g1/strings/0x409
    echo phh > /config/usb_gadget/g1/strings/0x409/serialnumber
    echo phh > /config/usb_gadget/g1/strings/0x409/manufacturer
    echo phh > /config/usb_gadget/g1/strings/0x409/product

    mkdir /config/usb_gadget/g1/functions/ffs.adb
    mkdir /config/usb_gadget/g1/functions/mtp.gs0
    mkdir /config/usb_gadget/g1/functions/ptp.gs1

    mkdir /config/usb_gadget/g1/configs/c.1/
    mkdir /config/usb_gadget/g1/configs/c.1/strings/0x409
    echo 'ADB MTP' > /config/usb_gadget/g1/configs/c.1/strings/0x409/configuration

    mkdir /dev/usb-ffs
    chmod 0770 /dev/usb-ffs
    chown shell:shell /dev/usb-ffs
    mkdir /dev/usb-ffs/adb/
    chmod 0770 /dev/usb-ffs/adb
    chown shell:shell /dev/usb-ffs/adb

    mount -t functionfs -o uid=2000,gid=2000 adb /dev/usb-ffs/adb

    /apex/com.android.adbd/bin/adbd &

    sleep 1
    echo none > /config/usb_gadget/g1/UDC
    ln -s /config/usb_gadget/g1/functions/ffs.adb /config/usb_gadget/g1/configs/c.1/f1
    ls /sys/class/udc |head -n 1 > /config/usb_gadget/g1/UDC

    sleep 2
    echo 2 > /sys/devices/virtual/android_usb/android0/port_mode
fi

vndk="$(getprop persist.sys.vndk)"
[ -z "$vndk" ] && vndk="$(getprop ro.vndk.version |grep -oE '^[0-9]+')"

if [ "$vndk" = 26 ];then
	resetprop_phh ro.vndk.version 26
fi

setprop sys.usb.ffs.aio_compat true

case "$(getprop ro.product.vendor.model)" in
    "Dumber mini"|"S9")
        resetprop_phh ro.sf.lcd_density 210
        resetprop_phh persist.sys.phh.touch_recovery_on_wake 1
        ;;
esac

if getprop ro.vendor.build.fingerprint | grep -q -i -e Blackview/BV9500Plus;then
    setprop persist.adb.nonblocking_ffs true
else
    setprop persist.adb.nonblocking_ffs false
fi

fixSPL() {
    if [ "$(getprop ro.product.cpu.abi)" = "armeabi-v7a" ]; then
        setprop ro.keymaster.mod 'AOSP on ARM32'
    else
        setprop ro.keymaster.mod 'AOSP on ARM64'
    fi
    img="$(find /dev/block -type l -iname kernel"$(getprop ro.boot.slot_suffix)" | grep by-name | head -n 1)"
    [ -z "$img" ] && img="$(find /dev/block -type l -iname boot"$(getprop ro.boot.slot_suffix)" | grep by-name | head -n 1)"
    if [ -n "$img" ]; then
    #Rewrite SPL/Android version if needed
    # Read Android version from vbmeta (often trustkernel's root of trust)
    Arelease="$(strings -n 2 /dev/block/by-name/vbmeta* | grep -A1 com.android.build.system.os_version | grep -E '^[0-9]+$' | sort -n | head -n1)"
    # Otherwise read it from boot.img
    [ -z "$Arelease" ] && Arelease="$(getSPL "$img" android)"
    spl="$(getSPL "$img" spl)"
    setprop ro.keymaster.xxx.release "${Arelease}"
    setprop ro.keymaster.xxx.security_patch "$spl"
    if [ -z "$Arelease" ] || [ -z "$spl" ];then
        return 0
    fi
    # Some devices will want true vbmeta_state and verifiedbootstate
    # Setup those properties redirect for "keymaster" prop redirects
    setprop ro.keymaster.xxx.vbmeta_state unlocked
    setprop ro.keymaster.xxx.verifiedbootstate orange

        setprop ro.keymaster.brn Android

        for f in \
            /vendor/lib64/hw/android.hardware.keymaster@3.0-impl-qti.so /vendor/lib/hw/android.hardware.keymaster@3.0-impl-qti.so \
            /system/lib64/vndk-26/libsoftkeymasterdevice.so /vendor/bin/teed \
            /apex/com.android.vndk.v26/lib/libsoftkeymasterdevice.so  \
            /apex/com.android.vndk.v26/lib64/libsoftkeymasterdevice.so  \
            /system/lib64/vndk/libsoftkeymasterdevice.so /system/lib/vndk/libsoftkeymasterdevice.so \
            /system/lib/vndk-26/libsoftkeymasterdevice.so \
            /system/lib/vndk-27/libsoftkeymasterdevice.so /system/lib64/vndk-27/libsoftkeymasterdevice.so \
	    /vendor/lib/libkeymaster3device.so /vendor/lib64/libkeymaster3device.so \
        /vendor/lib/libMcTeeKeymaster.so /vendor/lib64/libMcTeeKeymaster.so \
        /vendor/lib/hw/libMcTeeKeymaster.so /vendor/lib64/hw/libMcTeeKeymaster.so $additional; do
            [ ! -f "$f" ] && continue
            # shellcheck disable=SC2010
            ctxt="$(ls -lZ "$f" | grep -oE 'u:object_r:[^:]*:s0')"
            b="$(echo "$f" | tr / _)"

            cp -a "$f" "/mnt/phh/$b"
            sed -i \
                -e 's/ro.build.version.release/ro.keymaster.xxx.release/g' \
                -e 's/ro.build.version.security_patch/ro.keymaster.xxx.security_patch/g' \
                -e 's/ro.product.model/ro.keymaster.mod/g' \
                -e 's/ro.product.brand/ro.keymaster.brn/g' \
                "/mnt/phh/$b"
            chcon "$ctxt" "/mnt/phh/$b"
            mount -o bind "/mnt/phh/$b" "$f"
        done
        if [ "$(getprop init.svc.keymaster-3-0)" = "running" ]; then
            setprop ctl.restart keymaster-3-0
        fi
        if [ "$(getprop init.svc.teed)" = "running" ]; then
            setprop ctl.restart teed
        fi
    fi
}

changeKeylayout() {
    mpk="/mnt/phh/keylayout"
    cp -a /system/usr/keylayout /mnt/phh/keylayout
    changed=false

    if getprop ro.product.vendor.device |grep -qi -e mfh505glm -e fh50lm; then
        cp /system/phh/empty /mnt/phh/keylayout/uinput-fpc.kl
        chmod 0644 /mnt/phh/keylayout/uinput-fpc.kl
        changed=true
    fi

    if [ "$changed" = true ]; then
        mount -o bind /mnt/phh/keylayout /system/usr/keylayout
        restorecon -R /system/usr/keylayout
    fi
}

changeVolumeCurves() {
    ORIG_PATH="/vendor/etc/audio_policy_volumes.xml"
    MOD_DIR="/mnt/phh"
    MOD_PATH="$MOD_DIR/audio_policy_volumes.xml"

    mkdir -p "$MOD_DIR"
    cp -a "$ORIG_PATH" "$MOD_PATH"
    PATCH_DATA='--- audio_policy_volumes.xml
+++ audio_policy_volumes.xml
@@ -36,10 +36,10 @@
         <point>100,0</point>
     </volume>
     <volume stream="AUDIO_STREAM_VOICE_CALL" deviceCategory="DEVICE_CATEGORY_EARPIECE">
-        <point>0,-2400</point>
-        <point>33,-1600</point>
-        <point>66,-800</point>
-        <point>100,0</point>
+        <point>0,-5000</point>
+        <point>33,-3000</point>
+        <point>66,-1500</point>
+        <point>100,0</point>
     </volume>
     <volume stream="AUDIO_STREAM_VOICE_CALL" deviceCategory="DEVICE_CATEGORY_EXT_MEDIA"
                                              ref="DEFAULT_MEDIA_VOLUME_CURVE"/>
'

    # No heredoc => no shell temp file needed
    printf '%s' "$PATCH_DATA" | patch -p0 -d "$MOD_DIR"

    chcon u:object_r:vendor_configs_file:s0 $MOD_PATH
    mount -o bind "$MOD_PATH" "$ORIG_PATH"
}

fixAudioDevice() {
    ORIG_PATH="/vendor/etc/audio_device.xml"
    MOD_DIR="/mnt/phh"
    MOD_PATH="$MOD_DIR/audio_device.xml"

    mkdir -p "$MOD_DIR"
    cp -a "$ORIG_PATH" "$MOD_PATH"
    PATCH_DATA='--- audio_device.xml
+++ audio_device.xml
@@ -164,12 +164,16 @@
         <kctl name="Audio_MicSource1_Setting" value="ADC1" />
         <kctl name="Audio_ADC_1_Switch" value="On" />
         <kctl name="Audio_ADC_2_Switch" value="On" />
-        <kctl name="Audio_Preamp1_Switch" value="IN_ADC3" />
-        <kctl name="Audio_Preamp2_Switch" value="IN_ADC1" />
+        <kctl name="Audio_Preamp1_Switch" value="IN_ADC1" />
+        <kctl name="Audio_Preamp2_Switch" value="OPEN" />
+        <kctl name="Audio_PGA1_Setting" value="18Db" />
+        <kctl name="Audio_PGA2_Setting" value="0Db" />
     </path>
     <path name="builtin_Mic_BackMic" value="turnoff">
         <kctl name="Audio_Preamp1_Switch" value="OPEN" />
         <kctl name="Audio_Preamp2_Switch" value="OPEN" />
+        <kctl name="Audio_PGA1_Setting" value="0Db" />
+        <kctl name="Audio_PGA2_Setting" value="0Db" />
         <kctl name="Audio_ADC_1_Switch" value="Off" />
         <kctl name="Audio_ADC_2_Switch" value="Off" />
     </path>
@@ -178,11 +182,15 @@
         <kctl name="Audio_ADC_1_Switch" value="On" />
         <kctl name="Audio_ADC_2_Switch" value="On" />
         <kctl name="Audio_Preamp1_Switch" value="IN_ADC1" />
-        <kctl name="Audio_Preamp2_Switch" value="IN_ADC3" />
+        <kctl name="Audio_Preamp2_Switch" value="OPEN" />
+        <kctl name="Audio_PGA1_Setting" value="18Db" />
+        <kctl name="Audio_PGA2_Setting" value="0Db" />
     </path>
     <path name="builtin_Mic_BackMic_Inverse" value="turnoff">
         <kctl name="Audio_Preamp1_Switch" value="OPEN" />
         <kctl name="Audio_Preamp2_Switch" value="OPEN" />
+        <kctl name="Audio_PGA1_Setting" value="0Db" />
+        <kctl name="Audio_PGA2_Setting" value="0Db" />
         <kctl name="Audio_ADC_1_Switch" value="Off" />
         <kctl name="Audio_ADC_2_Switch" value="Off" />
     </path>
'

    printf '%s' "$PATCH_DATA" | patch -p0 -d "$MOD_DIR"
    chcon u:object_r:vendor_configs_file:s0 $MOD_PATH
    mount -o bind "$MOD_PATH" "$ORIG_PATH"
}

fixVolumeGainMap() {
    ORIG_PATH="/vendor/etc/audio_param/VolumeGainMap_AudioParam.xml"
    MOD_DIR="/mnt/phh"
    MOD_PATH="$MOD_DIR/VolumeGainMap_AudioParam.xml"

    [ ! -f "$ORIG_PATH" ] && return 0

    mkdir -p "$MOD_DIR"
    cp -a "$ORIG_PATH" "$MOD_PATH"
    PATCH_DATA='--- VolumeGainMap_AudioParam.xml
+++ VolumeGainMap_AudioParam.xml
@@ -42,9 +42,9 @@
       <Param name="dl_analog_type" value="2"/>
     </ParamUnit>
     <ParamUnit param_id="3">
-      <Param name="dl_total_gain" value="-1,-2,-3,-4,-5,-6,-7,-8,-9,-10,-11,-12,-13,-14,-15,-16,-17,-18,-19,-20,-21,-22,-23,-24,-25,-26,-27,-28,-29,-30,-31,-32,-33,-34,-35,-36,-37,-38,-39,-40,-41"/>
+      <Param name="dl_total_gain" value="-19,-20,-21,-22,-23,-24,-25,-26,-27,-28,-29,-30,-31,-32,-33,-34,-35,-36,-37,-38,-39,-40,-41,-42,-43,-44,-45,-46,-47,-48,-49,-50,-51,-52,-53,-54,-55,-56,-57,-58,-59"/>
       <Param name="dl_analog_gain" value="-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1"/>
-      <Param name="dl_digital_gain" value="0,-1,-2,-3,-4,-5,-6,-7,-8,-9,-10,-11,-12,-13,-14,-15,-16,-17,-18,-19,-20,-21,-22,-23,-24,-25,-26,-27,-28,-29,-30,-31,-32,-33,-34,-35,-36,-37,-38,-39,-40"/>
+      <Param name="dl_digital_gain" value="-18,-19,-20,-21,-22,-23,-24,-25,-26,-27,-28,-29,-30,-31,-32,-33,-34,-35,-36,-37,-38,-39,-40,-41,-42,-43,-44,-45,-46,-47,-48,-49,-50,-51,-52,-53,-54,-55,-56,-57,-58"/>
       <Param name="dl_total_gain_decimal" value="160,156,152,148,144,140,136,132,128,124,120,116,112,108,104,100,96,92,88,84,80,76,72,68,64,60,56,52,48,44,40,36,32,28,24,20,16,12,8,4,0"/>
       <Param name="dl_analog_type" value="3"/>
     </ParamUnit>
@@ -56,9 +56,9 @@
       <Param name="dl_analog_type" value="1"/>
     </ParamUnit>
     <ParamUnit param_id="5">
-      <Param name="dl_total_gain" value="0,-1,-2,-3,-4,-5,-6,-7,-8,-9,-10,-11,-12,-13,-14,-15,-16,-17,-18,-19,-20,-21,-22,-23,-24,-25,-26,-27,-28,-29,-30,-31,-32,-33,-34,-35,-36,-37,-38,-39,-40"/>
+      <Param name="dl_total_gain" value="-18,-19,-20,-21,-22,-23,-24,-25,-26,-27,-28,-29,-30,-31,-32,-33,-34,-35,-36,-37,-38,-39,-40,-41,-42,-43,-44,-45,-46,-47,-48,-49,-50,-51,-52,-53,-54,-55,-56,-57,-58"/>
       <Param name="dl_analog_gain" value="0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0"/>
-      <Param name="dl_digital_gain" value="0,-1,-2,-3,-4,-5,-6,-7,-8,-9,-10,-11,-12,-13,-14,-15,-16,-17,-18,-19,-20,-21,-22,-23,-24,-25,-26,-27,-28,-29,-30,-31,-32,-33,-34,-35,-36,-37,-38,-39,-40"/>
+      <Param name="dl_digital_gain" value="-18,-19,-20,-21,-22,-23,-24,-25,-26,-27,-28,-29,-30,-31,-32,-33,-34,-35,-36,-37,-38,-39,-40,-41,-42,-43,-44,-45,-46,-47,-48,-49,-50,-51,-52,-53,-54,-55,-56,-57,-58"/>
       <Param name="dl_total_gain_decimal" value="160,156,152,148,144,140,136,132,128,124,120,116,112,108,104,100,96,92,88,84,80,76,72,68,64,60,56,52,48,44,40,36,32,28,24,20,16,12,8,4,0"/>
       <Param name="dl_analog_type" value="-1"/>
     </ParamUnit>
@@ -70,9 +70,9 @@
       <Param name="dl_analog_type" value="-1"/>
     </ParamUnit>
     <ParamUnit param_id="7">
-      <Param name="dl_total_gain" value="2,1,0,-1,-2,-3,-4,-5,-6,-7,-8,-9,-10,-11,-12,-13,-14,-15,-16,-17,-18,-19,-20,-21,-22,-23,-24,-25,-26,-27,-28,-29,-30,-31,-32,-33,-34,-35,-36,-37,-38"/>
+      <Param name="dl_total_gain" value="-16,-17,-18,-19,-20,-21,-22,-23,-24,-25,-26,-27,-28,-29,-30,-31,-32,-33,-34,-35,-36,-37,-38,-39,-40,-41,-42,-43,-44,-45,-46,-47,-48,-49,-50,-51,-52,-53,-54,-55,-56"/>
       <Param name="dl_analog_gain" value="8,7,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6"/>
-      <Param name="dl_digital_gain" value="0,0,0,-1,-2,-3,-4,-5,-6,-7,-8,-9,-10,-11,-12,-13,-14,-15,-16,-17,-18,-19,-20,-21,-22,-23,-24,-25,-26,-27,-28,-29,-30,-31,-32,-33,-34,-35,-36,-37,-38"/>
+      <Param name="dl_digital_gain" value="-18,-18,-18,-19,-20,-21,-22,-23,-24,-25,-26,-27,-28,-29,-30,-31,-32,-33,-34,-35,-36,-37,-38,-39,-40,-41,-42,-43,-44,-45,-46,-47,-48,-49,-50,-51,-52,-53,-54,-55,-56"/>
       <Param name="dl_total_gain_decimal" value="160,156,152,148,144,140,136,132,128,124,120,116,112,108,104,100,96,92,88,84,80,76,72,68,64,60,56,52,48,44,40,36,32,28,24,20,16,12,8,4,0"/>
       <Param name="dl_analog_type" value="2"/>
     </ParamUnit>
'

    printf '%s' "$PATCH_DATA" | patch -p0 -d "$MOD_DIR"
    chcon u:object_r:vendor_configs_file:s0 $MOD_PATH
    mount -o bind "$MOD_PATH" "$ORIG_PATH"
}

mkdir -p /mnt/phh/
mount -t tmpfs -o rw,nodev,relatime,mode=755,gid=0 none /mnt/phh || true
mkdir /mnt/phh/empty_dir

STOCK_MTK_IMS_FLAG="$PHH_STATE_DIR/dumber_mini_stock_ims"
STOCK_MTK_IMS_STAGE=/system/etc/phh/stock_mtk_ims

stock_mtk_ims_targets='/system/priv-app/ImsService
/system/priv-app/MtkGbaService
/system/priv-app/MtkTelephonyAssist
/system/app/mediatek-res
/system/system_ext/priv-app/CarrierConfig'

hide_stock_mtk_ims_mountpoints() {
    for target in \
        /system/priv-app/ImsService \
        /system/priv-app/MtkGbaService \
        /system/priv-app/MtkTelephonyAssist \
        /system/app/mediatek-res; do
        mount -o bind /mnt/phh/empty_dir "$target" || return 1
    done
}

unmount_stock_mtk_ims() {
    for target in $stock_mtk_ims_targets; do
        umount "$target" 2>/dev/null || true
    done
}

mount_stock_mtk_ims() {
    mount -o bind "$STOCK_MTK_IMS_STAGE/ImsService" \
        /system/priv-app/ImsService || return 1
    mount -o bind "$STOCK_MTK_IMS_STAGE/MtkGbaService" \
        /system/priv-app/MtkGbaService || return 1
    mount -o bind "$STOCK_MTK_IMS_STAGE/MtkTelephonyAssist" \
        /system/priv-app/MtkTelephonyAssist || return 1
    mount -o bind "$STOCK_MTK_IMS_STAGE/mediatek-res" \
        /system/app/mediatek-res || return 1
    mount -o bind "$STOCK_MTK_IMS_STAGE/MtkCarrierConfig" \
        /system/system_ext/priv-app/CarrierConfig || return 1
}

if [ -f "$STOCK_MTK_IMS_FLAG" ] && mount_stock_mtk_ims; then
    setprop sys.phh.stock_mtk_ims true
else
    if [ -f "$STOCK_MTK_IMS_FLAG" ]; then
        log -t PHH "Stock MTK IMS staging incomplete; falling back to TrebleApp IMS"
    fi
    unmount_stock_mtk_ims
    hide_stock_mtk_ims_mountpoints || \
        log -t PHH "Failed to hide one or more stock MTK IMS mount points"
    setprop sys.phh.stock_mtk_ims false
fi

fixSPL

changeKeylayout

if [ ! -f "$PHH_STATE_DIR/disable_voip_earpiece" ]; then
    changeVolumeCurves
fi

if [ ! -f "$PHH_STATE_DIR/disable_wa_mic_fix" ]; then
    fixAudioDevice
fi

if [ ! -f "$PHH_STATE_DIR/disable_tel_earpiece" ]; then
    case "$(getprop ro.product.vendor.model)" in
        "Dumber mini"|"S9")
            # Skip the fix for Dumber Mini
            ;;
        *)
            fixVolumeGainMap
            ;;
    esac
fi

foundFingerprint=false

if [ "$foundFingerprint" = false ];then
    mount -o bind system/phh/empty /system/etc/permissions/android.hardware.fingerprint.xml
fi

if getprop ro.wlan.mtk.wifi.5g | grep -q 1; then
    setprop persist.sys.overlay.wifi5g true
fi

for f in /vendor/lib/mtk-ril.so /vendor/lib64/mtk-ril.so /vendor/lib/libmtk-ril.so /vendor/lib64/libmtk-ril.so; do
    [ ! -f $f ] && continue
    # shellcheck disable=SC2010
    ctxt="$(ls -lZ "$f" | grep -oE 'u:object_r:[^:]*:s0')"
    b="$(echo "$f" | tr / _)"

    cp -a "$f" "/mnt/phh/$b"
    sed -i \
        -e 's/AT+EAIC=2/AT+EAIC=3/g' \
        "/mnt/phh/$b"
    chcon "$ctxt" "/mnt/phh/$b"
    mount -o bind "/mnt/phh/$b" "$f"

    setprop persist.sys.phh.radio.force_cognitive true
    setprop persist.sys.radio.ussd.fix true
done

mount -o bind /system/phh/empty /vendor/lib/libpdx_default_transport.so
mount -o bind /system/phh/empty /vendor/lib64/libpdx_default_transport.so

mount -o bind /system/phh/empty /vendor/overlay/SysuiDarkTheme/SysuiDarkTheme.apk || true
mount -o bind /system/phh/empty /vendor/overlay/SysuiDarkTheme/SysuiDarkThemeOverlay.apk || true

if busybox_phh unzip -p /vendor/app/ims/ims.apk classes.dex | grep -qF -e Landroid/telephony/ims/feature/MmTelFeature -e Landroid/telephony/ims/feature/MMTelFeature; then
    mount -o bind /system/phh/empty /vendor/app/ims/ims.apk
fi

if getprop ro.product.model | grep -qF ANE; then
    setprop debug.sf.latch_unsignaled 1
fi

if [ $(find /vendor/etc/audio -type f |wc -l) -le 3 ];then
	mount -o bind /mnt/phh/empty_dir /vendor/etc/audio || true
fi

if [ -n "$(getprop ro.boot.product.hardware.sku)" ] && [ -z "$(getprop ro.hw.oemName)" ];then
	setprop ro.hw.oemName "$(getprop ro.boot.product.hardware.sku)"
fi

setprop ctl.stop console
copyprop() {
    p="$(getprop "$2")"
    if [ "$p" ]; then
        resetprop_phh "$1" "$(getprop "$2")"
    fi
}
if [ -f /system/phh/secure ] || [ -f /metadata/phh/secure ];then
    copyprop ro.build.device ro.vendor.build.device
    copyprop ro.system.build.fingerprint ro.vendor.build.fingerprint
    copyprop ro.bootimage.build.fingerprint ro.vendor.build.fingerprint
    copyprop ro.build.fingerprint ro.vendor.build.fingerprint
    copyprop ro.build.device ro.vendor.product.device
    copyprop ro.product.system.device ro.vendor.product.device
    copyprop ro.product.device ro.vendor.product.device
    copyprop ro.product.system.device ro.product.vendor.device
    copyprop ro.product.device ro.product.vendor.device
    copyprop ro.product.system.name ro.vendor.product.name
    copyprop ro.product.name ro.vendor.product.name
    copyprop ro.product.system.name ro.product.vendor.device
    copyprop ro.product.name ro.product.vendor.device
    copyprop ro.system.product.brand ro.vendor.product.brand
    copyprop ro.product.brand ro.vendor.product.brand
    copyprop ro.product.system.model ro.vendor.product.model
    copyprop ro.product.model ro.vendor.product.model
    copyprop ro.product.system.model ro.product.vendor.model
    copyprop ro.product.model ro.product.vendor.model
    copyprop ro.build.product ro.vendor.product.model
    copyprop ro.build.product ro.product.vendor.model
    copyprop ro.system.product.manufacturer ro.vendor.product.manufacturer
    copyprop ro.product.manufacturer ro.vendor.product.manufacturer
    copyprop ro.system.product.manufacturer ro.product.vendor.manufacturer
    copyprop ro.product.manufacturer ro.product.vendor.manufacturer
    (getprop ro.vendor.build.security_patch; getprop ro.keymaster.xxx.security_patch) |sort |tail -n 1 |while read v;do
        [ -n "$v" ] && resetprop_phh ro.build.version.security_patch "$v"
    done

    resetprop_phh ro.build.tags release-keys
    resetprop_phh ro.boot.vbmeta.device_state locked
    resetprop_phh ro.boot.verifiedbootstate green
    resetprop_phh ro.boot.flash.locked 1
    resetprop_phh ro.boot.veritymode enforcing
    resetprop_phh ro.boot.warranty_bit 0
    resetprop_phh ro.warranty_bit 0
    resetprop_phh ro.debuggable 0
    resetprop_phh ro.secure 1
    resetprop_phh ro.build.type user
    resetprop_phh --delete ro.build.selinux

    resetprop_phh ro.adb.secure 1

    # Hide system/xbin/su
    mount /mnt/phh/empty_dir /system/xbin
    mount /mnt/phh/empty_dir /system/app/me.phh.superuser
    mount /system/phh/empty /system/xbin/phh-su
else
    mkdir /mnt/phh/xbin
    chmod 0755 /mnt/phh/xbin
    chcon u:object_r:system_file:s0 /mnt/phh/xbin

    #phh-su will bind over this empty file to make a real su
    touch /mnt/phh/xbin/su
    chcon u:object_r:system_file:s0 /mnt/phh/xbin/su

    mount -o bind /mnt/phh/xbin /system/xbin
fi

resetprop_phh ro.debuggable 0

for abi in "" 64;do
    f=/vendor/lib$abi/libstagefright_foundation.so
    if [ -f "$f" ];then
        for vndk in 26 27 28 29;do
            mount "$f" /system/system_ext/apex/com.android.vndk.v$vndk/lib$abi/libstagefright_foundation.so
        done
    fi
done

setprop ro.product.first_api_level "$vndk"

if getprop ro.boot.boot_devices |grep -v , |grep -qE .;then
    ln -s /dev/block/platform/$(getprop ro.boot.boot_devices) /dev/block/bootdevice
fi

if grep -q -F ro.separate.soft /odm/build.prop;then
	setprop ro.separate.soft "$(sed -nE 's/^ro.separate.soft=(.*)/\1/p' /odm/build.prop)"
fi

if [ "$vndk" -le 28 ] && getprop ro.hardware |grep -q -e mt6761 -e mt6763 -e mt6765 -e mt6785 -e mt8768 -e mt6779 -e mt6771 -e mt8766;then
    setprop debug.stagefright.ccodec 0
fi

if getprop ro.omc.build.version |grep -qE .;then
	for f in $(find /odm -name \*.apk);do
		mount /system/phh/empty $f
	done
fi

resetprop_phh service.adb.root 0

resetprop_phh ro.bluetooth.library_name libbluetooth.so

board="$(getprop ro.board.platform)"

if [ "$board" = mt6768 ]; then
	setprop ro.netflix.bsp_rev MTK6768-19055-1
fi

setprop vendor.display.res_switch_en 1

resetprop_phh ro.control_privapp_permissions log

if [ -f /vendor/etc/init/vendor.ozoaudio.media.c2@1.0-service.rc ];then
    if [ "$vndk" -le 29 ]; then
        mount /system/etc/seccomp_policy/mediacodec.policy /vendor/etc/seccomp_policy/codec2.vendor.base.policy
    fi
fi

if [ "$vndk" -le 27 ];then
    setprop persist.sys.phh.no_present_or_validate true
fi

[ -d /mnt/vendor/persist ] && mount /mnt/vendor/persist /persist

for f in $(find /sys -name fts_gesture_mode);do
    setprop persist.sys.phh.focaltech_node "$f"
done

if [ "$vndk" -le 27 ] && [ -f /vendor/bin/mnld ];then
    setprop persist.sys.phh.sdk_override /vendor/bin/mnld=26
fi

if [ "$vndk" -le 30 ];then
	# On older vendor the default behavior was to disable color management
	# Don't override vendor value, merely add a fallback
	setprop ro.surface_flinger.use_color_management false
fi

if [ "$(stat -c '%U'  /dev/nxp_smartpa_dev)" == "root" ] &&
	[ "$(stat -c '%G' /dev/nxp_smartpa_dev)" == "root" ];then
    chown root:audio /dev/nxp_smartpa_dev
    chmod 0660 /dev/nxp_smartpa_dev
fi

if [ -f /vendor/bin/ccci_rpcd ];then
    setprop debug.phh.props.ccci_rpcd vendor
fi

if getprop ro.vendor.radio.default_network |grep -qE '[0-9]';then
  setprop ro.telephony.default_network $(getprop ro.vendor.radio.default_network)
fi

# Override media volume steps
resetprop_phh ro.config.media_vol_steps 15
resetprop_phh ro.config.media_vol_default 8
resetprop_phh ro.config.vc_call_vol_steps 15
