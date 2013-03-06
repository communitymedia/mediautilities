#!/bin/sh

# whether to use a shared (yes) or static (no) library
# NOTE: shared library does not actually work; this is more of an experiment than an actual option
USE_SHARED_LIBRARY=no

# check that the variables we need are defined
ERROR_EXIT=no
if [ -z "$PROJECT_PATH" ]; then
echo "\nError: please set the PROJECT_PATH variable to the location of the mediautilities library (e.g: export PROJECT_PATH=/path/to/mediautilities)"
ERROR_EXIT=yes
fi
if [ -z "$NDK_PATH" ]; then
echo "\nError: please set the NDK_PATH variable (e.g: export NDK_PATH=/Applications/Eclipse/sdks/android-ndk-r8d)"
echo "Get the NDK from http://developer.android.com/tools/sdk/ndk/index.html"
ERROR_EXIT=yes
fi
if [ -z "$OPENCORE_AMR_PATH" ]; then
echo "\nError: please set the OPENCORE_AMR_PATH variable (e.g: export OPENCORE_AMR_PATH=/path/to/opencore-amr)"
echo "Get opencore-amr from http://sourceforge.net/projects/opencore-amr/files/opencore-amr/ (tested with v0.1.3)"
ERROR_EXIT=yes
fi
if [ "$ERROR_EXIT" != "no" ]; then
echo
exit -1
fi

# change to verbose mode, and exit on error
set -xe

# set up the commands/directories we need
EXTERNAL_LIBRARY_PATH=$PROJECT_PATH/external
COMPILED_OPENCORE_PATH=$EXTERNAL_LIBRARY_PATH/opencore-amr
TOOLCHAIN=/tmp/opencore-amr-toolchain
PATH=$TOOLCHAIN/bin:$PATH
CC=arm-linux-androideabi-gcc
CXX=arm-linux-androideabi-g++

# create the custom NDK toolchain that we'll use to build the library
$NDK_PATH/build/tools/make-standalone-toolchain.sh --ndk-dir=$NDK_PATH --platform=android-8 --install-dir=$TOOLCHAIN

# build the opencore-amr library, with amr-nb decode capability only (don't care about amr-wb at the moment)
SHARED_LIB=
if [ "$USE_SHARED_LIBRARY" != "yes" ]; then
SHARED_LIB=--disable-shared
fi
cd $OPENCORE_AMR_PATH
./configure --host=arm-linux-androideabi --prefix=$COMPILED_OPENCORE_PATH --enable-amrnb-encoder=no --enable-amrnb-decoder=yes $SHARED_LIB
make clean
make -j3
make install

# create the Android.mk file, using the appropriate version for a static or a shared library
if [ "$USE_SHARED_LIBRARY" != "yes" ]; then
cat > $COMPILED_OPENCORE_PATH/Android.mk << "EOF"
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := opencore-amrnb
LOCAL_SRC_FILES := lib/libopencore-amrnb.a
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include/opencore-amrnb
include $(PREBUILT_STATIC_LIBRARY)
EOF
else
cat > $COMPILED_OPENCORE_PATH/Android.mk << "EOF"
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := opencore-amrnb
LOCAL_SRC_FILES := lib/libopencore-amrnb.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include/opencore-amrnb
include $(PREBUILT_SHARED_LIBRARY)
EOF
fi

# finally, build the wrapper library using the Android NDK
$NDK_PATH/ndk-build NDK_MODULE_PATH=$EXTERNAL_LIBRARY_PATH NDK_PROJECT_PATH=$PROJECT_PATH APP_PLATFORM=android-8
