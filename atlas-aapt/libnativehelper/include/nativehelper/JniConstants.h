/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef JNI_CONSTANTS_H_included
#define JNI_CONSTANTS_H_included

#include "JNIHelp.h"

/**
 * A cache to avoid calling FindClass at runtime.
 *
 * Class lookup is relatively expensive, so we do these lookups at startup. This means that code
 * that never uses, say, java.util.zip.Deflater still has to pay for the lookup, but it means that
 * on device the cost is paid during boot and amortized. A central cache also removes the temptation
 * to dynamically call FindClass rather than add a small cache to each file that needs one. Another
 * cost is that each class cached here requires a global reference, though in practice we save
 * enough by not having a global reference for each file that uses a class such as java.lang.String
 * which is used in several files.
 *
 * FindClass is still called in a couple of situations: when throwing exceptions, and in some of
 * the serialization code. The former is clearly not a performance case, and we're currently
 * assuming that neither is the latter.
 *
 * TODO: similar arguments hold for field and method IDs; we should cache them centrally too.
 */
struct JniConstants {
    static void init(JNIEnv* env);

    static jclass bigDecimalClass;
    static jclass booleanClass;
    static jclass byteArrayClass;
    static jclass byteClass;
    static jclass calendarClass;
    static jclass characterClass;
    static jclass charsetICUClass;
    static jclass constructorClass;
    static jclass deflaterClass;
    static jclass doubleClass;
    static jclass errnoExceptionClass;
    static jclass fieldClass;
    static jclass fileDescriptorClass;
    static jclass floatClass;
    static jclass gaiExceptionClass;
    static jclass inet6AddressClass;
    static jclass inetAddressClass;
    static jclass inetAddressHolderClass;
    static jclass inetSocketAddressClass;
    static jclass inetSocketAddressHolderClass;
    static jclass inflaterClass;
    static jclass inputStreamClass;
    static jclass integerClass;
    static jclass localeDataClass;
    static jclass longClass;
    static jclass methodClass;
    static jclass mutableIntClass;
    static jclass mutableLongClass;
    static jclass netlinkSocketAddressClass;
    static jclass objectClass;
    static jclass objectArrayClass;
    static jclass outputStreamClass;
    static jclass packetSocketAddressClass;
    static jclass parsePositionClass;
    static jclass patternSyntaxExceptionClass;
    static jclass referenceClass;
    static jclass shortClass;
    static jclass socketClass;
    static jclass socketImplClass;
    static jclass socketTaggerClass;
    static jclass stringClass;
    static jclass structAddrinfoClass;
    static jclass structFlockClass;
    static jclass structGroupReqClass;
    static jclass structGroupSourceReqClass;
    static jclass structLingerClass;
    static jclass structPasswdClass;
    static jclass structPollfdClass;
    static jclass structStatClass;
    static jclass structStatVfsClass;
    static jclass structTimevalClass;
    static jclass structUcredClass;
    static jclass structUtsnameClass;
    static jclass unixSocketAddressClass;
    static jclass zipEntryClass;
};

#define NATIVE_METHOD(className, functionName, signature) \
    { #functionName, signature, reinterpret_cast<void*>(className ## _ ## functionName) }

#endif  // JNI_CONSTANTS_H_included
