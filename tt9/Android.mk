LOCAL_PATH := $(call my-dir)


include $(CLEAR_VARS)

LOCAL_OPTIONAL_USES_LIBRARIES := \
    androidx.window.extensions \
    androidx.window.sidecar

LOCAL_MODULE := tt9
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := tt9.apk
LOCAL_MODULE_CLASS := APPS
LOCAL_CERTIFICATE := PRESIGNED

include $(BUILD_PREBUILT)

