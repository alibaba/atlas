package android.taobao.atlas.util;

import com.taobao.android.runtime.AndroidRuntime;

import java.io.Serializable;


public class OdexVerifier implements Serializable{

    public static boolean isOdexValid(String odexPath){
        return AndroidRuntime.getInstance().isOdexValid(odexPath);
    }
}
