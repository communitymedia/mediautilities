LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

# see: http://blog.algolia.com/android-ndk-how-to-reduce-libs-size/
LOCAL_CPPFLAGS += -ffunction-sections -fdata-sections -fvisibility=hidden
LOCAL_CPPFLAGS += -ffunction-sections -fdata-sections -fvisibility=hidden
LOCAL_CFLAGS += -ffunction-sections -fdata-sections
LOCAL_LDFLAGS += -Wl,--gc-sections

TARGET_ARCH := arm
TARGET_PLATFORM := android-8

LOCAL_MODULE := opencore-amrnb-wrapper
LOCAL_SRC_FILES := opencore-amrnb-wrapper.cpp
LOCAL_STATIC_LIBRARIES := opencore-amrnb
include $(BUILD_SHARED_LIBRARY)
$(call import-module,opencore-amr)