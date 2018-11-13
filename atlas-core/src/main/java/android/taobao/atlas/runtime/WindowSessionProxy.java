package android.taobao.atlas.runtime;

import android.app.Application;
import android.content.Context;
import android.taobao.atlas.framework.Atlas;
import android.taobao.atlas.framework.BundleClassLoader;
import android.taobao.atlas.util.log.impl.AtlasMonitor;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WindowSessionProxy
 *
 * @author zhayu.ll
 * @date 18/8/27
 */
public class WindowSessionProxy {

    public static void delegateWindowSession(Application mRawApplication) {
        try {
            WindowManager windowManager = (WindowManager)mRawApplication.getSystemService(Context.WINDOW_SERVICE);
            Field mGlobalField = windowManager.getClass().getDeclaredField("mGlobal");
            mGlobalField.setAccessible(true);
            final Object windowManagerGlobal = mGlobalField.get(windowManager);
            Method getWindowSession = windowManagerGlobal.getClass().getDeclaredMethod("getWindowSession");
            getWindowSession.setAccessible(true);
            Field sWindowSession = windowManagerGlobal.getClass().getDeclaredField("sWindowSession");
            sWindowSession.setAccessible(true);
            final Object windowSession = getWindowSession.invoke(null);
            Object newWindowSession = Proxy.newProxyInstance(mRawApplication.getClassLoader(), windowSession.getClass().getInterfaces(), new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    Object res = method.invoke(windowSession, args);
                    if (method.getName().equals("addToDisplay")) {
                        if (((int)res) == -2||((int)res) == -1){
                            Log.e("WindowSessionProxy","bad token");
                            clearLastView(windowManagerGlobal);
                            return -6;
                        }else {
                            return res;
                        }
                    }
                    return res;
                }
            });

            sWindowSession.set(null,newWindowSession);
        }catch (Throwable e){
            e.printStackTrace();
        }
    }

    private static void clearLastView(Object windowManagerGlobal) {
        try {
            Field f = windowManagerGlobal.getClass().getDeclaredField("mViews");
            f.setAccessible(true);
            Field f1 = windowManagerGlobal.getClass().getDeclaredField("mRoots");
            f.setAccessible(true);
            Field f2 = windowManagerGlobal.getClass().getDeclaredField("mParams");
            f2.setAccessible(true);
            List<View> mViews = (List<View>) f.get(windowManagerGlobal);
            List<Object> mRoots = (List<Object>) f1.get(windowManagerGlobal);
            List<WindowManager.LayoutParams> mParams = (List<WindowManager.LayoutParams>) f2.get(windowManagerGlobal);
            mViews.remove(mViews.size() - 1);
            mRoots.remove(mRoots.size() - 1);
            mParams.remove(mParams.size() - 1);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
