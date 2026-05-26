LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_OPTIONAL_USES_LIBRARIES := \
    androidx.window.extensions \
    androidx.window.sidecar

LOCAL_MODULE := import_contacts
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := import_contacts.apk
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_REPLACE_PREBUILT_APK_INSTALLED := $(LOCAL_PATH)/import_contacts.apk
LOCAL_PRODUCT_MODULE := true
LOCAL_DEX_PREOPT := false

include $(BUILD_PREBUILT)
