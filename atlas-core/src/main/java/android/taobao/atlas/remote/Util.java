package android.taobao.atlas.remote;

import java.lang.reflect.Field;

/**
 * Created by guanjie on 2017/12/8.
 */

public class Util {

    public static Field findFieldFromInterface(Object instance, String name) throws NoSuchFieldException {
        Class[] interfaces = instance.getClass().getInterfaces();
        for (Class ifc : interfaces) {
            for (Class<?> clazz = ifc; clazz != null; clazz = clazz.getSuperclass()) {
                try {
                    Field field = clazz.getDeclaredField(name);

                    if (!field.isAccessible()) {
                        field.setAccessible(true);
                    }
                    return field;
                } catch (NoSuchFieldException e) {
                    // ignore and search next
                }
            }
        }

        throw new NoSuchFieldException("Field " + name + " not found in " + instance.getClass());
    }
}
