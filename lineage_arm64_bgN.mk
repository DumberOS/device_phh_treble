TARGET_GAPPS_ARCH := arm64
include build/make/target/product/aosp_arm64.mk
#include device/phh/treble/remove_packages/Android.mk
include $(call all-makefiles-under, device/phh/treble/remove_packages)
$(call inherit-product, device/phh/treble/base.mk)

$(call inherit-product, vendor/gapps/arm64/arm64-vendor.mk)
$(call inherit-product, device/phh/treble/lineage.mk)
$(call inherit-product-if-exists, device/qin/f21pro/f21pro_gsi_overlay.mk)
$(call inherit-product, \
    $(SRC_TARGET_DIR)/product/virtual_ab_ota.mk)

PRODUCT_NAME := arm64_bgN
PRODUCT_DEVICE := tdgsi_arm64_ab
PRODUCT_BRAND := google
PRODUCT_SYSTEM_BRAND := google
PRODUCT_MODEL := DumberOS
PRODUCT_MANUFACTURER := dumber
PRODUCT_SYSTEM_MANUFACTURER := dumber


# Overwrite the inherited "emulator" characteristics
PRODUCT_CHARACTERISTICS := device

PRODUCT_PACKAGES += \
	android.hardware.lights-service.example \

PRODUCT_REMOVE_PACKAGES := \
AudioFX \
BasicDreams \
EasterEgg \
Etar \
ExactCalculator \
Recorder \
Velvet \
Eleven \
MtkInCallService \
SetupWizard \
#TrebleApp \
#Seedvault \
#LineageSetupWizard \
#NotoColorEmojiLegacy.ttf \
#NotoSansCJK-Regular.ttc \
#NotoSerifCJK-Regular.ttc \
#SpeechServicesByGoogle \
#talkback \

PRODUCT_PACKAGES += package_filter
PRODUCT_PACKAGES += more_apps

#PRODUCT_PACKAGES += ImsApp

PRODUCT_SYSTEM_PROPERTIES += \
    ro.dumbdroid.branch=gapps
