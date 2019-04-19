package android.taobao.atlas.runtime.newcomponent.service;

import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.ServiceInfo;
import android.content.pm.ActivityInfo;
import android.app.IServiceConnection;
import android.content.pm.ProviderInfo;
import android.app.IActivityManager.ContentProviderHolder;

interface IDelegateBinder {

    IBinder startService(in Intent serviceIntent,in ServiceInfo info);

    IBinder bindService(in Intent serviceIntent,in IBinder activityToken,in IServiceConnection conn);

    boolean unbindService(in IServiceConnection conn);

    int stopService(in Intent serviceIntent);

    Intent handleActivityStack(in Intent intent,in ActivityInfo info);

    void handleReceiver(in Intent intent, in ActivityInfo info);
}
