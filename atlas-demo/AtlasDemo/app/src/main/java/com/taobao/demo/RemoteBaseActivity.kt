package com.taobao.demo

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.android.play.core.splitcompat.SplitCompat

/**
 * @ClassName RemoteBaseActivity
 * @Description TODO
 * @Author zhayu.ll
 * @Date 2019-09-17 15:50
 * @Version 1.0
 */
abstract class RemoteBaseActivity : AppCompatActivity() {

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase)
        SplitCompat.install(this)
    }
}