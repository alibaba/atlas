package com.taobao.atlas.dexmerge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.taobao.atlas.runtime.RuntimeVariables;

/**
 * PatchVersionReceiver
 *
 * @author zhayu.ll
 * @date 18/9/7
 */
public class PatchVersionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int version = intent.getIntExtra("patch_version",2);
        RuntimeVariables.patchVersion = version;
        RuntimeVariables.androidApplication.unregisterReceiver(this);
    }
}
