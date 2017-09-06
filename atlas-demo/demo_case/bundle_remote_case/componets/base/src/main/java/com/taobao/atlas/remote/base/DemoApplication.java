package com.taobao.atlas.remote.base;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.taobao.atlas.bundleInfo.AtlasBundleInfoManager;
import android.taobao.atlas.framework.Atlas;
import android.taobao.atlas.runtime.ActivityTaskMgr;
import android.taobao.atlas.runtime.ClassNotFoundInterceptorCallback;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import org.osgi.framework.BundleException;

import java.io.File;

/**
 * Created by zhongcang on 2017/9/6.
 * .
 */

public class DemoApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        Atlas.getInstance().setClassNotFoundInterceptorCallback(new ClassNotFoundInterceptorCallback() {
            @Override
            public Intent returnIntent(Intent intent) {
                final String className = intent.getComponent().getClassName();
                final String bundleName = AtlasBundleInfoManager.instance().getBundleForComponet(className);
                if (TextUtils.isEmpty(bundleName) || AtlasBundleInfoManager.instance().isInternalBundle(bundleName)) {
                    return intent;
                }
                //remote bundle
                Activity activity = ActivityTaskMgr.getInstance().peekTopActivity();
                File remoteBundleFile = new File(activity.getExternalCacheDir(), "lib" + bundleName.replace(".", "_") + ".so");
                if (!remoteBundleFile.exists()) {
                    tips(activity, "can't find remote bundle with path : " + remoteBundleFile.getAbsolutePath());
                    return intent;
                }

                String path = remoteBundleFile.getAbsolutePath();
                PackageInfo info = activity.getPackageManager().getPackageArchiveInfo(path, 0);
                try {
                    Atlas.getInstance().installBundle(info.packageName, new File(path));
                } catch (BundleException e) {
                    tips(activity, "install remote bundle failed，" + e.getMessage());
                    e.printStackTrace();
                }

                activity.startActivities(new Intent[]{intent});
                return intent;
            }
        });
    }

    private void tips(Activity activity, String logStr) {
        Toast.makeText(activity, logStr, Toast.LENGTH_LONG).show();
        Log.e(Env.TAG, logStr);
    }
}
