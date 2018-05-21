package com.taobao.android.tpatch.builder;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import com.android.utils.ILogger;
import com.taobao.android.tools.TPatchTool;
import com.taobao.android.differ.dex.PatchException;
import com.taobao.android.object.BuildPatchInfos;
import com.taobao.android.object.PatchBundleInfo;
import com.taobao.android.object.PatchInfo;
import com.taobao.android.reader.*;
import com.taobao.android.tpatch.utils.JarSplitUtils;
import com.taobao.android.tpatch.utils.MD5Util;
import com.taobao.android.tpatch.utils.PathUtils;
import com.taobao.android.utils.CommandUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.GradleException;

public class PatchFileBuilder {

    private static final String ROLLBACK_VERSION = "-1";

    private final BuildPatchInfos historyBuildPatchInfos;
    private final PatchInfo currentBuildPatchInfo;
    private final File currentPatchFile;
    private final String baseVersion;
    private final File tPatchTmpFolder;
    private Map<String, File> awbMaps;
    private Map<String, PatchInfo> hisPatchInfos = new HashMap<String, PatchInfo>();
    private List<String> versionList = new ArrayList<>();
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
     * create history tpatch
     */
    public BuildPatchInfos createHistoryTPatches(boolean diffBundleDex, final ILogger logger) throws PatchException {
        final BuildPatchInfos buildPatchInfos = new BuildPatchInfos();
        List<PatchInfo> patchInfos = historyBuildPatchInfos.getPatches();

        patchInfos.parallelStream().filter(new java.util.function.Predicate<PatchInfo>() {
            @Override
            public boolean test(PatchInfo patchInfo) {
                if (!versionList.isEmpty() && !versionList.contains(patchInfo.getPatchVersion())) {
                    return false;
                }
                return true;
            }
        }).forEach(new Consumer<PatchInfo>() {
            @Override
            public void accept(PatchInfo patchInfo) {
                if (null != logger) {
                    logger.info("[CreateHisPatch]" + patchInfo.getPatchVersion() + "....");
                }
                hisPatchInfos.put(patchInfo.getPatchVersion(), patchInfo);
                PatchInfo newPatchInfo = null;
                try {
                    newPatchInfo = createHisTPatch(patchInfo.getPatchVersion(), logger);
                } catch (Exception e) {
                    throw new GradleException(e.getMessage(), e);
                }

                synchronized (buildPatchInfos) {
                    buildPatchInfos.getPatches().add(newPatchInfo);
                }

            }
        });

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
     * create patch for target version
     *
     * @param targetVersion
     */
    private PatchInfo createHisTPatch(String targetVersion, ILogger logger) throws IOException, PatchException {
        PatchInfo hisPatchInfo = hisPatchInfos.get(targetVersion);
        String patchName = "patch-" + currentBuildPatchInfo.getPatchVersion() + "@" + hisPatchInfo.getPatchVersion();
        File destTPathTmpFolder = new File(tPatchTmpFolder, patchName);
        destTPathTmpFolder.mkdirs();
        File infoFile = new File(destTPathTmpFolder,"patchInfo");
        FileUtils.writeStringToFile(infoFile, patchName);
        File curTPatchUnzipFolder = unzipCurTPatchFolder(patchName);
        // 处理awb的更新
        List<BundlePatch> bundlePatches = diffPatch(hisPatchInfo, currentBuildPatchInfo);
        PatchInfo newPatchInfo = processBundlePatch(hisPatchInfo, bundlePatches, curTPatchUnzipFolder);

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
            mainBundleInfo.setUnitTag(curMainBundleInfo.getUnitTag());
            mainBundleInfo.setSrcUnitTag(curMainBundleInfo.getSrcUnitTag());
            mainBundleInfo.setName(curMainBundleInfo.getName());
            mainBundleInfo.setPkgName(curMainBundleInfo.getPkgName());
            mainBundleInfo.setApplicationName(curMainBundleInfo.getApplicationName());
            newPatchInfo.getBundles().add(mainBundleInfo);
        }

        // 生成tpatch文件
        for (PatchBundleInfo bundleInfo : newPatchInfo.getBundles()) {
            if (bundleInfo.getMainBundle() || bundleInfo.getNewBundle() || noPatchBundles.contains(
                bundleInfo.getPkgName())) {
                File bundleFolder = new File(destTPathTmpFolder, bundleInfo.getName());
                File soFile = new File(destTPathTmpFolder, bundleInfo.getName() + ".so");
                if (soFile.exists() || bundleInfo.getVersion().equals(ROLLBACK_VERSION)) {
                    continue;
                }
                CommandUtils.exec(bundleFolder, "zip -r " + soFile.getAbsolutePath() + " . -x */ -x .*");
                //                zipBunldeSo(bundleFolder, soFile);
                FileUtils.deleteDirectory(bundleFolder);
            }
        }
        File tPatchFile = new File(patchsFolder, newPatchInfo.getFileName());
        if (tPatchFile.exists()) { FileUtils.deleteQuietly(tPatchFile); }
        CommandUtils.exec(destTPathTmpFolder, "zip -r " + tPatchFile.getAbsolutePath() + " . -x */ -x .*");
        if (null != logger) {
            logger.info("[TPatchFile]" + tPatchFile.getAbsolutePath());
        }
        return newPatchInfo;
    }

