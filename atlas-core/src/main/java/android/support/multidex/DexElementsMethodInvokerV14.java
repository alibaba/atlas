package android.support.multidex;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * Created by jingchaoqinjc on 18/3/4.
 */

public class DexElementsMethodInvokerV14 implements IDexElementsMethodInvoker {

    final Object dexPathList;
    final File optimizedDirectory;
    final Method makeDexElements;

    public DexElementsMethodInvokerV14(Object dexPathList, File optimizedDirectory, Method makeDexElements) {
        this.dexPathList = dexPathList;
        this.optimizedDirectory = optimizedDirectory;
        this.makeDexElements = makeDexElements;
    }

    @Override
    public Object[] invoke(ArrayList<File> files) throws InvocationTargetException, IllegalAccessException {
        return (Object[]) makeDexElements.invoke(dexPathList, files, optimizedDirectory);
    }
}
