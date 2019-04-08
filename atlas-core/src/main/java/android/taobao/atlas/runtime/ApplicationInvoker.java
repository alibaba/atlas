package android.taobao.atlas.runtime;

import android.app.Application;
import android.content.Context;
import android.taobao.atlas.util.RefectUtils;
import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 创建日期：2019/4/8 on 上午11:29
 * 描述:
 * 作者:zhayu.ll
 */
public class ApplicationInvoker {
    private static Map<String, ApplicationInvoker> mApplications = new ConcurrentHashMap();
    private volatile boolean inited = false;

    private ApplicationInvoker() {
    }

    public static ApplicationInvoker getInstance(String bundleName) {
        synchronized (mApplications) {
            if (mApplications.containsKey(bundleName)) {
                return  mApplications.get(bundleName);
            } else {
                ApplicationInvoker applicationInvoker = new ApplicationInvoker();
                mApplications.put(bundleName, applicationInvoker);
                return applicationInvoker;
            }
        }
    }

    public synchronized void invoke(String applicationClassName, Context context) {
        if (!this.inited) {
            this.inited = true;
            if (!TextUtils.isEmpty(applicationClassName)) {
                try {
                    Class<?> applicationClass = ApplicationInvoker.class.getClassLoader().loadClass(applicationClassName);
                    if (applicationClass == null) {
                        throw new ClassNotFoundException(String.format("can not find class: %s", applicationClassName));
                    }

                    Application app = (Application) applicationClass.newInstance();
                    Method attachBaseContext = RefectUtils.method(app, "attachBaseContext", new Class[]{Context.class});
                    attachBaseContext.invoke(app, context);
                    Method onCreate = RefectUtils.method(app, "onCreate", new Class[0]);
                    onCreate.invoke(app);
                    Log.e("ApplicationInvoker", "successfully invoke start application " + applicationClassName);
                } catch (Exception var7) {
                    var7.printStackTrace();
                    this.inited = false;
                }

            }
        }
    }

    public synchronized void invoke(String applicationClassName, Context context, ApplicationInvoker.AppInitListener appInitListener) {
        if (this.inited) {
            if (appInitListener != null) {
                appInitListener.onInitFinish();
            }

        } else {
            this.inited = true;
            if (TextUtils.isEmpty(applicationClassName)) {
                if (appInitListener != null) {
                    appInitListener.onInitFinish();
                }

            } else {
                Class applicationClass = null;

                try {
                    applicationClass = ApplicationInvoker.class.getClassLoader().loadClass(applicationClassName);
                    if (applicationClass == null) {
                        throw new ClassNotFoundException(String.format("can not find class: %s", applicationClassName));
                    }

                    Application app = (Application) applicationClass.newInstance();
                    Method attachBaseContext = RefectUtils.method(app, "attachBaseContext", new Class[]{Context.class});
                    attachBaseContext.invoke(app, context);
                    Method onCreate = RefectUtils.method(app, "onCreate", new Class[0]);
                    onCreate.invoke(app);
                    Log.e("ApplicationInvoker", "successfully invoke start application " + applicationClassName);
                    if (appInitListener != null) {
                        appInitListener.onInitFinish();
                    }
                } catch (Exception var8) {
                    this.inited = false;
                    if (appInitListener!= null) {
                        appInitListener.onInitError(var8.getMessage());
                    }
                }

            }
        }
    }

    public interface AppInitListener {
        void onInitFinish();

        void onInitError(String var1);
    }
}
