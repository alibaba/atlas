cmake_minimum_required(VERSION 3.3)
project(ShakaAapt)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")

set(AAPTROOT frameworks/base/tools/aapt)
set(aaptMain ${AAPTROOT}/Main.cpp)
set(aaptSources
        ${AAPTROOT}/AaptAssets.cpp
        ${AAPTROOT}/AaptConfig.cpp
        ${AAPTROOT}/AaptUtil.cpp
        ${AAPTROOT}/AaptXml.cpp
        ${AAPTROOT}/ApkBuilder.cpp
        ${AAPTROOT}/Command.cpp
        ${AAPTROOT}/CrunchCache.cpp
        ${AAPTROOT}/FileFinder.cpp
        ${AAPTROOT}/Images.cpp
        ${AAPTROOT}/Package.cpp
        ${AAPTROOT}/pseudolocalize.cpp
        ${AAPTROOT}/Resource.cpp
        ${AAPTROOT}/ResourceFilter.cpp
        ${AAPTROOT}/ResourceIdCache.cpp
        ${AAPTROOT}/ResourceTable.cpp
        ${AAPTROOT}/SourcePos.cpp
        ${AAPTROOT}/StringPool.cpp
        ${AAPTROOT}/WorkQueue.cpp
        ${AAPTROOT}/XMLNode.cpp
        ${AAPTROOT}/ZipEntry.cpp
        ${AAPTROOT}/ZipFile.cpp
)

set(LIB_androidfw_ROOT frameworks/base/libs/androidfw)
set(LIB_androidfw_commonSources
        ${LIB_androidfw_ROOT}/Asset.cpp
        ${LIB_androidfw_ROOT}/AssetDir.cpp
        ${LIB_androidfw_ROOT}/AssetManager.cpp
        ${LIB_androidfw_ROOT}/misc.cpp
        ${LIB_androidfw_ROOT}/ObbFile.cpp
        ${LIB_androidfw_ROOT}/ResourceTypes.cpp
        ${LIB_androidfw_ROOT}/StreamingZipInflater.cpp
        ${LIB_androidfw_ROOT}/TypeWrappers.cpp
        ${LIB_androidfw_ROOT}/ZipFileRO.cpp
        ${LIB_androidfw_ROOT}/ZipUtils.cpp)

set(LIB_LIBUTILS system/core/libutils)
set(LIB_LIBUTILS_commonSources
        ${LIB_LIBUTILS}/BlobCache.cpp
        ${LIB_LIBUTILS}/CallStack.cpp
        ${LIB_LIBUTILS}/FileMap.cpp
        ${LIB_LIBUTILS}/JenkinsHash.cpp
        ${LIB_LIBUTILS}/LinearTransform.cpp
        ${LIB_LIBUTILS}/Log.cpp
        ${LIB_LIBUTILS}/NativeHandle.cpp
        ${LIB_LIBUTILS}/Printer.cpp
        ${LIB_LIBUTILS}/ProcessCallStack.cpp
        ${LIB_LIBUTILS}/PropertyMap.cpp
        ${LIB_LIBUTILS}/RefBase.cpp
        ${LIB_LIBUTILS}/SharedBuffer.cpp
        ${LIB_LIBUTILS}/Static.cpp
        ${LIB_LIBUTILS}/StopWatch.cpp
        ${LIB_LIBUTILS}/String8.cpp
        ${LIB_LIBUTILS}/String16.cpp
        ${LIB_LIBUTILS}/SystemClock.cpp
        ${LIB_LIBUTILS}/Threads.cpp
        ${LIB_LIBUTILS}/Timers.cpp
        ${LIB_LIBUTILS}/Tokenizer.cpp
        ${LIB_LIBUTILS}/Unicode.cpp
        ${LIB_LIBUTILS}/VectorImpl.cpp
        ${LIB_LIBUTILS}/misc.cpp
)

set(LIB_EXPAT external/expat)
set(LIB_EXPAT_commonSources
    ${LIB_EXPAT}/lib/xmlparse.c
    ${LIB_EXPAT}/lib/xmlrole.c
    ${LIB_EXPAT}/lib/xmltok.c
    ${LIB_EXPAT}/lib/xmltok_impl.c
    ${LIB_EXPAT}/lib/xmltok_ns.c
)

set(SOURCE_FILES ${SOURCE_FILES}
        ${aaptMain}
        ${aaptSources}
        ${LIB_androidfw_commonSources}
        ${LIB_LIBUTILS_commonSources}
        ${LIB_EXPAT_commonSources}
)

include_directories(${AAPTROOT})
include_directories(frameworks/base/include
        external/safe-iop/include
        system/core/include
        external/zlib
        external/expat/lib
)

add_definitions(-DXML_TOK_IMPL_C)

add_executable(ShakaAapt ${SOURCE_FILES})