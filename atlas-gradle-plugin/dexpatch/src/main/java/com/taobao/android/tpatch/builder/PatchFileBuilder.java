package com.taobao.android.tpatch.builder;

import com.android.utils.ILogger;
import com.taobao.android.BasePatchTool;
import com.taobao.android.TPatchDexTool;
import com.taobao.android.differ.dex.PatchException;
import com.taobao.android.object.BuildPatchInfos;
import com.taobao.android.object.PatchBundleInfo;
import com.taobao.android.object.PatchInfo;
import com.taobao.android.task.ExecutorServicesHelper;
import com.taobao.android.tpatch.model.BundleBO;
import com.taobao.android.tpatch.utils.JarSplitUtils;
import com.taobao.android.tpatch.utils.MD5Util;
import com.taobao.android.tpatch.utils.PathUtils;
import com.taobao.android.utils.ZipUtils;
import com.taobao.common.dexpatcher.DexPatchApplier;
import com.taobao.common.dexpatcher.DexPatchGenerator;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.sql.Date;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;


public class PatchFileBuilder {

    private static final String ROLLBACK_VERSION = "-1";

    private final BuildPatchInfos historyBuildPatchInfos;
    private final PatchInfo currentBuildPatchInfo;
    private final File currentPatchFile;
    private final String baseVersion;
    private final File tPatchTmpFolder;
    private Map<String, File> awbMaps;
    private Map<String, PatchInfo> hisPatchInfos = new HashMap<String, PatchInfo>();
    private List<String>versionList = new ArrayList<>();
    private final File patchsFolder;

    private List<String> noPatchBundles;

    public PatchFileBuilder(BuildPatchInfos historyBuildPatchInfos, File currentPatchFile,
                            PatchInfo currentBuildPatchInfo, Map<String, File> awbMaps, File targetFolder,
                            String baseVersion) {
        if (null == historyBuildPatchInfos) {
            this.historyBuildPatchInfos = new BuildPatchInfos();
        } else {
            this.historyBuildPatchInfos = historyBuildPatchInfos;
        }
        this.currentBuildPatchInfo = currentBuildPatchInfo;
        this.awbMaps = awbMaps;
        this.currentPatchFile = currentPatchFile;
        this.baseVersion = baseVersion;
        this.tPatchTmpFolder = new File(targetFolder.getParentFile(), "tpatch-tmp");
        this.patchsFolder = targetFolder;
    }

    public void setNoPatchBundles(List<String> noPatchBundles) {
        this.noPatchBundles = noPatchBundles;
    }

