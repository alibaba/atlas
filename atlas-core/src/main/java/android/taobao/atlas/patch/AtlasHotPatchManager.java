package android.taobao.atlas.patch;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
import android.text.TextUtils;
import android.util.Pair;
import dalvik.system.DexFile;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;

/**
 * Created by guanjie on 2017/11/6.
 */

public class AtlasHotPatchManager implements BundleListener{
    private static final String TAG = "AtlasHotPatchManager";
    private static final AtlasHotPatchManager sPatchManager = new AtlasHotPatchManager();
    private static final String HOTFIX_NAME_POSTFIX = ".dex";
    private final String MAIN_DEX_PKG = "com.taobao.maindex";

    private File sCurrentVersionPatchDir;
    private File meta;
    private ConcurrentHashMap<String,Long> hotpatchBundles = new ConcurrentHashMap<>();
    private HashMap<String,String> activePatchs = new HashMap<>();
    private OnPatchActivatedListener mPatchListener;

    public static synchronized AtlasHotPatchManager getInstance(){
        return sPatchManager;
    }

    public interface OnPatchActivatedListener{
        void onPatchActivated(String bundleName,String location,long patchVersion);
    }

    private AtlasHotPatchManager(){
        try {
            String versionName = RuntimeVariables.androidApplication.getPackageManager().getPackageInfo(RuntimeVariables.androidApplication.getPackageName(),0).versionName;
            File sPatchDir = new File(Framework.STORAGE_LOCATION,"hotpatch/");
            if(RuntimeVariables.sCurrentProcessName.equals(RuntimeVariables.androidApplication.getPackageName())){
                purgeOldPatchsByAppVersion(sPatchDir,versionName);
            }
            sCurrentVersionPatchDir = new File(sPatchDir,versionName);
            if(!sCurrentVersionPatchDir.exists()){
                sCurrentVersionPatchDir.mkdirs();
            }
            meta = new File(sCurrentVersionPatchDir,"meta");
            if(meta.exists()) {
                try {
                    readPatchInfo();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        patchMainDex();
        Atlas.getInstance().addBundleListener(this);
    }

    /**
     * @param targetVersion app版本
     * @param patchEntries <bundleName,<patchVersion,patchEntry>>
     * @throws IOException
     */
    public void installHotFixPatch(String targetVersion, HashMap<String,Pair<Long,InputStream>> patchEntries) throws IOException{
        if(!sCurrentVersionPatchDir.getName().equals(targetVersion)){
            throw new IllegalStateException("mismatch version error");
        }
        if(!sCurrentVersionPatchDir.exists()){
            sCurrentVersionPatchDir.mkdirs();
        }
        if(!sCurrentVersionPatchDir.exists()){
            return;
        }
        File sPatchVersionDir = sCurrentVersionPatchDir;
        if(!sPatchVersionDir.exists()){
            sPatchVersionDir.mkdirs();
        }
        if(!sPatchVersionDir.exists()){
            throw new IOException("crate patch dir fail : "+sPatchVersionDir.getAbsolutePath());
        }
        Iterator iter = patchEntries.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String,Pair<Long,InputStream>> entry = (Map.Entry) iter.next();
            File patchBundleDir = new File(sPatchVersionDir,entry.getKey());
            patchBundleDir.mkdirs();
            if(patchBundleDir.exists()){
                String lockKey = entry.getKey()+".patch";
                try {
                    BundleLock.WriteLock(lockKey);
                    if(entry.getValue().first<0){
                        hotpatchBundles.remove(entry.getKey());
                        continue;
                    }
                    File hotFixFile = new File(patchBundleDir, entry.getValue().first+HOTFIX_NAME_POSTFIX);
                    installDex(entry.getValue().second, hotFixFile);
                    hotpatchBundles.put(entry.getKey(),Long.valueOf(entry.getValue().first));
                    String pkgName = entry.getKey();
                    if (MAIN_DEX_PKG.equals(pkgName)){
                        activePatch(pkgName,new Patch(hotFixFile,RuntimeVariables.androidApplication.getClassLoader()));
                    }else {
                        BundleImpl bundle = (BundleImpl) Atlas.getInstance().getBundle(entry.getKey());
                        if(bundle!=null){
                            Patch p = new Patch(hotFixFile,bundle.getClassLoader());
                            activePatch(entry.getKey(),p);
                        }
                    }
                }catch(Exception e){
                    e.printStackTrace();
                }finally{
                    BundleLock.WriteUnLock(lockKey);
                }
            }
        }
        storePatchInfo();
    }

    /**
     * @return <bundleName,patchVersion>
     */
    public Map<String,Long> getAllInstallPatch(){
        return hotpatchBundles;
    }

    public void setPatchListener(OnPatchActivatedListener listener){
        mPatchListener = listener;
    }

    public void storePatchInfo() throws IOException{
        if(!meta.exists()){
            meta.getParentFile().mkdirs();
            meta.createNewFile();
        }
        if(meta.exists()) {
            try {
                AtlasFileLock.getInstance().LockExclusive(meta);
                StringBuilder bundleList = new StringBuilder("");
                for (Iterator iterator = hotpatchBundles.entrySet().iterator(); iterator.hasNext(); ) {
                    Map.Entry entry = (java.util.Map.Entry) iterator.next();
                    bundleList.append(entry.getKey());
                    bundleList.append("@");
                    bundleList.append(entry.getValue());
                    bundleList.append(";");
                }
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(meta)));
                out.writeUTF(bundleList.toString());
                out.flush();
                IOUtil.quietClose(out);
            }finally {
                AtlasFileLock.getInstance().unLock(meta);
            }
        }
    }

