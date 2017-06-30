package com.taobao.android.builder.tools.proguard.dump.utils;

import java.lang.reflect.Field;

public class ReflectUtils {

    public static Object getField(Object obj, String fieldName) throws Exception {
        Field t = obj.getClass().getDeclaredField(fieldName);
        t.setAccessible(true);
        return t.get(obj);
    }
}
