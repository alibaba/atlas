package android.taobao.atlas.runtime.newcomponent;

import android.taobao.atlas.runtime.RuntimeVariables;

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
 *  <Activity
        android:name="ATLASPROXY_com_taobao_demo_Activity"/>
    <Activity
        android:name="ATLASPROXY_remote_Activity"
        android:process=":remote"/>
    <Activity
        android:name="ATLASPROXY_com_taobao_single_Activity"
        android:process="com.taobao.single"/>

    <Service
        android:name="ATLASPROXY_com_taobao_demo_Service"/>
    <Service
        android:name="ATLASPROXY_remote_Service"
        android:process=":remote"/>
    <Service
        android:name="ATLASPROXY_com_taobao_single_Service"
        android:process="com.taobao.single"/>

    <ContentProvider
        android:name="ATLASPROXY_com_taobao_demo_Provider"
        android:authorities="ATLASPROXY_com_taobao_demo_Provider"/>
    <ContentProvider
        android:name="ATLASPROXY_remote_Provider"
        android:authorities="ATLASPROXY_remote_Provider"
        android:process=":remote"/>
    <ContentProvider
        android:name="ATLASPROXY_com_taobao_single_Provider"
        android:authorities="ATLASPROXY_com_taobao_single_Provider"
        android:process="com.taobao.single"/>

 * and also apk will add the classses below
 *
 *  class ATLASPROXY_com_taobao_demo_Service   extends android.taobao.atlas.runtime.newcomponent.service.BaseDelegateService
 *  class ATLASPROXY_remote_Service            extends android.taobao.atlas.runtime.newcomponent.service.BaseDelegateService
 *  class ATLASPROXY_com_taobao_single_Service extends android.taobao.atlas.runtime.newcomponent.service.BaseDelegateService
 *
 *  class ATLASPROXY_com_taobao_demo_Provider    extends android.taobao.atlas.runtime.newcomponent.provider.ContentProviderBridge
 *  class ATLASPROXY_remote_Service              extends android.taobao.atlas.runtime.newcomponent.provider.ContentProviderBridge
 *  class ATLASPROXY_com_taobao_single_Provider  extends android.taobao.atlas.runtime.newcomponent.provider.ContentProviderBridge
 *
 */
public class BridgeUtil {

    public static final int TYPE_ACTIVITYBRIDGE = 1;
    public static final int TYPE_SERVICEBRIDGE  = 2;
    public static final int TYPE_PROVIDERBRIDGE = 3;

    public static final String PROXY_PREFIX = "ATLASPROXY";

    public static String getBridgeComponent(final int type,final String process){
        switch(type){
            case TYPE_ACTIVITYBRIDGE:
                return String.format("%s_%s_%s",PROXY_PREFIX,fixProcess(process),"Activity");
            case TYPE_SERVICEBRIDGE:
                return String.format("%s_%s_%s",PROXY_PREFIX,fixProcess(process),"Service");
            case TYPE_PROVIDERBRIDGE:
                return String.format("%s_%s_%s",PROXY_PREFIX,fixProcess(process),"Provider");
        }
        throw new RuntimeException("wrong type");
    }

    public static String fixProcess(String processName){
        String prefix = RuntimeVariables.androidApplication+":";
        if(processName.startsWith(prefix)){
            return processName.substring(prefix.length(),processName.length());
        }else{
            return processName.replace(".","_");
        }
    }
}