    public void readPatchInfo() throws IOException{
        try {
            AtlasFileLock.getInstance().LockExclusive(meta);
            DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(meta)));
            String bundleListStr = input.readUTF();
            if (!TextUtils.isEmpty(bundleListStr)) {
                String[] bundles = bundleListStr.split(";");
                if (bundles != null && bundles.length > 0) {
                    for (String bundleInfo : bundles) {
                        String[] infoItems = bundleInfo.split("@");
                        hotpatchBundles.put(infoItems[0], Long.parseLong(infoItems[1]));
                    }
                }
            }
            IOUtil.quietClose(input);
        }finally {
            AtlasFileLock.getInstance().LockExclusive(meta);
        }
    }

    private void patchMainDex(){
        if(hotpatchBundles.containsKey(MAIN_DEX_PKG)) {
            final long version = hotpatchBundles.get(MAIN_DEX_PKG);
            File maindexPatchFile = new File(sCurrentVersionPatchDir, MAIN_DEX_PKG+"/" + version + HOTFIX_NAME_POSTFIX);
            if (maindexPatchFile.exists()) {
                purgeOldPatchsOfBundle(maindexPatchFile, version);
                activePatch(MAIN_DEX_PKG, new Patch(maindexPatchFile, RuntimeVariables.androidApplication.getClassLoader()));
            }
        }
    }

    private void patchBundle(final Bundle bundle){
        if(hotpatchBundles.get(bundle.getLocation())==null){
            return;
        }
        String lockKey = bundle.getLocation() + ".patch";
        try {
            BundleLock.WriteLock(lockKey);
            if (activePatchs.get(bundle.getLocation()) != null) {
                return;
            }
            long version = hotpatchBundles.get(bundle.getLocation());
            File bundlePatchFile = new File(sCurrentVersionPatchDir,String.format("%s/%s%s",
                    bundle.getLocation(),version,HOTFIX_NAME_POSTFIX));
            if (bundlePatchFile.exists()) {
                purgeOldPatchsOfBundle(bundlePatchFile,version);
                activePatch(bundle.getLocation(),new Patch(bundlePatchFile,((BundleImpl)bundle).getClassLoader()));
            }
        }catch(Exception e){
            e.printStackTrace();
        }finally {
            BundleLock.WriteUnLock(lockKey);
        }
    }

    private void activePatch(String patchBundleName,Patch patch){
        activePatchs.put(patchBundleName,patch.file.getAbsolutePath());
        try {
            patch.activate();
            if(mPatchListener!=null){
                mPatchListener.onPatchActivated(patchBundleName,patch.file.getAbsolutePath(),hotpatchBundles.get(patchBundleName));
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

    private void purgeOldPatchsByAppVersion(File patchDir, final String currentVersion){
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

    private void purgeOldPatchsOfBundle(final File validPatchFile,final long version){
        if(RuntimeVariables.sCurrentProcessName.equals(RuntimeVariables.androidApplication.getPackageName())) {
            File oldpatchs[] = validPatchFile.getParentFile().listFiles(new FileFilter() {
                @Override
                public boolean accept(File patchFile) {
                    return (patchFile.getName().endsWith(HOTFIX_NAME_POSTFIX) && !patchFile.getName().startsWith(version + ""));
                }
            });
            if (oldpatchs != null && oldpatchs.length > 0) {
                for (File patchFile : oldpatchs) {
                    Patch p = new Patch(patchFile, null);
                    p.purge();
                }
            }
        }
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
            odexFile = new File(file.getParentFile(),patchNameWithoutPostFix+".odex");
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
            if (null == interfaces){
                return false;
            }
            for(Class itf : interfaces){
                if(itf != IAtlasHotPatch.class) {
                    continue;
                }
                Process processAnno =  (Process) clazz.getAnnotation(Process.class);
                String patchProcess = processAnno !=null ? processAnno.value() : RuntimeVariables.androidApplication.getPackageName();
                if(patchProcess.equals(RuntimeVariables.sCurrentProcessName)) {
                    return true;
                }
            }
            return false;
        }

        public void purge(){
            if(odexFile.exists()) {
                odexFile.delete();
            }
            if(file.exists()){
                file.delete();
            }
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
