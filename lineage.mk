$(call inherit-product, vendor/lineage/config/common_full_phone.mk)
-include vendor/lineage/build/core/config.mk
-include vendor/lineage/build/core/apicheck.mk


PRODUCT_SYSTEM_PROPERTIES += \
    persist.sys.bt.unsupported.commands=182