    /**
     * 创建历史版本的tpatch
     */
    public BuildPatchInfos createHistoryTPatches(boolean diffBundleDex, final ILogger logger) throws PatchException {
        final BuildPatchInfos buildPatchInfos = new BuildPatchInfos();
        List<PatchInfo> patchInfos = historyBuildPatchInfos.getPatches();
        String taskName = "CreateHisPatch";
        ExecutorServicesHelper executorServicesHelper = new ExecutorServicesHelper();
        for (final PatchInfo patchInfo : patchInfos) {
            if (!versionList.isEmpty()&&!versionList.contains(patchInfo.getPatchVersion())){
                continue;
            }
            executorServicesHelper.submitTask(taskName, new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    if (null != logger) {
                        logger.info("[CreateHisPatch]" + patchInfo.getPatchVersion() + "....");
                    }
                    try {
                        hisPatchInfos.put(patchInfo.getPatchVersion(), patchInfo);
                        PatchInfo newPatchInfo = createHisTPatch(patchInfo.getPatchVersion(), logger);
                        buildPatchInfos.getPatches().add(newPatchInfo);
                    } catch (IOException e) {
                        throw new PatchException(e.getMessage(), e);
                    }
                    return true;

                }
            });
        }

        try {
            executorServicesHelper.waitTaskCompleted(taskName);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        executorServicesHelper.stop();

        return buildPatchInfos;
    }

    // 获取主bundle
    private PatchBundleInfo getMainBundleInfo(PatchInfo patchInfo) {
        for (PatchBundleInfo bundleInfo : patchInfo.getBundles()) {
            if (bundleInfo.getMainBundle()) {
                return bundleInfo;
            }
        }
        return null;
    }

    /**
     * 创建指定版本的patch文件
     *
     * @param targetVersion
     */
    private PatchInfo createHisTPatch(String targetVersion, ILogger logger) throws IOException, PatchException {
        PatchInfo hisPatchInfo = hisPatchInfos.get(targetVersion);
        String patchName = "patch-" + currentBuildPatchInfo.getPatchVersion() + "@" + hisPatchInfo.getPatchVersion();
        File destTPathTmpFolder = new File(tPatchTmpFolder, patchName);
        destTPathTmpFolder.mkdirs();
        File curTPatchUnzipFolder = unzipCurTPatchFolder(patchName);
        // 处理awb的更新
        List<BundlePatch> bundlePatches = diffPatch(hisPatchInfo, currentBuildPatchInfo);
        PatchInfo newPatchInfo = processBundlePatch(hisPatchInfo, bundlePatches,curTPatchUnzipFolder);

        // 比对主bundle的信息
        PatchBundleInfo curMainBundleInfo = getMainBundleInfo(currentBuildPatchInfo);
        PatchBundleInfo mainBundleInfo = new PatchBundleInfo();
        mainBundleInfo.setMainBundle(true);
        mainBundleInfo.setNewBundle(false);
        if (null != curMainBundleInfo) {
            File mainBundleFile = new File(curTPatchUnzipFolder, curMainBundleInfo.getName() + ".so");
            FileUtils.copyFileToDirectory(mainBundleFile, destTPathTmpFolder);
            mainBundleInfo.setVersion(curMainBundleInfo.getVersion());
            mainBundleInfo.setPkgName(curMainBundleInfo.getPkgName());
            mainBundleInfo.setBaseVersion(curMainBundleInfo.getBaseVersion());
            mainBundleInfo.setName(curMainBundleInfo.getName());
            mainBundleInfo.setPkgName(curMainBundleInfo.getPkgName());
            mainBundleInfo.setApplicationName(curMainBundleInfo.getApplicationName());
            newPatchInfo.getBundles().add(mainBundleInfo);
        }

        // 生成tpatch文件
        for (PatchBundleInfo bundleInfo : newPatchInfo.getBundles()) {
            if (bundleInfo.getMainBundle() || bundleInfo.getNewBundle() || noPatchBundles.contains(bundleInfo.getPkgName())) {
                File bundleFolder = new File(destTPathTmpFolder, bundleInfo.getName());
                File soFile = new File(destTPathTmpFolder, bundleInfo.getName() + ".so");
                if (soFile.exists() || bundleInfo.getVersion().equals(ROLLBACK_VERSION)) {
                    continue;
                }
                zipBunldeSo(bundleFolder, soFile);
                FileUtils.deleteDirectory(bundleFolder);
            }
        }
        File tPatchFile = new File(patchsFolder, newPatchInfo.getFileName());
        if (tPatchFile.exists()) FileUtils.deleteQuietly(tPatchFile);
        zipBunldeSo(destTPathTmpFolder, tPatchFile);
        if (null != logger) {
            logger.info("[TPatchFile]" + tPatchFile.getAbsolutePath());
        }
        return newPatchInfo;
    }

    /**
     * 生成主dex的so
     *
     * @param bundleFolder
     * @param soOutputFile
     */
    private void zipBunldeSo(File bundleFolder, File soOutputFile) throws PatchException {
        try {
            Manifest manifest = createManifest();
            FileOutputStream fileOutputStream = new FileOutputStream(soOutputFile);
            JarOutputStream jos = new JarOutputStream(new BufferedOutputStream(fileOutputStream), manifest);
            // Add ZIP entry to output stream.
//            jos.setComment(patchVersion+"@"+targetVersion);
            File[] files = bundleFolder.listFiles();
            for (File file : files) {
                if (file.isDirectory()) {
                    addDirectory(jos, file, file.getName());
                } else {
                    addFile(jos, file);
                }
            }
            IOUtils.closeQuietly(jos);
            if (null != fileOutputStream) IOUtils.closeQuietly(fileOutputStream);
        } catch (IOException e) {
            throw new PatchException(e.getMessage(), e);
        }
    }

    /**
     * 比较2个patch版本之间的差异
     *
     * @param hisPatchInfo
     * @param currentPatchInfo
     * @return
     */
    private List<BundlePatch> diffPatch(PatchInfo hisPatchInfo, PatchInfo currentPatchInfo) {
        List<BundlePatch> list = new LinkedList<BundlePatch>();
        Map<String, PatchBundleInfo> hisBundles = toMap(hisPatchInfo.getBundles());
        Map<String, PatchBundleInfo> curBundles = toMap(currentPatchInfo.getBundles());
        for (Map.Entry<String, PatchBundleInfo> entry : curBundles.entrySet()) {
            String bundleName = entry.getKey();
            PatchBundleInfo curBundleInfo = entry.getValue();
            BundlePatch bundlePatch = new BundlePatch();
            bundlePatch.name = curBundleInfo.getName();
            bundlePatch.dependency = curBundleInfo.getDependency();
            bundlePatch.pkgName = curBundleInfo.getPkgName();
            bundlePatch.artifactId = curBundleInfo.getArtifactId();
            bundlePatch.version = curBundleInfo.getVersion();
            bundlePatch.newBundle = curBundleInfo.getNewBundle();
            bundlePatch.hisPatchUrl = hisPatchInfo.getDownloadUrl();
            bundlePatch.mainBundle = curBundleInfo.getMainBundle();
            bundlePatch.baseVersion = curBundleInfo.getBaseVersion();
            if (hisBundles.containsKey(bundleName)&&!hisBundles.get(bundleName).getNewBundle()) { // 如果之前的patch版本也包含这个bundle的patch
                PatchBundleInfo hisBundleInfo = hisBundles.get(bundleName);
                bundlePatch.baseVersion = hisBundleInfo.getVersion();
                if (curBundleInfo.getVersion().equalsIgnoreCase(hisBundleInfo.getVersion())) { // 如果2个patch版本没变化
                    // 说明:为了防止虽然版本号没变化但是文件内容也有变化的情况，直接也做merge操作
                    bundlePatch.bundlePolicy = BundlePolicy.MERGE;
                } else {// 如果版本有变化，进行merge操作
                    bundlePatch.bundlePolicy = BundlePolicy.MERGE;
                }
                hisBundles.remove(bundleName);
            } else { // 如果历史的patch中没有包含该bundle
                bundlePatch.bundlePolicy = BundlePolicy.ADD;
            }
            list.add(bundlePatch);
        }

        // 继续添加在历史版本里有变动的，在新的版本进行回滚的操作
        for (Map.Entry<String, PatchBundleInfo> entry : hisBundles.entrySet()) {
            PatchBundleInfo hisBundleInfo = entry.getValue();
            BundlePatch bundlePatch = new BundlePatch();
            bundlePatch.name = hisBundleInfo.getName();
            bundlePatch.dependency = hisBundleInfo.getDependency();
            bundlePatch.pkgName = hisBundleInfo.getPkgName();
            bundlePatch.artifactId = hisBundleInfo.getArtifactId();
            bundlePatch.bundlePolicy = BundlePolicy.ROLLBACK;
            bundlePatch.version = ROLLBACK_VERSION;
            bundlePatch.baseVersion = hisBundleInfo.getVersion();
            list.add(bundlePatch);
        }
        return list;
    }

    /**
     * 解压当前的tpatch文件
     */

    private File unzipCurTPatchFolder(String patchName) throws IOException {
        File curTPatchUnzipFolder = new File(currentPatchFile.getParentFile(), "curTpatch");
        synchronized (this) {
            if (curTPatchUnzipFolder.exists() && curTPatchUnzipFolder.isDirectory()) {
                return curTPatchUnzipFolder;
            }
            curTPatchUnzipFolder.mkdirs();
            ZipUtils.unzip(currentPatchFile, curTPatchUnzipFolder.getAbsolutePath());
            File[] libs = curTPatchUnzipFolder.listFiles();
            if (libs != null && libs.length > 0) {
                for (File lib : libs) {
                    if (lib.isFile() && lib.getName().endsWith(".so")) {
                        File destFolder = new File(lib.getParentFile(), FilenameUtils.getBaseName(lib.getName()));
                        System.out.println(lib.getAbsolutePath());
                        ZipUtils.unzip(lib, destFolder.getAbsolutePath());
                    }
                }
            }
        }
        return curTPatchUnzipFolder;

    }

    /**
     * 处理各自bundle的patch文件
     *
     * @param hisPatchInfo
     * @param bundlePatchs
     */
    private PatchInfo processBundlePatch(PatchInfo hisPatchInfo, List<BundlePatch> bundlePatchs,File curTPatchUnzipFolder) throws IOException,
            PatchException {
        String patchName = "patch-" + currentBuildPatchInfo.getPatchVersion() + "@" + hisPatchInfo.getPatchVersion();
        PatchInfo patchInfo = new PatchInfo();
        patchInfo.setFileName(patchName + ".tpatch");
        patchInfo.setTargetVersion(hisPatchInfo.getPatchVersion());
        patchInfo.setPatchVersion(currentBuildPatchInfo.getPatchVersion());
        File hisTPatchFile = new File(tPatchTmpFolder, hisPatchInfo.getPatchVersion() + "-download.tpatch");
        File hisTPatchUnzipFolder = new File(tPatchTmpFolder, hisPatchInfo.getPatchVersion());
        File destTPathTmpFolder = new File(tPatchTmpFolder, patchName);
        if (!destTPathTmpFolder.exists()) {
            destTPathTmpFolder.mkdirs();
        }
        for (BundlePatch bundlePatch : bundlePatchs) {
            boolean addToPatch = true;
            String bundleName = "lib" + bundlePatch.pkgName.replace('.', '_');
            if (bundlePatch.mainBundle) {
                continue;
            } else if (noPatchBundles.contains(bundlePatch.pkgName)) {
                File currentBundle = new File(curTPatchUnzipFolder, "lib" + bundlePatch.pkgName.replace(".", "_") + ".so");
                if (!currentBundle.exists()) {
                    continue;
                }
                FileUtils.copyFileToDirectory(currentBundle, destTPathTmpFolder);
                PatchBundleInfo patchBundleInfo = new PatchBundleInfo();
                patchBundleInfo.setApplicationName(bundlePatch.applicationName);
                patchBundleInfo.setArtifactId(bundlePatch.artifactId);
                patchBundleInfo.setMainBundle(false);
                patchBundleInfo.setNewBundle(bundlePatch.newBundle);
                patchBundleInfo.setName(bundleName);
                patchBundleInfo.setPkgName(bundlePatch.pkgName);
                patchBundleInfo.setVersion(bundlePatch.version);
                patchBundleInfo.setBaseVersion(bundlePatch.baseVersion);
                patchBundleInfo.setDependency(bundlePatch.dependency);
                patchInfo.getBundles().add(patchBundleInfo);
                continue;
            }

            File curBundleFolder = new File(curTPatchUnzipFolder, bundleName);
            File bundleDestFolder = new File(destTPathTmpFolder, bundleName);
            PatchBundleInfo patchBundleInfo = new PatchBundleInfo();
            patchBundleInfo.setApplicationName(bundlePatch.applicationName);
            patchBundleInfo.setArtifactId(bundlePatch.artifactId);
            patchBundleInfo.setMainBundle(false);
            patchBundleInfo.setNewBundle(bundlePatch.newBundle);
            patchBundleInfo.setName(bundleName);
            patchBundleInfo.setPkgName(bundlePatch.pkgName);
            patchBundleInfo.setVersion(bundlePatch.version);
            patchBundleInfo.setBaseVersion(bundlePatch.baseVersion);
            patchBundleInfo.setDependency(bundlePatch.dependency);
            switch (bundlePatch.bundlePolicy) {
                case ADD:
                    bundleDestFolder.mkdirs();
                    FileUtils.copyDirectory(curBundleFolder, bundleDestFolder);
                    break;
                case REMOVE:
                    addToPatch = false;
                    break; // donothing
                case ROLLBACK:// donothing
                    break;
                case MERGE:
                    downloadTPathAndUnzip(hisPatchInfo.getDownloadUrl(), hisTPatchFile, hisTPatchUnzipFolder);
                    File hisBundleFolder = new File(hisTPatchUnzipFolder, bundleName);
                    if (!hisBundleFolder.exists()) { //如果历史的文件不存在,就直接覆盖
                        throw new PatchException("The bundle:" + bundleName + " does not existed in tpatch:"
                                + hisPatchInfo.getDownloadUrl());
//                        bundleDestFolder.mkdirs();
//                        FileUtils.copyDirectory(curBundleFolder, bundleDestFolder);
                    } else {
                        File fullAwbFile = awbMaps.get(bundlePatch.artifactId);
                        if (fullAwbFile == null){
                            System.out.println(bundlePatch.artifactId + " is not exits!");
                            FileUtils.copyDirectory(curBundleFolder, bundleDestFolder);
                            break;

                        }
                        copyDiffFiles(fullAwbFile, curBundleFolder, hisBundleFolder, bundleDestFolder);
                        if (!bundleDestFolder.exists()||bundleDestFolder.listFiles().length == 0){
                            addToPatch = false;
                        }
                    }
                    break;
            }
            if (addToPatch) {
                patchInfo.getBundles().add(patchBundleInfo);
            }
        }
        return patchInfo;
    }

    /**
     * 复制拷贝有diff的文件
     *  @param fullLibFile
     * @param curBundleFolder
     * @param hisBundleFolder
     * @param destBundleFolder
     * @param bundleName
     */
    private void copyDiffFiles(File fullLibFile, File curBundleFolder, File hisBundleFolder,
                               File destBundleFolder) throws IOException, PatchException {
        Map<String, FileDef> curBundleFileMap = getListFileMap(curBundleFolder);
        Map<String, FileDef> hisBundleFileMap = getListFileMap(hisBundleFolder);
        Set<String> rollbackFiles = new HashSet<String>();
        // 判断差别
        for (Map.Entry<String, FileDef> entry : curBundleFileMap.entrySet()) {
            String curFilePath = entry.getKey();
            FileDef curFileDef = entry.getValue();

            File destFile = new File(destBundleFolder, curFilePath);
//            if (curFilePath.endsWith(".dex")){
//                createHisBundleDex(curFileDef,hisBundleFileMap.get(curFilePath),destFile,fullLibFile);
//                hisBundleFileMap.remove(curFilePath);
//                continue;
//            }
            if (hisBundleFileMap.containsKey(curFilePath)) {
                FileDef hisFileDef = hisBundleFileMap.get(curFilePath);
                if (curFileDef.md5.equals(hisFileDef.md5)) {
                    // donothing
                } else {
                    FileUtils.copyFile(curFileDef.file, destFile);
                }
                hisBundleFileMap.remove(curFilePath);
            } else { // 如果新的patch里有这个文件
                FileUtils.copyFile(curFileDef.file, destFile);
            }
        }

        for (Map.Entry<String, FileDef> entry : hisBundleFileMap.entrySet()) {
            rollbackFiles.add(entry.getKey());
        }
        if (rollbackFiles.size() > 0) {
            JarSplitUtils.splitZipToFolder(fullLibFile, destBundleFolder, rollbackFiles);
        }
    }



    private void createHisBundleDex(FileDef curFileDef, FileDef fileDef, File destFile, File newBundleFile) throws IOException {
        if (fileDef == null){
            FileUtils.copyFile(curFileDef.file,destFile);
        }else {
            DexPatchApplier dexPatchApplier = new DexPatchApplier(getBaseDexFile(newBundleFile,true),fileDef.file);
            File tempFile = new File(destFile.getParentFile(),"merge.dex");
            dexPatchApplier.executeAndSaveTo(tempFile);
            DexPatchGenerator dexPatchGenerator = new DexPatchGenerator(tempFile,getBaseDexFile(newBundleFile,false));
            dexPatchGenerator.executeAndSaveTo(destFile);
            FileUtils.deleteQuietly(tempFile);

        }
    }

    private File getBaseDexFile(File newBundleFile,boolean base) {
        File newApkUnzipFolder = new File(newBundleFile.getAbsolutePath().split("lib/armeabi")[0]);
        File baseApkUnzipFolder = new File(newApkUnzipFolder.getParentFile(), BasePatchTool.BASE_APK_UNZIP_NAME);
        File baseBundleFile = null;
        File oldBundleFolder = null;
        if (base) {
             baseBundleFile = new File(baseApkUnzipFolder, "lib" + File.separator + "armeabi" + File.separator + newBundleFile.getName());
             oldBundleFolder = new File(baseBundleFile.getParentFile(), FilenameUtils.getBaseName(baseBundleFile.getName()));
            System.out.println("getBaseDexFile:" + new File(oldBundleFolder, "classes.dex").getAbsolutePath());
            return new File(oldBundleFolder, "classes.dex");
        }else {
             baseBundleFile = new File(newApkUnzipFolder, "lib" + File.separator + "armeabi" + File.separator + newBundleFile.getName());
             oldBundleFolder = new File(baseBundleFile.getParentFile(), FilenameUtils.getBaseName(baseBundleFile.getName()));
            System.out.println("getNewDexFile:" + new File(oldBundleFolder, "classes.dex").getAbsolutePath());
            return new File(oldBundleFolder, "classes.dex");
        }
    }

    /**
     * 将指定文件夹下的文件转换为map
     *
     * @param folder
     * @return
     * @throws PatchException
     */
    private Map<String, FileDef> getListFileMap(File folder) throws PatchException, IOException {
        Map<String, FileDef> map = new HashMap<String, FileDef>();
        if (!folder.exists() || !folder.isDirectory()) {
            throw new PatchException("The input folder:" + folder.getAbsolutePath()
                    + " does not existed or is not a directory!");
        }
        Collection<File> files = FileUtils.listFiles(folder, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
        for (File file : files) {
            String path = PathUtils.toRelative(folder, file.getAbsolutePath());
            String md5 = MD5Util.getFileMD5String(file);
            FileDef fileDef = new FileDef(md5, path, file);
            map.put(path, fileDef);
        }
        return map;
    }

    public void setHistroyVersionList(List<String> versionList) {
        this.versionList = versionList;

    }

    // 简易文件定义
    class FileDef {

        String md5;
        String relativePath;
        File file;

        public FileDef(String md5, String relativePath, File file) {
            this.md5 = md5;
            this.relativePath = relativePath;
            this.file = file;
        }
    }

    /**
     * 如果下载的文件不存在，则下载文件
     *
     * @param httpUrl
     * @param saveFile
     * @param tmpUnzipFolder
     * @throws IOException
     */
    private void downloadTPathAndUnzip(String httpUrl, File saveFile, File tmpUnzipFolder) throws IOException {
        if (!saveFile.exists() || !saveFile.isFile()) {
            downloadFile(httpUrl, saveFile);
        }
        ZipUtils.unzip(saveFile, tmpUnzipFolder.getAbsolutePath());
    }

    /**
     * http下载
     */
    private void downloadFile(String httpUrl, File saveFile) throws IOException {
        // 下载网络文件
        int bytesum = 0;
        int byteread = 0;
        URL url = new URL(httpUrl);
        URLConnection conn = url.openConnection();
        InputStream inStream = conn.getInputStream();
        FileOutputStream fs = new FileOutputStream(saveFile);

        byte[] buffer = new byte[1204];
        while ((byteread = inStream.read(buffer)) != -1) {
            bytesum += byteread;
            fs.write(buffer, 0, byteread);
        }
        fs.flush();
        inStream.close();
        fs.close();
    }

    /**
     * 转换为map
     *
     * @param bundles
     * @return
     */
    private Map<String, PatchBundleInfo> toMap(List<PatchBundleInfo> bundles) {
        Map<String, PatchBundleInfo> bundleMap = new HashMap<String, PatchBundleInfo>();
        for (PatchBundleInfo bundle : bundles) {
            bundleMap.put(bundle.getArtifactId(), bundle);
        }
        return bundleMap;
    }

    /**
     * 创建Andfix的manifest信息
     *
     * @return
     */
    private Manifest createManifest() {
        Manifest manifest = new Manifest();
        Attributes main = manifest.getMainAttributes();
        main.putValue("Manifest-Version", "1.0");
        main.putValue("Created-By", "1.0 (JarPatch)");
        main.putValue("Created-Time", new Date(System.currentTimeMillis()).toGMTString());
        return manifest;
    }

    /**
     * 往jar文件里增加文件
     *
     * @param jos
     * @param file
     */
    private void addFile(JarOutputStream jos, File file) throws PatchException {
        byte[] buf = new byte[8064];
        String path = file.getName();
        InputStream in = null;
        try {
            in = new FileInputStream(file);
            ZipEntry fileEntry = new ZipEntry(path);
            jos.putNextEntry(fileEntry);
            // Transfer bytes from the file to the ZIP file
            int len;
            while ((len = in.read(buf)) > 0) {
                jos.write(buf, 0, len);
            }
            // Complete the entry
            jos.closeEntry();
            in.close();
        } catch (IOException e) {
            throw new PatchException(e.getMessage(), e);
        }
    }

    /**
     * Adds a directory to a  with a directory prefix.
     *
     * @param jos       ZipArchiver to use to archive the file.
     * @param directory The directory to add.
     * @param prefix    An optional prefix for where in the Jar file the directory's contents should go.
     */
    protected void addDirectory(JarOutputStream jos, File directory, String prefix) throws PatchException {
        if (directory != null && directory.exists()) {
            Collection<File> files = FileUtils.listFiles(directory, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
            byte[] buf = new byte[8064];
            for (File file : files) {
                if (file.isDirectory()) {
                    continue;
                }
                String path = prefix + File.separator + PathUtils.toRelative(directory, file.getAbsolutePath());
                InputStream in = null;
                try {
                    in = new FileInputStream(file);
                    ZipEntry fileEntry = new ZipEntry(path);
                    jos.putNextEntry(fileEntry);
                    // Transfer bytes from the file to the ZIP file
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        jos.write(buf, 0, len);
                    }
                    // Complete the entry
                    jos.closeEntry();
                    in.close();
                } catch (IOException e) {
                    throw new PatchException(e.getMessage(), e);
                }
            }
        }
    }

    class BundlePatch {

        String pkgName;
        String applicationName;
        List<String> dependency;
        String name;
        String artifactId;
        boolean newBundle;
        BundlePolicy bundlePolicy;
        String version;
        String hisPatchUrl;
        boolean mainBundle;
        String baseVersion;
    }

    /**
     * 表示当前patch的bundle与之前patch版本的策略，包括合并,回滚,无变化
     */
    enum BundlePolicy {
        ADD, MERGE, REMOVE, ROLLBACK;
    }

}
