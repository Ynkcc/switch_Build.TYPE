LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE    := switch_build_type
LOCAL_SRC_FILES := main.cpp
LOCAL_CFLAGS    += -Wall -Wextra -Os -fvisibility=hidden -fvisibility-inlines-hidden
LOCAL_CPPFLAGS  += -std=c++17 -fno-exceptions -fno-rtti
LOCAL_LDLIBS    += -llog
LOCAL_LDFLAGS   += -Wl,--exclude-libs,ALL -Wl,--as-needed
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE    := switch
LOCAL_SRC_FILES := switch.c
LOCAL_CFLAGS    += -Wall -Wextra -Os
include $(BUILD_EXECUTABLE)
