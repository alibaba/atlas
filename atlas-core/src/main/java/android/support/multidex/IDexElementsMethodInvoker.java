package android.support.multidex;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

/**
 * Created by jingchaoqinjc on 18/3/4.
 */

public interface IDexElementsMethodInvoker {

    public Object[] invoke(ArrayList<File> files) throws InvocationTargetException, IllegalAccessException;

}
