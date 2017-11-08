package android.taobao.atlas.patch;

import android.content.Context;
import android.content.pm.PackageManager;
import android.taobao.atlas.framework.Atlas;
import android.taobao.atlas.framework.BundleImpl;
import android.taobao.atlas.framework.Framework;
import android.taobao.atlas.runtime.RuntimeVariables;
import android.taobao.atlas.util.AtlasFileLock;
import android.taobao.atlas.util.BundleLock;
import android.taobao.atlas.util.IOUtil;
import android.taobao.atlas.util.StringUtils;
import android.util.Log;
import android.util.Pair;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import dalvik.system.DexFile;

/**
 * Created by guanjie on 2017/11/6.
 */

public class AtlasHotPatchManager implements BundleListener{
    private static final String TAG = "AtlasHotPatchManager";
    private static final AtlasHotPatchManager sPatchManager = new AtlasHotPatchManager();
    private static final String HOTFIX_NAME_POSTFIX = ".dex";

    private File sCurrentVersionPatchDir;
    private String currentVersionName;
    private HashMap<String,File> patchInfos = new HashMap<>();
    private HashMap<String,String> activePatchs = new HashMap<>();
    private OnPatchActivatedListener mPatchListener;

    public static synchronized AtlasHotPatchManager getInstance(){
        return sPatchManager;
    }

    public interface OnPatchActivatedListener{
        void onPatchActivated(String bundleName,String location);
    }

