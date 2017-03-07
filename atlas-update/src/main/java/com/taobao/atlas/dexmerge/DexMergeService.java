package com.taobao.atlas.dexmerge;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * @author zhayu.ll
 */
public class DexMergeService extends Service {

    IDexMergeCallback mCallback = null;

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private final IDexMergeBinder.Stub mBinder = new IDexMergeBinder.Stub() {

        @Override
        public void dexMerge(String patchFilePath,List<MergeObject> list, boolean b) throws RemoteException {
            MergeExcutorServices mergeExcutorServices = new MergeExcutorServices(mCallback);
            try {
                mergeExcutorServices.excute(patchFilePath,list, b);
            } catch (ExecutionException e) {
                e.printStackTrace();
                if (mCallback!= null){
                    mCallback.onMergeAllFinish(false,"ExecutionException");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                if (mCallback!= null){
                    mCallback.onMergeAllFinish(false,"InterruptedException");
                }
            }
        }


        public void registerListener(IDexMergeCallback cb) {
            if (cb != null) {
                mCallback = cb;
            }
        }
    };
}
