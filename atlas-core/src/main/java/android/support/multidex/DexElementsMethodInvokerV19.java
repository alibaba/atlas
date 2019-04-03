package android.support.multidex;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * Created by jingchaoqinjc on 18/3/4.
 */

public class DexElementsMethodInvokerV19 implements IDexElementsMethodInvoker {

    final Object dexPathList;
    final File optimizedDirectory;
    final ArrayList<IOException> suppressedExceptions;
    final Method makeDexElements;

    public DexElementsMethodInvokerV19(Object dexPathList, File optimizedDirectory, ArrayList<IOException> suppressedExceptions, Method makeDexElements) {
        this.dexPathList = dexPathList;
        this.optimizedDirectory = optimizedDirectory;
        this.suppressedExceptions = suppressedExceptions;
        this.makeDexElements = makeDexElements;
    }

    @Override
    public Object[] invoke(ArrayList<File> files) throws InvocationTargetException, IllegalAccessException {
        return (Object[]) makeDexElements.invoke(dexPathList, files, optimizedDirectory, suppressedExceptions);
    }
}