    private AtlasHotPatchManager(){
        try {
            String versionName = RuntimeVariables.androidApplication.getPackageManager().getPackageInfo(RuntimeVariables.androidApplication.getPackageName(),0).versionName;
            currentVersionName = versionName;
            File sPatchDir = new File(Framework.STORAGE_LOCATION,"hotpatch/");
            if(RuntimeVariables.sCurrentProcessName.equals(RuntimeVariables.androidApplication.getPackageName())){
                purgeOldCache(sPatchDir,versionName);
            }
            sCurrentVersionPatchDir = new File(sPatchDir,versionName);
            if(!sCurrentVersionPatchDir.exists()){
                sCurrentVersionPatchDir.mkdirs();
            }
            if(sCurrentVersionPatchDir.exists()) {
                AtlasFileLock.getInstance().unLock(sPatchDir);
                File[] bundlePatchs = sCurrentVersionPatchDir.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File file) {
                        return file.isDirectory();
                    }
                });
                if(bundlePatchs!=null){
                    for(File bundePatchDir : bundlePatchs){
                        if(new File(bundePatchDir,"deprecated").exists()) {
                            Framework.deleteDirectory(bundePatchDir);
                        }else{
                            patchInfos.put(bundePatchDir.getName(), bundePatchDir);
                        }
                    }
                }

            }
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }finally {
            AtlasFileLock.getInstance().unLock(sCurrentVersionPatchDir);
        }
        patchMainDex();
    }

    public void installHotFixPatch(String targetVersion, HashMap<String,Pair<Integer,InputStream>> patchEntries) throws IOException{
        if(!sCurrentVersionPatchDir.exists()){
            sCurrentVersionPatchDir.mkdirs();
        }
        if(!sCurrentVersionPatchDir.exists()){
            return;
        }
        File sPatchVersionDir = new File(sCurrentVersionPatchDir,targetVersion);
        if(!sPatchVersionDir.exists()){
            sPatchVersionDir.mkdirs();
        }
        if(!sPatchVersionDir.exists()){
            throw new IOException("crate patch dir fail : "+sPatchVersionDir.getAbsolutePath());
        }
        Iterator iter = patchEntries.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String,Pair<Integer,InputStream>> entry = (Map.Entry) iter.next();
            File patchBundleDir = new File(sPatchVersionDir,entry.getKey());
            patchBundleDir.mkdirs();
            if(patchBundleDir.exists()){
                String lockKey = entry.getKey()+".patch";
                try {
                    BundleLock.WriteLock(lockKey);
                    File hotFixFile = new File(patchBundleDir, entry.getValue().first+HOTFIX_NAME_POSTFIX);
                    installDex(entry.getValue().second, hotFixFile);
                    patchInfos.put(entry.getKey(),hotFixFile);
                    BundleImpl bundle = (BundleImpl) Atlas.getInstance().getBundle(entry.getKey());
                    if(bundle!=null){
                        Patch p = new Patch(hotFixFile,bundle.getClassLoader());
                        activePatch(entry.getKey(),p);
                    }
                }catch(Exception e){
                    e.printStackTrace();
                }finally{
                    BundleLock.WriteUnLock(lockKey);
                }
            }
        }
    }

    public void setPatchListener(OnPatchActivatedListener listener){
        mPatchListener = listener;
    }

    private void patchMainDex(){
        File maindexPatchFile = patchInfos.get("com.taobao.maindex");
        if(maindexPatchFile.exists()){
            File maindexPatchs[] = maindexPatchFile.listFiles(new FileFilter() {
                @Override
                public boolean accept(File patchFle) {
                    return patchFle.getName().endsWith(HOTFIX_NAME_POSTFIX);
                }
            });
            Patch targetPatch = null;
            Patch oldPatch = null;
            if(maindexPatchs!=null && maindexPatchs.length>0) {
                for (File patchFile : maindexPatchs) {
                    Patch p = new Patch(patchFile,RuntimeVariables.androidApplication.getClassLoader());
                    if(targetPatch==null){
                        targetPatch = p;
                    }else{
                        if(targetPatch.compare(p)>0){
                            oldPatch = p;
                        }else{
                            oldPatch = targetPatch;
                            targetPatch = p;
                        }
                        if(oldPatch!=null){
                            if(RuntimeVariables.sCurrentProcessName.equals(RuntimeVariables.androidApplication.getPackageName())){
                                oldPatch.purge();
                            }
                        }
                    }
                }
            }
            if(targetPatch!=null){
                activePatch("com.taobao.maindex",targetPatch);
            }
        }
    }

    private void patchBundle(Bundle bundle){
        String lockKey = bundle.getLocation() + ".patch";
        try {
            BundleLock.WriteLock(lockKey);
            if (activePatchs.get(bundle.getLocation()) != null && !new File(activePatchs.get(bundle.getLocation()),"deprecated").exists()) {
                return;
            }
            File bundlePatchFile = patchInfos.get(bundle.getLocation());
            if (bundlePatchFile.exists()) {
                File bundledexPatchs[] = bundlePatchFile.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File patchFle) {
                        return patchFle.getName().endsWith(HOTFIX_NAME_POSTFIX);
                    }
                });
                Patch targetPatch = null;
                Patch oldPatch;
                if (bundledexPatchs != null && bundledexPatchs.length > 0) {
                    for (File patchFile : bundledexPatchs) {
                        Patch p = new Patch(patchFile, ((BundleImpl) bundle).getClassLoader());
                        if (targetPatch == null) {
                            targetPatch = p;
                        } else {
                            if (targetPatch.compare(p) > 0) {
                                oldPatch = p;
                            } else {
                                oldPatch = targetPatch;
                                targetPatch = p;
                            }
                            if (oldPatch != null) {
                                if (RuntimeVariables.sCurrentProcessName.equals(RuntimeVariables.androidApplication.getPackageName())) {
                                    oldPatch.purge();
                                }
                            }
                        }
                    }
                }
                if (targetPatch != null) {
                    activePatch(bundle.getLocation(),targetPatch);
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }finally {
            BundleLock.WriteUnLock(lockKey);
        }
    }

    public void disablePatch(String bundleName){
        File bundlePatchDir = new File(sCurrentVersionPatchDir,bundleName);
        if(bundlePatchDir.exists()){
            try {
                Log.e(TAG,"disable patch : "+bundleName);
                new File(bundlePatchDir,"deprecated").createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void activePatch(String patchBundleName,Patch patch){
        activePatchs.put(patchBundleName,patch.file.getAbsolutePath());
        try {
            patch.activate();
            if(mPatchListener!=null){
                mPatchListener.onPatchActivated(patchBundleName,patch.file.getAbsolutePath());
            }
        }catch (Throwable e){
            e.printStackTrace();
            if(Framework.DEBUG){
                throw new RuntimeException(e);
            }
        }
    }

    private void installDex(InputStream hotDexStream,  File targetFile) throws IOException{
        targetFile.createNewFile();
        BufferedOutputStream bos = new BufferedOutputStream(
                new FileOutputStream(targetFile));

        BufferedInputStream bi = new BufferedInputStream(hotDexStream);
        byte[] readContent = new byte[1024];
        int readCount = bi.read(readContent);
        while (readCount != -1) {
            bos.write(readContent, 0,readCount);
            readCount =bi.read(readContent);
        }
        IOUtil.quietClose(bos);
        IOUtil.quietClose(bi);
    }

    private void purgeOldCache(File patchDir, final String currentVersion){
        try {
            if (patchDir.exists()) {
                File[] oldPatchDirs = patchDir.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File file) {
                        return (file.isDirectory() && !file.getName().equals(currentVersion));
                    }
                });
                if (oldPatchDirs != null) {
                    for (File oldDir : oldPatchDirs) {
                        Framework.deleteDirectory(oldDir);
                    }
                }
            }
        }catch(Throwable e){e.printStackTrace();}
    }

    @Override
    public void bundleChanged(BundleEvent event) {
        if(event.getType() == BundleEvent.BEFORE_STARTED){
            patchBundle(event.getBundle());
        }
    }

    private static class Patch{
        private File file;
        private File odexFile;
        private ClassLoader sourceClassLoader;
        private DexFile dexFile;
        private int  version;
        private PatchClassLoader classLoader;

        private Patch(File patch,ClassLoader sourceClassLoader){
            file = patch;
            String patchNameWithoutPostFix = StringUtils.substringBetween(patch.getName(),"",HOTFIX_NAME_POSTFIX);
            odexFile = new File(file.getName(),patchNameWithoutPostFix+".odex");
            version = Integer.parseInt(patchNameWithoutPostFix);
            this.sourceClassLoader = sourceClassLoader;
        }

        public synchronized PatchClassLoader loadDex(){
            if (classLoader == null){
                try {
                    AtlasFileLock.getInstance().LockExclusive(odexFile);
                    dexFile = (DexFile) RuntimeVariables.sDexLoadBooster.getClass().getDeclaredMethod("loadDex",Context.class,String.class, String.class, int.class, boolean.class).invoke(
                            RuntimeVariables.sDexLoadBooster,RuntimeVariables.androidApplication, file.getAbsolutePath(), odexFile.getAbsolutePath(), 0, true);
                    if(dexFile!=null){
                        classLoader = new PatchClassLoader(sourceClassLoader,this);
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }finally {
                    AtlasFileLock.getInstance().unLock(odexFile);
                    return classLoader;
                }
            }else{
                return classLoader;
            }
        }

        public void activate(){
            if(classLoader==null){
                classLoader = loadDex();
            }
            if(classLoader!=null) {
                ArrayList<Class> entryPointClasses = new ArrayList<>();
                Enumeration<String> classItems = dexFile.entries();
                while (classItems.hasMoreElements()) {
                    String className = classItems.nextElement().replace("/", ".");
                    Class clz = classLoader.findPatchClass(className);
                    if (isValidEntryClass(clz)) {
                        entryPointClasses.add(clz);
                    }
                }
                if (entryPointClasses != null) {
                    for (Class entryClass : entryPointClasses) {
                        try {
                            IAtlasHotPatch patch = (IAtlasHotPatch) entryClass.newInstance();
                            patch.fix(RuntimeVariables.androidApplication);
                        } catch (Throwable e) {
                            e.printStackTrace();
                            if(Framework.DEBUG){
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }
            }
        }

        private boolean isValidEntryClass(Class clazz){
            Class[] interfaces = clazz.getInterfaces();
            if(interfaces!=null){
                for(Class itf : interfaces){
                    if(itf == IAtlasHotPatch.class){
                        Process processAnno =  (Process) itf.getAnnotation(Process.class);
                        String patchProcess = processAnno!=null ? processAnno.value() : RuntimeVariables.androidApplication.getPackageName();
                        if(processAnno.value().equals(RuntimeVariables.sCurrentProcessName)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        public void install(){

        }

        public void purge(){
            if(odexFile.exists()) {
                odexFile.delete();
            }
            if(file.exists()){
                file.delete();
            }
        }

        public int compare(Patch targetPatch){
            return version > targetPatch.version ? 1 : 0;
        }
    }

    private static class PatchClassLoader extends ClassLoader{
        private Patch patch;
        private ClassLoader sourceClassLoader;
        public PatchClassLoader(ClassLoader sourceClassLoader,Patch patch) {
            super(Object.class.getClassLoader());
            this.patch = patch;
            this.sourceClassLoader = sourceClassLoader;
        }

        @Override
        protected Class<?> findClass(String className) throws ClassNotFoundException {
            Class clz = findPatchClass(className);
            if(clz!=null){
                return clz;
            }else{
                return sourceClassLoader.loadClass(className);
            }

        }
        private Class findPatchClass(String className){
            return patch.dexFile.loadClass(className,this);
        }
    }
}
