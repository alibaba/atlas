package com.taobao.atlas.dexmerge;

import android.os.RemoteException;
import com.taobao.atlas.dex.Dex;
import com.taobao.atlas.dexmerge.dx.merge.CollisionPolicy;
import com.taobao.atlas.dexmerge.dx.merge.DexMerger;
import rx.Observable;
import rx.Observer;
import rx.Subscriber;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Created by lilong on 16/7/5.
 */
public class MergeExcutorServices {

    private IDexMergeCallback mCallback = null;
    public static ZipFile sZipPatch;
    private static final String TAG = "mergeTask";
    HashMap<String, List<ZipEntry>> bundleEntryGroup = new HashMap<String, List<ZipEntry>>();

    public static OS os = OS.mac;


    public MergeExcutorServices(IDexMergeCallback mCallback) throws RemoteException {
        this.mCallback = mCallback;

    }

    public void excute(String patchFilePath, final List<MergeObject> list, final boolean b) throws ExecutionException, InterruptedException {
        Observable.just(patchFilePath).map(new Func1<String, Map>() {
            @Override
            public Map call(String s) {
                try {
                    sZipPatch = new ZipFile(s);
                    Enumeration<? extends ZipEntry> zes = sZipPatch.entries();
                    ZipEntry entry = null;
                    String key = null;

                    while (zes.hasMoreElements()) {
                        entry = zes.nextElement();
                        if (entry.getName().equals("libcom_taobao_maindex.so")) {
                            List<ZipEntry> mainDex = new ArrayList<ZipEntry>();
                            mainDex.add(entry);
                            bundleEntryGroup.put("com_taobao_maindex", mainDex);
                        } else if (entry.getName().startsWith("lib")) {
                            if (entry.getName().indexOf("/") != -1) {
                                key = entry.getName().substring(3, entry.getName().indexOf("/"));
                                os = OS.mac;
                            } else if (entry.getName().indexOf("\\") != -1) {
                                key = entry.getName().substring(3, entry.getName().indexOf("\\"));
                                os = OS.windows;

                            }
                            List<ZipEntry> bundleEntry = null;
                            if ((bundleEntry = bundleEntryGroup.get(key)) == null) {
                                bundleEntry = new ArrayList<ZipEntry>();
                                bundleEntryGroup.put(key, bundleEntry);
                                bundleEntry.add(entry);
                            } else {
                                bundleEntryGroup.get(key).add(entry);
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return bundleEntryGroup;
            }
        }).subscribe(new Observer<Map>() {
            @Override
            public void onCompleted() {
                if (bundleEntryGroup.size() != list.size()){
                    onError(new RuntimeException("parse bundleEntryGroup failed!"));
                }
            }

            @Override
            public void onError(Throwable e) {
                try {
                    mCallback.onMergeAllFinish(false,e.getMessage());
                } catch (RemoteException e1) {
                    e1.printStackTrace();
                }

            }

            @Override
            public void onNext(Map map) {

            }

        });

        List<MergeTask>tasks = new ArrayList<>();
        for (MergeObject mo:list){
            MergeTask mergeTask = new MergeTask(new File(mo.originalFile),bundleEntryGroup.get(mo.patchName.replace(".","_")),mo.patchName, new File(mo.mergeFile), b);
            tasks.add(mergeTask);
        }
        System.setProperty("rx.scheduler.max-computation-threads",String.valueOf(Runtime.getRuntime().availableProcessors()/2));
        final CountDownLatch countDownLatch = new CountDownLatch(1);
            Observable.from(tasks).flatMap(new Func1<MergeTask, Observable<File>>() {
                @Override
                public Observable<File> call(final MergeTask mergeTask) {
                    return  Observable.just(mergeTask).map(new Func1<MergeTask, File>() {
                        @Override
                        public File call(MergeTask mergeTask) {
                            try {
                                return mergeTask.call();
                            } catch (IOException e) {
                                e.printStackTrace();
                            } catch (MergeException e) {
                                e.printStackTrace();
                            }
                            return null;
                        }
                    }).subscribeOn(Schedulers.computation());
                }
            }).subscribe(new Subscriber<File>() {
                @Override
                public void onCompleted() {
                    try {
                        mCallback.onMergeAllFinish(true,null);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    countDownLatch.countDown();
                }

                @Override
                public void onError(Throwable e) {
                    try {
                        mCallback.onMergeAllFinish(false,e.getMessage());
                    } catch (RemoteException e1) {
                        e1.printStackTrace();
                    }
                    countDownLatch.countDown();
                }

                @Override
                public void onNext(File file) {
                    if (file!= null && file.exists()){
                        try {
                            mCallback.onMergeFinish(file.getAbsolutePath(),true,null);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }

                }
            });
            countDownLatch.await();
            if (sZipPatch != null){
                try {
                    sZipPatch.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }




    public class MergeTask implements Callable<File> {


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
        public File call() throws IOException, MergeException {
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
            return outFile;
        }
    }


    private void dexMergeInternal(InputStream[] inputStreams, OutputStream newDexStream, String bundleName) throws IOException {
        FileOutputStream fileOutputStream = null;
        if (inputStreams[0] == null || inputStreams[1] == null) {
            try {
                mCallback.onMergeFinish(bundleName,false, "argNUll");
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            return ;
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


//    public static void main(String []args) throws InterruptedException {
//        String []aa = new String[]{"1","2","3","a"};
//        Observable observable1 = Observable.just("1");
//        Observable observable2 = Observable.just("2");
//        Observable observable5 = Observable.just("5");
//        Observable observable6 = Observable.just("6");
//        Observable observable7 = Observable.just("7");
//        Observable observable3 = Observable.just("3");
//        Observable observable4 = Observable.just("4");
//        final ExecutorService es = Executors.newFixedThreadPool(5);
//
//        final CountDownLatch countDownLatch = new CountDownLatch(1);
//        Observable.concatEager(observable1,observable2,observable5,observable6,observable7,observable3,observable4).flatMap(new Func1<String,Observable<Integer>>() {
//            @Override
//            public Observable<Integer> call(final String integer) {
//                return Observable.just(integer).map(new Func1<String, Integer>() {
//                    @Override
//                    public Integer call(String s) {
//                        return Integer.valueOf(s);
//                    }
//                }).subscribeOn(Schedulers.from(es));
//            }
//
//        }).observeOn(Schedulers.immediate()).subscribe(new Subscriber<Integer>() {
//            @Override
//            public void onCompleted() {
//                countDownLatch.countDown();
//            }
//
//            @Override
//            public void onError(Throwable e) {
//                countDownLatch.countDown();
//
//            }
//
//            @Override
//            public void onNext(Integer integer) {
//                System.out.println(integer+Thread.currentThread().getName()+new Date(System.currentTimeMillis()));
//            }
//
//        });
//        countDownLatch.await();
//        System.out.println("4"+Thread.currentThread().getName());
//        try {
//            Thread.sleep(2000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//    }

}
