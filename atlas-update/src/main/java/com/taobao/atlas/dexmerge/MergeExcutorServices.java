package com.taobao.atlas.dexmerge;

import android.os.RemoteException;
import android.util.Log;
import com.taobao.atlas.dex.Dex;
import com.taobao.atlas.dexmerge.dx.merge.CollisionPolicy;
import com.taobao.atlas.dexmerge.dx.merge.DexMerger;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.Schedulers;

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
        io.reactivex.Observable.just(patchFilePath).map(new Function<String, Map>() {
            @Override
            public Map<String, List<ZipEntry>> apply(String s) throws Exception {
                try {
                    sZipPatch = new ZipFile(s);
                    Enumeration<? extends ZipEntry> zes = sZipPatch.entries();
                    ZipEntry entry = null;
                    String key = null;

                    while (zes.hasMoreElements()) {
                        entry = zes.nextElement();
                        if (entry.getName().equals("libcom_taobao_maindex.so")) {
                            List<ZipEntry> mainDex = new ArrayList<>();
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

        }).subscribe(new io.reactivex.Observer<Map>() {

            @Override
            public void onError(Throwable e) {
                try {
                    mCallback.onMergeAllFinish(false,e.getMessage());
                } catch (RemoteException e1) {
                    e1.printStackTrace();
                }

            }

            @Override
            public void onComplete() {
                Log.e("MergeExcutorServices","merge bundle size:"+bundleEntryGroup.size());

            }

            @Override
            public void onSubscribe(Disposable disposable) {

            }

            @Override
            public void onNext(Map map) {

            }

        });

        MergeTask[]tasks = new MergeTask[list.size()];
        for (int i =0;i < list.size(); i ++){
            MergeTask mergeTask = new MergeTask(new File(list.get(i).originalFile),bundleEntryGroup.get(list.get(i).patchName.replace(".","_")),list.get(i).patchName, new File(list.get(i).mergeFile), b);
            tasks[i] = mergeTask;
        }
        System.setProperty("rx2.computation-threads",String.valueOf(Runtime.getRuntime().availableProcessors()/2));
        RxJavaPlugins.setErrorHandler(new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) throws Exception {
                mCallback.onMergeAllFinish(false,throwable.getMessage());
            }
        });
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        try {
            Observable.fromArray(tasks).flatMap(new Function<MergeTask, ObservableSource<File>>() {
                @Override
                public ObservableSource<File> apply(MergeTask mergeTask) throws Exception {
                    return Observable.just(mergeTask).map(new Function<MergeTask, File>() {
                        @Override
                        public File apply(MergeTask mergeTask) throws Exception {
                            File file = null;
                            try {
                                file = mergeTask.call();
                            }catch (IllegalStateException e){
                                e.printStackTrace();
                            }
                            return file;
                        }
                    }).subscribeOn(Schedulers.computation());
                }
            }).subscribe(new Observer<File>() {

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
                public void onComplete() {
                    try {
                        mCallback.onMergeAllFinish(true,null);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    countDownLatch.countDown();
                }

                @Override
                public void onSubscribe(Disposable disposable) {
                    if (disposable.isDisposed()){
                        onError(new IllegalStateException("connection closed!"));
                    }

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
        }catch (Throwable e){
            try {
                mCallback.onMergeAllFinish(false,e.getMessage());
            } catch (RemoteException e1) {
                e1.printStackTrace();
            }
        }finally {
            Schedulers.shutdown();

            if (sZipPatch != null){
                try {
                    sZipPatch.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
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


    public static void main(String []args) throws InterruptedException {
        final MergeObject mergeTask = new MergeObject(null,null,null);
        final MergeObject mergeTask1 = new MergeObject(null,null,null);

        String[]aa = new String[]{"a","b","c","d","e"};
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        Observable.fromArray(aa).flatMap(new Function<String, ObservableSource<String>>() {
            @Override
            public ObservableSource<String> apply(String s) throws Exception {
                return Observable.just(s).map(new Function<String, String>() {
                    @Override
                    public String apply(String s) throws Exception {
                        return s+s;
                    }
                }).subscribeOn(Schedulers.computation());
            }
        }).observeOn(Schedulers.newThread()).subscribe(new Observer<String>() {
            @Override
            public void onSubscribe(Disposable d) {

            }

            @Override
            public void onNext(String value) {
                System.out.println(value);
                System.out.println("xxxx");

            }

            @Override
            public void onError(Throwable e) {
                System.out.println("onError");


            }

            @Override
            public void onComplete() {
                System.out.println("onComplete");
                countDownLatch.countDown();

            }
        });
        countDownLatch.await();
//       Observable.just(1,2,3,4).doOnSubscribe(new Consumer<Disposable>() {
//           @Override
//           public void accept(Disposable disposable) throws Exception {
//               System.out.println("doOnSubscribe0 +"+Thread.currentThread().getName());
//
//           }
//       }).map(new Function<Integer, Integer>() {
//
//           @Override
//           public Integer apply(Integer integer) throws Exception {
//               System.out.println("map0 +"+Thread.currentThread().getName());
//
//               return integer;
//           }
//       }).subscribeOn(Schedulers.newThread()).map(new Function<Integer, Integer>() {
//           @Override
//           public Integer apply(Integer integer) throws Exception {
//               System.out.println("map +"+Thread.currentThread().getName());
//               return integer+1;
//           }
//       }).subscribeOn(Schedulers.computation()).map(new Function<Integer, Integer>() {
//           @Override
//           public Integer apply(Integer integer) throws Exception {
//               System.out.println("map5 +"+Thread.currentThread().getName());
//
//               return integer;
//           }
//       }).doOnSubscribe(new Consumer<Disposable>() {
//           @Override
//           public void accept(Disposable disposable) throws Exception {
//               System.out.println("doOnSubscribe +"+Thread.currentThread().getName());
//
//
//           }
//       }).subscribeOn(Schedulers.io()).observeOn(Schedulers.single()).doOnSubscribe(new Consumer<Disposable>() {
//           @Override
//           public void accept(Disposable disposable) throws Exception {
//               System.out.println("doOnSubscribe1 +"+Thread.currentThread().getName());
//
//           }
//       }).subscribeOn(Schedulers.computation()).map(new Function<Integer, Integer>() {
//           @Override
//           public Integer apply(Integer integer) throws Exception {
//               System.out.println("map1 +"+Thread.currentThread().getName());
//
//               return integer+1;
//           }
//       }).observeOn(Schedulers.computation()).map(new Function<Integer, Integer>() {
//           @Override
//           public Integer apply(Integer integer) throws Exception {
//               System.out.println("map3 +"+Thread.currentThread().getName());
//
//               return integer;
//           }
//       }).blockingSubscribe(new Consumer<Integer>() {
//           @Override
//           public void accept(Integer integer) throws Exception {
//               System.out.println("Consumer +"+integer+Thread.currentThread().getName());
//
//           }
//       });

//        Set<String>set = new HashSet<>();
//        set.add("a");
//        set.add("b");
//        set.add("f");
//        set.add("c");
//        System.out.println(set.toArray()[0]);
//       Thread.sleep(5000);
    }


}