    /**
     * generate main dex so
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
            if (null != fileOutputStream) { IOUtils.closeQuietly(fileOutputStream); }
        } catch (IOException e) {
            throw new PatchException(e.getMessage(), e);
        }
    }

    /**
     * compare two patch
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
            bundlePatch.unitTag = curBundleInfo.getUnitTag();
            bundlePatch.version = curBundleInfo.getVersion();
            //            bundlePatch.srcUnitTag = curBundleInfo.getSrcUnitTag();
            bundlePatch.newBundle = curBundleInfo.getNewBundle();
            bundlePatch.hisPatchUrl = hisPatchInfo.getDownloadUrl();
            bundlePatch.mainBundle = curBundleInfo.getMainBundle();
            //            bundlePatch.baseVersion = curBundleInfo.getBaseVersion();
            if (hisBundles.containsKey(bundleName) && !hisBundles.get(bundleName)
                .getNewBundle()) { // 如果之前的patch版本也包含这个bundle的patch
                PatchBundleInfo hisBundleInfo = hisBundles.get(bundleName);
                bundlePatch.baseVersion = hisBundleInfo.getVersion();
                bundlePatch.srcUnitTag = hisBundleInfo.getUnitTag();
                if (curBundleInfo.getVersion().equalsIgnoreCase(hisBundleInfo.getVersion())) { // 如果2个patch版本没变化
                    // 说明:为了防止虽然版本号没变化但是文件内容也有变化的情况，直接也做merge操作
                    bundlePatch.bundlePolicy = BundlePolicy.MERGE;
                } else {// 如果版本有变化，进行merge操作
                    bundlePatch.bundlePolicy = BundlePolicy.MERGE;
                }
                hisBundles.remove(bundleName);
            } else { // 如果历史的patch中没有包含该bundle
                bundlePatch.bundlePolicy = BundlePolicy.ADD;
                bundlePatch.srcUnitTag = curBundleInfo.getSrcUnitTag();
            }
            list.add(bundlePatch);
        }

        // 继续添加在历史版本里有变动的，在新的版本进行回滚的操作
        for (Map.Entry<String, PatchBundleInfo> entry : hisBundles.entrySet()) {
            PatchBundleInfo hisBundleInfo = entry.getValue();
            BundlePatch bundlePatch = new BundlePatch();
            bundlePatch.name = hisBundleInfo.getName();
            bundlePatch.unitTag = hisBundleInfo.getUnitTag();
            bundlePatch.srcUnitTag = hisBundleInfo.getSrcUnitTag();
            bundlePatch.dependency = hisBundleInfo.getDependency();
            bundlePatch.pkgName = hisBundleInfo.getPkgName();
            bundlePatch.artifactId = hisBundleInfo.getArtifactId();
            bundlePatch.bundlePolicy = BundlePolicy.ROLLBACK;
            bundlePatch.version = ROLLBACK_VERSION;
            bundlePatch.reset = true;
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
            CommandUtils.exec(tPatchTmpFolder,
                              "unzip " + currentPatchFile.getAbsolutePath() + " -d " + curTPatchUnzipFolder
                                  .getAbsolutePath());
            //            ZipUtils.unzip(currentPatchFile, curTPatchUnzipFolder.getAbsolutePath());
            File[] libs = curTPatchUnzipFolder.listFiles();
            if (libs != null && libs.length > 0) {
                for (File lib : libs) {
                    if (lib.isFile() && lib.getName().endsWith(".so")) {
                        File destFolder = new File(lib.getParentFile(), FilenameUtils.getBaseName(lib.getName()));
                        System.out.println(lib.getAbsolutePath());
                        CommandUtils.exec(tPatchTmpFolder,
                                          "unzip " + lib.getAbsolutePath() + " -d " + destFolder.getAbsolutePath());
                        //                        ZipUtils.unzip(lib, destFolder.getAbsolutePath());
                    }
                }
            }
        }
        return curTPatchUnzipFolder;

    }

    /**
     * process so file
     *
     * @param hisPatchInfo
     * @param bundlePatchs
     */
    private PatchInfo processBundlePatch(PatchInfo hisPatchInfo, List<BundlePatch> bundlePatchs,
                                         File curTPatchUnzipFolder) throws IOException,
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
                File currentBundle = new File(curTPatchUnzipFolder,
                                              "lib" + bundlePatch.pkgName.replace(".", "_") + ".so");
                if (!currentBundle.exists()) {
                    continue;
                }
                FileUtils.copyFileToDirectory(currentBundle, destTPathTmpFolder);
                PatchBundleInfo patchBundleInfo = new PatchBundleInfo();
                patchBundleInfo.setApplicationName(bundlePatch.applicationName);
                patchBundleInfo.setArtifactId(bundlePatch.artifactId);
                patchBundleInfo.setMainBundle(false);
                patchBundleInfo.setSrcUnitTag(bundlePatch.srcUnitTag);
                patchBundleInfo.setUnitTag(bundlePatch.unitTag);
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
            patchBundleInfo.setSrcUnitTag(bundlePatch.srcUnitTag);
            patchBundleInfo.setMainBundle(false);
            patchBundleInfo.setUnitTag(bundlePatch.unitTag);
            patchBundleInfo.setReset(bundlePatch.reset);
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
                    File hisBundleFolder = new File(hisTPatchUnzipFolder, bundleName);
                    if (!hisTPatchFile.exists()) {
                        if (StringUtils.isBlank(hisPatchInfo.getDownloadUrl()) && new File(TPatchTool.hisTpatchFolder,
                                                                                           hisPatchInfo.getFileName())
                            .exists()) {
                            File hisPatchFile = new File(TPatchTool.hisTpatchFolder, hisPatchInfo.getFileName());
                            System.out.println("hisPatchFile:" + hisPatchFile.getAbsolutePath());
                            if (hisPatchFile.exists()) {
                                FileUtils.copyFile(new File(TPatchTool.hisTpatchFolder, hisPatchInfo.getFileName()),
                                                   hisTPatchFile);
                                CommandUtils.exec(tPatchTmpFolder,
                                                  "unzip " + hisPatchFile + " -d " + hisTPatchUnzipFolder
                                                      .getAbsolutePath());
                                //                            ZipUtils.unzip(hisTPatchFile, hisTPatchUnzipFolder
                                // .getAbsolutePath());
                            }
                        } else {
                            downloadTPathAndUnzip(hisPatchInfo.getDownloadUrl(), hisTPatchFile, hisTPatchUnzipFolder);
//                            File mainDexFile = new File(hisTPatchUnzipFolder,"libcom_taobao_maindex.so");
//                            if (mainDexFile.exists()){
//                                try {
//                                    System.out.println("start put bundleInfos for version:"+hisPatchInfo.getPatchVersion()+"......");
//                                    TPatchTool.bundleInfos.put(hisPatchInfo.getPatchVersion(),new AtlasFrameworkPropertiesReader(
//                                                                                                new MethodReader(
//                                                                                                new ClassReader(
//                                                                                                new DexReader(mainDexFile))),TPatchTool.bundleInfos.get(currentBuildPatchInfo.getPatchVersion())).read("Landroid/taobao/atlas/framework/FrameworkProperties;","<clinit>"));
//                                } catch (Exception e) {
//                                    e.printStackTrace();
//                                }
//                            }
                        }
                    }
                    if (!hisBundleFolder.exists()) {
                        throw new IOException(hisBundleFolder.getAbsolutePath() + " is not exist in history bundle!");
                    } else {
                        File fullAwbFile = awbMaps.get(bundlePatch.artifactId);
                        if (fullAwbFile == null) {
                            System.out.println(bundlePatch.artifactId + " is not exits!");
                            FileUtils.copyDirectory(curBundleFolder, bundleDestFolder);
                            break;

                        }
                        copyDiffFiles(fullAwbFile, curBundleFolder, hisBundleFolder, bundleDestFolder,patchBundleInfo.getSrcUnitTag().equals(patchBundleInfo.getUnitTag()));
                        if (!bundleDestFolder.exists() || FileUtils.listFiles(bundleDestFolder,null,true).size() == 0) {
                            if (patchBundleInfo.getUnitTag().equals(patchBundleInfo.getSrcUnitTag())) {
                                addToPatch = false;
                            }else {
//                                throw new PatchException(patchName+"patch中:"+patchBundleInfo.getPkgName()+"的srcunittag和unittag不一致,"+patchBundleInfo.getUnitTag()+","+patchBundleInfo.getSrcUnitTag()+"但是无任何变更,无法动态部署，请重新集成!");
                                patchBundleInfo.setInherit(true);
                            }
                        }
                    }
                    break;
            }

            if (addToPatch&&patchBundleInfo.getUnitTag().equals(patchBundleInfo.getSrcUnitTag())){

                throw new PatchException(patchName+"patch中:"+patchBundleInfo.getPkgName()+"的srcunittag和unittag一致,"+patchBundleInfo.getUnitTag()+",无法动态部署，请重新集成!"
                    +"\n检查是否修改了bundle的版本号，参见排查文档：https://alibaba.github.io/atlas/faq/dynamic_failed_help.html");

            }else if (addToPatch) {
                patchInfo.getBundles().add(patchBundleInfo);
            }
        }
        return patchInfo;
    }

    /**
     * 复制拷贝有diff的文件
     *
     * @param fullLibFile
     * @param curBundleFolder
     * @param hisBundleFolder
     * @param destBundleFolder
     * @param bundleName
     */
    private void copyDiffFiles(File fullLibFile, File curBundleFolder, File hisBundleFolder,
                               File destBundleFolder,boolean equalUnitTag) throws IOException, PatchException {
        Map<String, FileDef> curBundleFileMap = getListFileMap(curBundleFolder);
        Map<String, FileDef> hisBundleFileMap = getListFileMap(hisBundleFolder);
        Set<String> rollbackFiles = new HashSet<String>();
        // 判断差别
        for (Map.Entry<String, FileDef> entry : curBundleFileMap.entrySet()) {
            String curFilePath = entry.getKey();
            FileDef curFileDef = entry.getValue();
            if (curFileDef.file.getName().endsWith("abc_wb_textfield_cdf.jpg")&&equalUnitTag){
                hisBundleFileMap.remove(curFilePath);
                continue;
            }

            File destFile = new File(destBundleFolder, curFilePath);
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
     * download file
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
        //        ZipUtils.unzip(saveFile, tmpUnzipFolder.getAbsolutePath());
        CommandUtils.exec(tPatchTmpFolder,
                          "unzip " + saveFile.getAbsolutePath() + " -d " + tmpUnzipFolder.getAbsolutePath());
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
        String unitTag;
        String srcUnitTag;
        boolean reset;
    }

    /**
     * 表示当前patch的bundle与之前patch版本的策略，包括合并,回滚,无变化
     */
    enum BundlePolicy {
        ADD,
        MERGE,
        REMOVE,
        ROLLBACK;
    }

}
