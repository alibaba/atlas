package com.android.build.gradle.internal;

import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.api.BaseVariantOutputImpl;
import com.android.ide.common.build.ApkData;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author lilong
 * @create 2017-11-30 下午1:50
 */

public class ApkDataUtils {

    public static ApkData get(BaseVariantOutput baseVariantOutput){
        Method method = null;
        try {
            method = BaseVariantOutputImpl.class.getDeclaredMethod("getApkData");
            method.setAccessible(true);
            return (ApkData) method.invoke(baseVariantOutput);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }

        return null;
    }
}
