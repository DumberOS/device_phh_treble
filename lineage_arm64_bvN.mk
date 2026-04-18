TARGET_GAPPS_ARCH := arm64
include build/make/target/product/aosp_arm64.mk
$(call inherit-product, device/phh/treble/base.mk)


$(call inherit-product, device/phh/treble/lineage.mk)

PRODUCT_NAME := arm64_bvN
PRODUCT_DEVICE := tdgsi_arm64_ab
PRODUCT_BRAND := google
PRODUCT_SYSTEM_BRAND := google
PRODUCT_MODEL := DumberOS
PRODUCT_MANUFACTURER := dumber
PRODUCT_SYSTEM_MANUFACTURER := dumber


# Overwrite the inherited "emulator" characteristics
PRODUCT_CHARACTERISTICS := device

WITH_MICROG := true

PRODUCT_PACKAGES += \
    android.hardware.lights-service.example

PRODUCT_REMOVE_PACKAGES := \
AudioFX \
BasicDreams \
EasterEgg \
Etar \
ExactCalculator \
Recorder \
talkback \
Velvet \
Eleven \
NotoColorEmojiLegacy.ttf \
MtkInCallService \
#Seedvault \

PRODUCT_PACKAGES += \
    NotoSansCJK-Regular.ttc

PRODUCT_PACKAGES += package_filter

PRODUCT_PACKAGES += GmsCore GsfProxy FakeStore more_apps

#PRODUCT_PACKAGES += ImsApp

PRODUCT_SYSTEM_PROPERTIES += \
    ro.dumbdroid.branch=vanilla
