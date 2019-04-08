package android.taobao.atlas.util;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 创建日期：2019/4/8 on 上午11:38
 * 描述:
 * 作者:zhayu.ll
 */
public class RefectUtils {
    public RefectUtils() {
    }

    public static Method method(Object o, String methodName, Class... clazz) {
        Class c = o.getClass();

        while(c != Object.class) {
            try {
                Method method = c.getDeclaredMethod(methodName, clazz);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException var5) {
                c = c.getSuperclass();
            }
        }

        return null;
    }

    public static Field field(Object o, String fieldName) {
        Class c = o.getClass();

        while(c != Object.class) {
            try {
                Field field = c.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException var4) {
                c = c.getSuperclass();
            }
        }

        return null;
    }

    public static Object invoke(Object o, Method m, Object... objects) {
        m.setAccessible(true);

        try {
            return m.invoke(o, objects);
        } catch (IllegalAccessException var4) {
            var4.printStackTrace();
        } catch (InvocationTargetException var5) {
            var5.printStackTrace();
        }

        return null;
    }

    public static Object fieldGet(Object o, Field field) {
        field.setAccessible(true);

        try {
            return field.get(o);
        } catch (IllegalAccessException var3) {
            var3.printStackTrace();
            return null;
        }
    }
}

