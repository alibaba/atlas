package android.support.multidex;

import java.lang.reflect.InvocationTargetException;

/**
 * Created by jingchaoqinjc on 18/3/4.
 */

public interface IDexElementsMaker {

    Object[] make() throws IllegalAccessException, InvocationTargetException,
            NoSuchMethodException;

}
