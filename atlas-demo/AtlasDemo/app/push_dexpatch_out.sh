#!/bin/sh

adb shell rm -rf /sdcard/Android/data/com.taobao.demo/cache
adb shell mkdir /sdcard/Android/data/com.taobao.demo/cache
adb push build/outputs/tpatch-debug/dexpatch-1.0.0.json /sdcard/Android/data/com.taobao.demo/cache/
adb push build/outputs/tpatch-debug/1.0.0@1.0.0.tpatch /sdcard/Android/data/com.taobao.demo/cache/