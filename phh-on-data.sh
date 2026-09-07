#!/system/bin/sh

vndk="$(getprop persist.sys.vndk)"
[ -z "$vndk" ] && vndk="$(getprop ro.vndk.version |grep -oE '^[0-9]+')"

if getprop persist.sys.phh.no_vendor_overlay |grep -q true;then
	for part in odm vendor;do
		mount /mnt/phh/empty_dir/ /$part/overlay
	done
fi

if getprop persist.sys.phh.caf.media_profile |grep -q true;then
    setprop media.settings.xml "/vendor/etc/media_profiles_vendor.xml"
fi

# CarrierConfig caches are keyed only by APK versionCode. The stock MTK APK
# keeps versionCode 1, so invalidate its cache when our compatibility asset changes.
ims_cc_generation="20260824-stock2"
ims_cc_applied="$(getprop persist.sys.phh.ims.carrierconfig_cache_generation)"
ims_cc_apk=/system/system_ext/priv-app/CarrierConfig/CarrierConfig.apk
ims_cc_cache_dir=/data/user_de/0/com.android.phone/files
if [ "$(getprop sys.phh.stock_mtk_ims)" = true ] && \
        [ -f "$ims_cc_apk" ] && [ "$ims_cc_applied" != "$ims_cc_generation" ]; then
    if [ -d "$ims_cc_cache_dir" ]; then
        rm -f "$ims_cc_cache_dir"/carrierconfig-com.android.carrierconfig-*.xml
    fi
    setprop persist.sys.phh.ims.carrierconfig_cache_generation "$ims_cc_generation"
fi


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
