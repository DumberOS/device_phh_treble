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


