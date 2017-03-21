package com.taobao.atlas.dexmerge;

import android.os.RemoteException;
import android.util.Log;
import com.taobao.atlas.dex.Dex;
import com.taobao.atlas.dexmerge.dx.merge.CollisionPolicy;
import com.taobao.atlas.dexmerge.dx.merge.DexMerger;

import java.io.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Created by lilong on 16/7/5.
 */
public class MergeExcutorServices {

    private IDexMergeCallback mCallback = null;
    ExecutorService es = null;
    public static ZipFile sZipPatch;
    public static AtomicInteger successCount = new AtomicInteger();
    public  static AtomicInteger needMergeCount = new AtomicInteger();
    List<Future<Boolean>> fList = new ArrayList<Future<Boolean>>();
    private static final String TAG = "mergeTask";

    public static OS os = OS.mac;


    public MergeExcutorServices(IDexMergeCallback mCallback) {
        this.mCallback = mCallback;
        es = Executors.newFixedThreadPool(3);

    }

    public void excute(String patchFilePath,List<MergeObject> list, boolean b) throws ExecutionException, InterruptedException {
        if (!b){
            needMergeCount.set(list.size());
        }
        try {
            sZipPatch = new ZipFile(patchFilePath);
            Enumeration<? extends ZipEntry> zes = sZipPatch.entries();
            ZipEntry entry = null;
            String key = null;

            HashMap<String,List<ZipEntry>> bundleEntryGroup= new HashMap<String,List<ZipEntry>>();
            while (zes.hasMoreElements()) {
                entry = zes.nextElement();
                if (entry.getName().equals("libcom_taobao_maindex.so")){
                    List<ZipEntry>mainDex = new ArrayList<ZipEntry>();
                    mainDex.add(entry);
                    bundleEntryGroup.put("com_taobao_maindex",mainDex);
                }else if(entry.getName().startsWith("lib")){
                    if (entry.getName().indexOf("/")!= -1){
                        key = entry.getName().substring(3,entry.getName().indexOf("/"));
                        os = OS.mac;
                    }else if (entry.getName().indexOf("\\")!= -1){
                        key = entry.getName().substring(3,entry.getName().indexOf("\\"));
                        os = OS.windows;

                    }
                    List<ZipEntry> bundleEntry = null;
                    if((bundleEntry=bundleEntryGroup.get(key)) == null){
                        bundleEntry = new ArrayList<ZipEntry>();
                        bundleEntryGroup.put(key,bundleEntry);
                        bundleEntry.add(entry);
                    }else {
                        bundleEntryGroup.get(key).add(entry);
                    }
                }
            }

            for (MergeObject mergeObject : list) {
                MergeTask mergeTask = new MergeTask(new File(mergeObject.originalFile),bundleEntryGroup.get(mergeObject.patchName.replace(".","_")),mergeObject.patchName, new File(mergeObject.mergeFile), b);
                Future future = es.submit(mergeTask);
                fList.add(future);
            }
            waitTaskCompleted();

        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if (sZipPatch != null){
                try {
                    sZipPatch.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        es.shutdown();
        try {
            if (successCount.get() == needMergeCount.get()) {
                Log.e(TAG,"merge all finished");
                mCallback.onMergeAllFinish(true, null);
                successCount.set(0);
                needMergeCount.set(0);
            } else {
                mCallback.onMergeAllFinish(false, "merge failed!");
                Log.e(TAG,"merge all finish but failed!");
                successCount.set(0);
                needMergeCount.set(0);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }


    public boolean waitTaskCompleted() throws InterruptedException, ExecutionException {
        boolean flag = true;
        if (null == fList) {
            return true;
        } else {
            for (Future<Boolean> future : fList) {
                if (!future.get()) {
                    flag = false;
                }
            }
        }
        return flag;
    }


    public class MergeTask implements Callable {


        public MergeTask(File sourceFile, List<ZipEntry> patchEntries,String patchName, File outFile, boolean diff) {
            this.sourceFile = sourceFile;
            this.patchName = patchName;
            this.patchEntries = patchEntries;
            this.outFile = outFile;
            this.diffDex = diff;
        }

        private File sourceFile;
        private String patchName;
        private File outFile;
        private List<ZipEntry> patchEntries;
        private boolean diffDex;


        @Override
        public Boolean call() throws Exception {
            MergeTool.mergePrepare(sourceFile, patchEntries,patchName, outFile, diffDex, new PrepareCallBack() {

                @Override
                public void prepareMerge(String patchBundleName,ZipFile sourceFile, ZipEntry patchDex, OutputStream newDexStream) throws IOException {
                    boolean classMerge = false;
                    InputStream[] inputStreams = new InputStream[2];
                    try {
                        inputStreams[0] = sourceFile.getInputStream(new ZipEntry("classes.dex"));
                        inputStreams[1] = MergeExcutorServices.sZipPatch.getInputStream(patchDex);
//                        if (patchDex.getName().endsWith(".tdex")){
//                            classMerge = true;
//                        }
                        dexMergeInternal(inputStreams, newDexStream,patchBundleName);

                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        for (InputStream inputStream:inputStreams){
                            if (inputStream != null){
                                inputStream.close();
                            }

                        }

                    }

                }

            });
            return true;
        }
    }


    private void dexMergeInternal(InputStream[] inputStreams, OutputStream newDexStream, String bundleName) {
        FileOutputStream fileOutputStream = null;
        if (inputStreams[0] == null || inputStreams[1] == null) {
            try {
                mCallback.onMergeFinish(bundleName,false, "argNUll");
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            return;
        }

        try {

            //方式一
//            DexPatchApplier dexPatchApplier = new DexPatchApplier(inputStreams[0],inputStreams[1]);
//            dexPatchApplier.executeAndSaveTo(newDexStream);
            //方式二
            Dex dex1 = new Dex(inputStreams[1]);
            Dex dex2 = new Dex(inputStreams[0]);
            List<Dex> dexs = new ArrayList<Dex>();
            dexs.add(dex1);
            dexs.add(dex2);
            DexMerger mDexMerge = new DexMerger(new Dex[]{dex1, dex2}, CollisionPolicy.KEEP_FIRST);
            mDexMerge.setCompactWasteThreshold(1);
            Dex outDex = mDexMerge.merge();
            outDex.writeTo(newDexStream);
            newDexStream.flush();
            mCallback.onMergeFinish(bundleName, true, "Success");
            successCount.incrementAndGet();
        } catch (Throwable e) {
            e.printStackTrace();
            try {
                mCallback.onMergeFinish(bundleName, false, "IOException 2");
            } catch (RemoteException e1) {
                e1.printStackTrace();
            }
        }finally {
            if (fileOutputStream != null){
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }
    }

    public interface PrepareCallBack {

        void prepareMerge(String patchBundleName,ZipFile sourceFile, ZipEntry patchDex, OutputStream newDexStream) throws IOException;

    }
    enum OS{
        mac,windows,linux
    }

}
