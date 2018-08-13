package android.support.multidex;

import android.util.Log;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

/**
 * Created by jingchaoqinjc on 18/3/4.
 */

public class DexElementsMaker implements IDexElementsMaker {

    private static final String TAG = "DexElementsMaker";

    final ArrayList<File> files;
    final IDexElementsMethodInvoker invoker;

    DexElementsMaker(ArrayList<File> files, IDexElementsMethodInvoker invoker) {
        this.files = files;
        this.invoker = invoker;
    }

    @Override
    public Object[] make() throws IllegalAccessException, InvocationTargetException,
            NoSuchMethodException {

        if (files.size() <= 1) {
            return invoker.invoke(files);
        }

        //通过算法，将文件分解成大小相似的文件集，使得每个文件集在加载的时候时间相似
        ArrayList<ArrayList<File>> filesList = makeFilesList();

        int size = filesList.size();
        FutureTask<Object[]>[] futureTasks = new FutureTask[size];
        for (int i = 0; i < size; i++) {
            futureTasks[i] = new FutureTask<Object[]>(new DexElementsCallable(i, filesList.get(i), invoker));
        }

        //其他任务在子线程里完成，加速加载
        for (int i = 1; i < size; i++) {
            new Thread(futureTasks[i]).start();
        }
        //一个任务在主线程完成，充分利用主线程资源
        futureTasks[0].run();

        ArrayList<Object[]> objectsList = new ArrayList<Object[]>();
        int objectsTotalLength = 0;

        try {
            for (int i = 0; i < size; i++) {
                Object[] objects = futureTasks[i].get();
                if (objects == null) throw new RuntimeException("Illegal Action");

                objectsTotalLength += objects.length;
                objectsList.add(objects);
            }

            Object[] objects = new Object[objectsTotalLength];

            int offset = 0;
            for (Object[] subObjects : objectsList) {
                if (subObjects != null) {
                    System.arraycopy(subObjects, 0, objects, offset, subObjects.length);
                    offset += subObjects.length;
                }
            }

            return objects;
        } catch (Exception e) {

        }

        return invoker.invoke(files);
    }

    private long getMaxFileLength() {
        long max = 0;
        for (File file : files) {
            if (file != null) {
                long length = file.length();
                max = length > max ? length : max;
            }
        }
        return max;
    }

    private ArrayList<ArrayList<File>> makeFilesList() {
        ArrayList<ArrayList<File>> filesList = new ArrayList<ArrayList<File>>();

        long maxFileLength = getMaxFileLength();
        long subTotalLength = 0;
        ArrayList<File> subFiles = new ArrayList<File>();
        filesList.add(subFiles);

        for (File file : files) {
            if (file != null) {
                long subLength = file.length();
                if (subLength + subTotalLength > maxFileLength) {
                    subTotalLength = 0;
                    subFiles = new ArrayList<File>();
                    filesList.add(subFiles);
                }

                subFiles.add(file);
                subTotalLength += subLength;
            }
        }

        return filesList;
    }

    private static class DexElementsCallable implements Callable<Object[]> {
        final int id;
        final ArrayList<File> files;
        final IDexElementsMethodInvoker invoker;

        private DexElementsCallable(int id, ArrayList<File> files, IDexElementsMethodInvoker invoker) {
            this.id = id;
            this.files = files;
            this.invoker = invoker;
        }

        @Override
        public Object[] call() throws Exception {
            try {
                long startTime = System.currentTimeMillis();
                Object[] objects = invoker.invoke(files);
                long endTime = System.currentTimeMillis();
                Log.i(TAG, "cost " + id + " time:" + (endTime - startTime));
                return objects;
            } catch (Exception e) {

            }
            return null;
        }
    }

}
