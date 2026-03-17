LOCAL_PATH := $(call my-dir)


include $(CLEAR_VARS)

LOCAL_OPTIONAL_USES_LIBRARIES := \
    androidx.window.extensions \
    androidx.window.sidecar

LOCAL_MODULE := organic_maps
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := organic_maps.apk
LOCAL_MODULE_CLASS := APPS
LOCAL_CERTIFICATE := platform
LOCAL_MULTILIB := first
LOCAL_PREBUILT_JNI_LIBS_arm64 := @lib/arm64-v8a/liborganicmaps.so

include $(BUILD_PREBUILT)

