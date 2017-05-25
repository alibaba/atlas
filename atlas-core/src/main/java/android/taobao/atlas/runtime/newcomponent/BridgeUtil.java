package android.taobao.atlas.runtime.newcomponent;

import android.taobao.atlas.runtime.RuntimeVariables;
import android.text.TextUtils;

/**
 * Created by guanjie on 2017/4/12.
 *
 * example:
 * if your app has three process: 1 | com.taobao.demo
 *                                2 | :remote
 *                                3 | com.taobao.single
 *
 * then AndroidManifest will add the components below
 *
 *  <activity
        android:name="android.taobao.atlas.runtime.newcomponent.ATLASPROXY_com_taobao_demo_Activity"/>
    <activity
        android:name="android.taobao.atlas.runtime.newcomponent.ATLASPROXY_com_taobao_demo_remote_Activity"
        android:process=":remote"/>
    <activity
        android:name="android.taobao.atlas.runtime.newcomponent.ATLASPROXY_com_taobao_demo_com_taobao_single_Activity"
        android:process="com.taobao.single"/>

    <service
        android:name="android.taobao.atlas.runtime.newcomponent.ATLASPROXY_com_taobao_demo_Service"/>
    <service
        android:name="android.taobao.atlas.runtime.newcomponent.android.taobao.atlas.runtime.newcomponentATLASPROXY_com_taobao_demo_remote_Service"
        android:process=":remote"/>
    <service
        android:name="android.taobao.atlas.runtime.newcomponent.ATLASPROXY_com_taobao_demo_com_taobao_single_Service"
        android:process="com.taobao.single"/>

    <provider
        android:name="android.taobao.atlas.runtime.newcomponent.ATLASPROXY_com_taobao_demo_Provider"
        android:authorities="android.taobao.atlas.runtime.newcomponent.ATLASPROXY_com_taobao_demo_Provider"/>
    <provider
        android:name="android.taobao.atlas.runtime.newcomponent.ATLASPROXY_com_taobao_demo_remote_Provider"
        android:authorities="android.taobao.atlas.runtime.newcomponent.ATLASPROXY_com_taobao_demo_remote_Provider"
        android:process=":remote"/>
    <provider
        android:name="android.taobao.atlas.runtime.newcomponent.ATLASPROXY_com_taobao_demo_com_taobao_single_Provider"
        android:authorities="android.taobao.atlas.runtime.newcomponent.ATLASPROXY_com_taobao_demo_com_taobao_single_Provider"
        android:process="com.taobao.single"/>

 * and also apk will add the classses below
 *
 *  class ATLASPROXY_com_taobao_demo_Service   extends android.taobao.atlas.runtime.newcomponent.service.BaseDelegateService
 *  class ATLASPROXY_com_taobao_demo_remote_Service            extends android.taobao.atlas.runtime.newcomponent.service.BaseDelegateService
 *  class ATLASPROXY_com_taobao_demo_com_taobao_single_Service extends android.taobao.atlas.runtime.newcomponent.service.BaseDelegateService
 *
 *  class ATLASPROXY_com_taobao_demo_Provider    extends android.taobao.atlas.runtime.newcomponent.provider.ContentProviderBridge
 *  class ATLASPROXY_com_taobao_demo_remote_Service              extends android.taobao.atlas.runtime.newcomponent.provider.ContentProviderBridge
 *  class ATLASPROXY_com_taobao_demo_com_taobao_single_Provider  extends android.taobao.atlas.runtime.newcomponent.provider.ContentProviderBridge
 *
 */
public class BridgeUtil {

    public static final int TYPE_ACTIVITYBRIDGE = 1;
    public static final int TYPE_SERVICEBRIDGE  = 2;
    public static final int TYPE_PROVIDERBRIDGE = 3;

    public static final String COMPONENT_PACKAGE = "android.taobao.atlas.runtime.newcomponent.";
    public static final String PROXY_PREFIX = "ATLASPROXY";

    public static String getBridgeName(final int type, final String process){
        switch(type){
            case TYPE_ACTIVITYBRIDGE:
                return String.format("%s%s_%s_%s",COMPONENT_PACKAGE,PROXY_PREFIX,fixProcess(process),"Activity");
            case TYPE_SERVICEBRIDGE:
                return String.format("%s%s_%s_%s",COMPONENT_PACKAGE,PROXY_PREFIX,fixProcess(process),"Service");
            case TYPE_PROVIDERBRIDGE:
                return String.format("%s%s_%s_%s",COMPONENT_PACKAGE,PROXY_PREFIX,fixProcess(process),"Provider");
        }
        throw new RuntimeException("wrong type");
    }

    public static String fixProcess(String processName){
        processName = TextUtils.isEmpty(processName) ? RuntimeVariables.androidApplication.getPackageName() : processName;
        String prefix = RuntimeVariables.androidApplication.getPackageName()+":";
        if(processName.equals(RuntimeVariables.androidApplication.getPackageName())){
            return processName.replace(".", "_");
        }else {
            String childProcessPrefix = RuntimeVariables.androidApplication.getPackageName().replace(".","_")+"_";
            if (processName.startsWith(prefix)) {
                return childProcessPrefix+processName.substring(prefix.length(), processName.length());
            } else {
                return childProcessPrefix+processName.replace(".", "_");
            }
        }
    }
}
