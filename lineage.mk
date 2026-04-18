$(call inherit-product, vendor/lineage/config/common_full_phone.mk)
-include vendor/lineage/build/core/config.mk
-include vendor/lineage/build/core/apicheck.mk


PRODUCT_SYSTEM_PROPERTIES += \
    persist.sys.bt.unsupported.commands=182

PRODUCT_SYSTEM_PROPERTIES += \
    persist.dbg.volte_avail_ovr=1
PRODUCT_SYSTEM_PROPERTIES += \
    persist.dbg.wfc_avail_ovr=1
PRODUCT_SYSTEM_PROPERTIES += \
    persist.dbg.allow_ims_off=1
PRODUCT_SYSTEM_PROPERTIES += \
    persist.sys.phh.disable_voice_call_in=true

#PRODUCT_COPY_FILES += \
#      device/phh/treble/permissions/privapp-permissions-com.mediatek.ims.xml:system/etc/permissions/privapp-permissions-com.mediatek.ims.xml

PRODUCT_COPY_FILES += \
      device/phh/treble/permissions/privapp-permissions-eu.dumbdroid.settingspanel.xml:system/etc/permissions/privapp-permissions-eu.dumbdroid.settingspanel.xml


#PRODUCT_COPY_FILES += \
#    device/phh/treble/ims/mediatek-ims-extension-plugin.jar:system/system_ext/framework/mediatek-ims-extension-plugin.jar

PRODUCT_PACKAGES += tt9
PRODUCT_PACKAGES += DumbdroidUpdater DumbdroidPanel DumbdroidDonationLink DumbdroidAdmin
PRODUCT_PACKAGES += TrebleDisableAutoPowerModes
