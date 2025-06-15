LOCAL_PATH := $(call my-dir)


include $(CLEAR_VARS)

LOCAL_OPTIONAL_USES_LIBRARIES := \
    com.mediatek.ims.oemplugin \
    com.mediatek.ims.plugin

LOCAL_MODULE := ImsApp
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := ims.apk
LOCAL_MODULE_CLASS := APPS
LOCAL_CERTIFICATE := PRESIGNED

include $(BUILD_PREBUILT)
