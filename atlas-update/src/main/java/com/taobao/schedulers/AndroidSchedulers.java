package com.taobao.schedulers;

import android.os.Handler;
import android.os.Looper;
import com.taobao.plugins.RxAndroidPlugins;
import io.reactivex.Scheduler;

import java.util.concurrent.Callable;

/**
 * @author lilong
 * @create 2017-06-05 上午9:50
 */
public final class AndroidSchedulers {

    private static final class MainHolder {

        static final Scheduler DEFAULT = new HandlerScheduler(new Handler(Looper.getMainLooper()));
    }

    private static final Scheduler MAIN_THREAD = RxAndroidPlugins.initMainThreadScheduler(
            new Callable<Scheduler>() {
                @Override public Scheduler call() throws Exception {
                    return MainHolder.DEFAULT;
                }
            });

    /** A {@link Scheduler} which executes actions on the Android main thread. */
    public static Scheduler mainThread() {
        return RxAndroidPlugins.onMainThreadScheduler(MAIN_THREAD);
    }

    /** A {@link Scheduler} which executes actions on {@code looper}. */
    public static Scheduler from(Looper looper) {
        if (looper == null) throw new NullPointerException("looper == null");
        return new HandlerScheduler(new Handler(looper));
    }

    private AndroidSchedulers() {
        throw new AssertionError("No instances.");
    }
}
