LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := privapp-permissions-com.dumbermini.apps.xml
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_PATH := $(TARGET_OUT_PRODUCT_ETC)/permissions
LOCAL_SRC_FILES := $(LOCAL_MODULE)
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)
LOCAL_MODULE := initial-package-stopped-states-com.dumbermini.apps.xml
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_PATH := $(TARGET_OUT_PRODUCT_ETC)/sysconfig
LOCAL_SRC_FILES := $(LOCAL_MODULE)
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)

LOCAL_OPTIONAL_USES_LIBRARIES := \
    androidx.window.extensions \
    androidx.window.sidecar

LOCAL_MODULE := more_apps
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := more_apps.apk
LOCAL_MODULE_CLASS := APPS
LOCAL_PRIVILEGED_MODULE := true
LOCAL_CERTIFICATE := platform
LOCAL_MULTILIB := first
LOCAL_PREBUILT_JNI_LIBS_arm64 := @lib/arm64-v8a/libandroidx.graphics.path.so
LOCAL_REQUIRED_MODULES := \
    privapp-permissions-com.dumbermini.apps.xml \
    initial-package-stopped-states-com.dumbermini.apps.xml
LOCAL_PRODUCT_MODULE := true

include $(BUILD_PREBUILT)
