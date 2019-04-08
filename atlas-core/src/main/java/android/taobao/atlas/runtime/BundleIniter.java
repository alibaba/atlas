package android.taobao.atlas.runtime;

import android.taobao.atlas.bundleInfo.AtlasBundleInfoManager;
import android.text.TextUtils;

/**
 * 创建日期：2019/4/8 on 上午11:27
 * 描述:
 * 作者:zhayu.ll
 */
public class BundleIniter {

    public static void initBundle(String bundleName, ApplicationInvoker.AppInitListener appInitListener){

        if (TextUtils.isEmpty(bundleName)|| AtlasBundleInfoManager.instance().getBundleInfo(bundleName) == null) {

            return;
        }

        String appName = AtlasBundleInfoManager.instance().getBundleInfo(bundleName).applicationName;

        if (!TextUtils.isEmpty(appName)) {
            ApplicationInvoker.getInstance(bundleName).invoke(appName, RuntimeVariables.androidApplication,appInitListener);

        }else {
            if (appInitListener != null){
                appInitListener.onInitFinish();
            }
        }
    }


}
