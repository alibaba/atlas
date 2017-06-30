package android.taobao.atlas.startup;

import android.content.Context;
import android.os.Build;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import dalvik.system.DexFile;

/**
 * Created by guanjie on 2017/5/24.
 */

public class DexFileCompat implements Serializable{

    public static Method openDexFile;
    public static Field  mCookie;
    public static Field  mFileName;

    static{
        try {
            openDexFile = DexFile.class.getDeclaredMethod("openDexFile", String.class, String.class, int.class);
            openDexFile.setAccessible(true);

            mCookie = DexFile.class.getDeclaredField("mCookie");
            mCookie.setAccessible(true);
            mFileName = DexFile.class.getDeclaredField("mFileName");
            mFileName.setAccessible(true);
        }catch(Throwable e){
            if(Build.VERSION.SDK_INT>15) {
                throw new RuntimeException(e);
            }
        }
    }

    static public DexFile loadDex(Context context, String sourcePathName, String outputPathName,
                                  int flags) throws Exception {
        if(Build.VERSION.SDK_INT<=15) {
            return DexFile.loadDex(sourcePathName,outputPathName,flags);
        }else{
            DexFile dexFile = DexFile.loadDex(context.getApplicationInfo().sourceDir,null,0);
            try {
                int cookie = (int)openDexFile.invoke(null,sourcePathName,outputPathName,flags);
                mFileName.set(dexFile,sourcePathName);
                mCookie.set(dexFile,cookie);
            } catch (Exception e) {
                throw  e;
            }
            return dexFile;
        }
    }

}
