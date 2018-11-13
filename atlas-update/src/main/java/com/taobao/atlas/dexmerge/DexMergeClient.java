package com.taobao.atlas.dexmerge;

import android.content.*;
import android.os.IBinder;
import android.os.RemoteException;
import android.taobao.atlas.runtime.RuntimeVariables;
import android.util.Log;

import java.util.List;

/**
 * Created by xieguo.xg on 1/21/16.
 */
public class DexMergeClient {

    public static final int REMOTE_TIMEOUT = 60 * 1000;

    private Object lock = new Object();

//    private Object mergeLock = new Object();

    private boolean isFinished;

    private boolean isBinded;

    private long mStartTime;

    private final static String TAG = "DexMergeClient";

    private boolean isTimeout = true; // Whether unlock caused by timeout

    private boolean isBinderDied = false;

    private final static int numBinderDieTries = 3;
    IDexMergeBinder dexMergeBinder;

    private IBinder.DeathRecipient mDeathRecipient = new MyServiceDeathHandler();

    private MergeCallback mergeCallBack;

    public DexMergeClient(MergeCallback mergeCallBack) {
        this.mergeCallBack = mergeCallBack;
        IntentFilter intentFilter = new IntentFilter("com.taobao.atlas.intent.PATCH_VERSION");
        RuntimeVariables.androidApplication.registerReceiver(new PatchVersionReceiver(),intentFilter);
    }

    public boolean prepare() {
        Intent intent = new Intent();
        intent.setClassName(RuntimeVariables.androidApplication,
                "com.taobao.atlas.dexmerge.DexMergeService");

        mStartTime = System.currentTimeMillis();

        /**
         * try to Bind dexmerge service
         */
        if (!RuntimeVariables.androidApplication.bindService(intent,
                conn,
                Context.BIND_AUTO_CREATE |
                        Context.BIND_IMPORTANT)) {
            return false;
        }

        /**
         * Block wait service bind
         */
        if (!isBinded) {
            try {
                synchronized (lock) {
                    // 10 minutes timeout
                    lock.wait(REMOTE_TIMEOUT);
                }
            } catch (InterruptedException e) {
            }
        }


        if (!isBinded) {

            /**
             * unbind once timeout
             */
//            AppMonitor.Counter.commit("dexMerge", "RemoteException", "bind timeout " +
//                    VERSION.SDK_INT +
//                    " " +
//                    (mPowerManager.isScreenOn() ? 1 : 0), 1);
            RuntimeVariables.androidApplication.unbindService(conn);
        }
        return isBinded;
    }

    public void unPrepare() {
        RuntimeVariables.androidApplication.unbindService(conn);
    }


    public boolean dexMerge(String patchFilePath, List toMergeList, boolean diffBundleDex) {
        if (toMergeList.size() == 0) {
            return true;
        }
        mStartTime = System.currentTimeMillis();

        if (!dexMergeInternal(patchFilePath,toMergeList, diffBundleDex) && isBinderDied) {

            /**
             * handle binder died case
             */
            for (int i = 0; i < numBinderDieTries && isBinderDied; i++) {
                isBinderDied = false;
                if (prepare() == false) {
                    return isFinished;
                }

                if (dexMergeInternal(patchFilePath,toMergeList, diffBundleDex)) {

                    break;
                }
            }
        }

        return isFinished;

    }

    private boolean dexMergeInternal(String patchFilePath,List toMergeList, boolean diffBundleDex) {
        isFinished = false;

        try {
            dexMergeBinder.dexMerge(patchFilePath,toMergeList, diffBundleDex);
//            synchronized (mergeLock){
//                try {
//                    mergeLock.wait();
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }


        return isFinished;
    }


    ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            //返回一个IDexMergeBinder对象
            Log.d(TAG, "Get binder" + (System.currentTimeMillis() - mStartTime) + " ms");
            dexMergeBinder = IDexMergeBinder.Stub.asInterface(service);

            isBinded = true;

            //注册回调接口
            try {
                dexMergeBinder.registerListener(new IDexMergeCallback.Stub() {

                    @Override
                    public void onMergeFinish(String filePath, boolean result, String reason) {
                        if (!result) {
                            if (mergeCallBack != null) {
                                mergeCallBack.onMergeResult(false, filePath);
                            }
                            Log.e(TAG, "merge Failed:" + filePath);

                        } else if (mergeCallBack != null) {

                            mergeCallBack.onMergeResult(true, filePath);
                        }

                    }

                    @Override
                    public void onMergeAllFinish(boolean result, String reason) {
                        isFinished = result;
                        synchronized (lock) {
                            isTimeout = false;
                            lock.notifyAll();
                        }

                        Log.d(TAG, "dexMerge  " + result + (System.currentTimeMillis() - mStartTime) + " ms");


                    }

                });
            } catch (RemoteException e) {

                isTimeout = false;
                Log.d(TAG, "dexMerge registerListener RemoteException" +
                        (System.currentTimeMillis() - mStartTime) +
                        " ms");
                return;

            } finally {
                synchronized (lock) {
                    lock.notifyAll();
                }
            }

            try {
                service.linkToDeath(mDeathRecipient, 0);
            } catch (RemoteException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    };


    private class MyServiceDeathHandler implements IBinder.DeathRecipient {
        public MyServiceDeathHandler() {
        }

        @Override
        public void binderDied() {
            synchronized (lock) {
                isTimeout = false;
                isBinderDied = true;
                lock.notifyAll();
//                mergeLock.notifyAll();
            }
            Log.e(TAG, "dexMerge service died");
        }
    }
}
