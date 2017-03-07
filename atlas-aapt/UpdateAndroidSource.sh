#! /bin/bash
SRCROOT=/Develop/android/source/android-last
DESROOT=.

array=( \
	bionic/libc/include \
	external/compiler-rt \
	external/expat \
	external/libcxx \
	external/libcxxabi \
	external/libpng \
	external/safe-iop \
	external/zlib \
	frameworks/base/include \
	frameworks/base/libs/androidfw \
	frameworks/base/tools/aapt \
	frameworks/native/include \
	libnativehelper \
	prebuilts/clang/darwin-x86 \
	prebuilts/clang/host \
	prebuilts/gcc \
	sdk/build \
	system/core/base \
	system/core/include \
	system/core/libcutils \
	system/core/liblog \
	system/core/libutils \
	system/core/libziparchive \
	)

for var in ${array[@]};do
rsync -aP --delete $SRCROOT/$var/ $DESROOT/$var/ \
  --exclude .git \
  --exclude test \
  --exclude tests \
  --exclude testdata \
  --exclude examples \
  --exclude doc \
  --exclude www \
  --exclude unittests \
  --exclude common \
  --exclude linux-x86/mips \
  --exclude linux-x86/arm \
  --exclude linux-x86/aarch64 \
  --exclude linux-x86/x86 \
  --exclude linux-x86/host/x86_64-linux-glibc2.11-4.8 \
  --exclude linux-x86/host/x86_64-w64-mingw32-4.8 \
  --exclude darwin-x86/mips \
  --exclude darwin-x86/arm \
  --exclude darwin-x86/aarch64 \
  --exclude darwin-x86/x86
done




