package android.taobao.atlas.startup.patch.releaser;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.graphics.Path;
import android.os.Environment;
import android.os.Process;
import android.os.UserHandle;
import android.taobao.atlas.hack.Hack;
import android.taobao.atlas.startup.patch.KernalBundle;
import android.text.TextUtils;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * DexProfile
 *
 * @author zhayu.ll
 * @date 18/9/5
 */
public class PatchDexProfile {

    private static PatchDexProfile mPatchDexProfile = null;
    private Application mApp;

    static Hack.HackedClass vmRuntime_clazz = null;
    static Hack.HackedField vmRuntime_THE_ONE = null;
    static Hack.HackedMethod vmRuntime_disableJitCompilation = null;
    static Hack.HackedMethod vmRuntime_registerAppInfo = null;


    static Hack.HackedClass systemProperties_clazz = null;
    static Hack.HackedMethod systemProperties_getBoolean = null;
    static Hack.HackedMethod systemProperties_set = null;


    static Hack.HackedClass applicationLoaders_clazz = null;
    static Hack.HackedField applicationLoaders_gApplicationLoaders = null;
    static Hack.HackedMethod applicationLoaders_addPath = null;


    static {
        try {
            vmRuntime_clazz = Hack.into("dalvik.system.VMRuntime");
            vmRuntime_THE_ONE = vmRuntime_clazz.staticField("THE_ONE");
            vmRuntime_disableJitCompilation = vmRuntime_clazz.method("disableJitCompilation");


            systemProperties_clazz = Hack.into("android.os.SystemProperties");
            systemProperties_getBoolean = systemProperties_clazz.staticMethod("getBoolean", String.class, boolean.class);
            systemProperties_set = systemProperties_clazz.staticMethod("set",String.class,String.class);
//            dexLoadReporter_clazz = Hack.into("android.app.DexLoadReporter");
//            dexLoadReporter_INSTANCE = dexLoadReporter_clazz.staticField("INSTANCE");
//            dexLoadReporter_registerAppDataDir = dexLoadReporter_clazz.method("registerAppDataDir", String.class, String.class);

            applicationLoaders_clazz = Hack.into("android.app.ApplicationLoaders");
            applicationLoaders_gApplicationLoaders = applicationLoaders_clazz.staticField("gApplicationLoaders");
//            applicationLoaders_addPath = applicationLoaders_clazz.method("addPath", ClassLoader.class, String.class);
        }catch (Throwable e){
            e.printStackTrace();
        }

    }

    private PatchDexProfile(Application mApplication) {
        this.mApp = mApplication;
    }


    public static PatchDexProfile instance(Application mApp) {
        if (mPatchDexProfile == null) {
            mPatchDexProfile = new PatchDexProfile(mApp);
        }
        return mPatchDexProfile;
    }


    public void disableJitCompile() {
        try {
            if (vmRuntime_disableJitCompilation != null) {
                vmRuntime_disableJitCompilation.invoke(vmRuntime_THE_ONE.get(null));
            }
//            systemProperties_set.invoke(null,"dalvik.vm.usejitprofiles","false");
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void setupJitProfileSupport(String[] dexFiles) {

        try {
            boolean jitEnabled = (boolean) systemProperties_getBoolean.invoke(null, "dalvik.vm.usejitprofiles", false);
            if (!jitEnabled) {
                return;
            }
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

        // Only set up profile support if the loaded apk has the same uid as the
        // current process.
        // Currently, we do not support profiling across different apps.
        // (e.g. application's uid might be different when the code is
        // loaded by another app via createApplicationContext)
        if (mApp.getApplicationInfo().uid != Process.myUid()) {
            return;
        }

        final List<String> codePaths = new ArrayList<>();

        //we no need to add orignal apk path to profile
//        if ((mApp.getApplicationInfo().flags & ApplicationInfo.FLAG_HAS_CODE) != 0) {
//            codePaths.add(mApp.getApplicationInfo().sourceDir);
//        }

        if (dexFiles != null) {
            Collections.addAll(codePaths, dexFiles);
        }

        if (codePaths.isEmpty()) {
            // If there are no code paths there's no need to setup a profile file and register with
            // the runtime,
            return;
        }

        final File profileFile = getPrimaryProfileFile(mApp.getPackageName());

        try {

            vmRuntime_registerAppInfo.invoke(null, profileFile.getPath(),
                    codePaths.toArray(new String[codePaths.size()]));
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }


//        // Register the app data directory with the reporter. It will
//        // help deciding whether or not a dex file is the primary apk or a
//        // secondary dex.
//
//        try {
//
//            dexLoadReporter_registerAppDataDir.invoke(dexLoadReporter_INSTANCE.get(null), mApp.getPackageName(), mApp.getApplicationInfo().dataDir);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }

    }


    public static void addPathes(List<String> addedPaths) {
        try {
            if (addedPaths != null && addedPaths.size() > 0) {
                String add = TextUtils.join(File.pathSeparator, addedPaths);
                applicationLoaders_addPath.invoke(applicationLoaders_gApplicationLoaders.get(null), KernalBundle.class.getClassLoader(), add);
            }

        } catch (Exception e) {
            e.printStackTrace();

        }


    }


    private File getPrimaryProfileFile(String packageName) {
        try {
            Method m = Environment.class.getDeclaredMethod("getDataProfilesDePackageDirectory", int.class, String.class);
            m.setAccessible(true);
            File profileDir = (File) m.invoke(null, mApp.getApplicationInfo().uid / 100000, packageName);
            return new File(profileDir, "primary-patch.prof");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
