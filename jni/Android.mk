LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := libbatmanclient
LOCAL_SRC_FILES := batmanclient/batmanclient.c

include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE    := libnativetask
LOCAL_SRC_FILES := libnativetask/org_servalproject_system_NativeTask.c
LOCAL_LDLIBS := -lc

include $(BUILD_SHARED_LIBRARY)

