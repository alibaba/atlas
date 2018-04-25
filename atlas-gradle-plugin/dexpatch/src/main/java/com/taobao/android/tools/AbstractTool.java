package com.taobao.android.tools;

import com.alibaba.fastjson.JSON;
import com.android.utils.ILogger;
import com.google.common.collect.Lists;
import com.taobao.android.inputs.BaseInput;
import com.taobao.android.inputs.TpatchInput;
import com.taobao.android.object.ApkFileList;
import com.taobao.android.object.ArtifactBundleInfo;
import com.taobao.android.object.DiffType;
import com.taobao.android.outputs.PatchFile;
import com.taobao.android.utils.CommandUtils;
import com.taobao.android.utils.SystemUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

/**
 * @author lilong
 * @create 2017-11-03 上午12:07
 */

public abstract class AbstractTool {

    protected ILogger logger;

    public static final String BASE_APK_UNZIP_NAME = "base.apk";
    public static final String NEW_APK_UNZIP_NAME = "new.apk";
    protected static final String DEX_NAME = "classes.dex";
    protected static final String DEX_SUFFIX = ".dex";
    protected static final String CLASSES = "classes";
    protected static final int DEFAULT_API_LEVEL = 19;

    public void setInput(BaseInput input) {
        this.input = input;
    }

    protected BaseInput input;

    public abstract PatchFile doPatch() throws Exception;

    public boolean isModifyBundle(String bundleSoFileName) {

        DiffType diffType = getModifyType(bundleSoFileName);

        if (diffType == DiffType.NONE){
            return false;
        }
        return DiffType.ADD.equals(diffType) || DiffType.MODIFY.equals(diffType);

    }


    public DiffType getModifyType(String bundleSoFileName) {
        for (ArtifactBundleInfo artifactBundleInfo : input.artifactBundleInfos) {
            String packageName = artifactBundleInfo.getPkgName();
            if (null == packageName) {
                return DiffType.NONE;
            }
            String bundleName = "lib" + packageName.replace('.', '_') + ".so";
            if (bundleName.equals(bundleSoFileName)) {
                if (null != logger) {
                    logger.info("[BundleDiffType]" + bundleSoFileName + ":" + artifactBundleInfo.getDiffType());
                }
                return artifactBundleInfo.getDiffType();
            }
        }
        return DiffType.NONE;
    }


    public String getBundleName(String bundleSoFileName) {
        return FilenameUtils.getBaseName(bundleSoFileName.replace("lib", ""));
    }

    public DiffType getMainBundleDiffType() {

        for (ArtifactBundleInfo artifactBundleInfo:input.artifactBundleInfos){
            if(artifactBundleInfo.getMainBundle()){
                return artifactBundleInfo.getDiffType();
            }
        }
        return DiffType.NONE;
    }


    public List<File> getFolderDexFiles(File folder) {
        List<File> dexFiles = Lists.newArrayList();
        File baseDex = new File(folder, DEX_NAME);
        if (baseDex.exists()) {
            dexFiles.add(baseDex);
            // 比较是否存在着多dex
            int dexIndex = 2;
            File newIndexDex = getNextDexFile(folder, dexIndex);
            while (null != newIndexDex && newIndexDex.exists()) {
                dexFiles.add(newIndexDex);
                dexIndex++;
                newIndexDex = getNextDexFile(folder, dexIndex);
            }
        }
        return dexFiles;
    }

    /**
     * unzip 2 apk file
     *
     * @param outPatchDir
     */
    protected File unzipApk(File outPatchDir) throws IOException {
        File unzipFolder = new File(outPatchDir, "unzip");
        if (!unzipFolder.exists()){
            unzipFolder.mkdirs();
        }
        File baseApkUnzipFolder = new File(unzipFolder, BASE_APK_UNZIP_NAME);
        File newApkUnzipFolder = new File(unzipFolder, NEW_APK_UNZIP_NAME);
        CommandUtils.exec(outPatchDir,"unzip "+input.baseApkBo.getApkFile().getAbsolutePath()+" -d "+baseApkUnzipFolder.getAbsolutePath());

        if (input.newApkBo.getApkFile().isDirectory()){
            FileUtils.moveDirectory(input.newApkBo.getApkFile(), newApkUnzipFolder);
        }else {
            CommandUtils.exec(outPatchDir,"unzip "+input.newApkBo.getApkFile().getAbsolutePath()+" -d "+ newApkUnzipFolder.getAbsolutePath());
        }

        return unzipFolder;
    }


    public void downloadTPath(String httpUrl, File saveFile) throws IOException {
        if (!saveFile.exists() || !saveFile.isFile()) {
            downloadFile(httpUrl, saveFile);
        }
    }

    /**
     * http download
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

    public void setLogger(ILogger logger) {
        this.logger = logger;
    }

    public File getNextDexFile(File dexParentFolder, int dexNumber) {
        return new File(dexParentFolder, CLASSES + dexNumber + DEX_SUFFIX);
    }

    public File getNextDexFile(File dexParentFolder, int dexNumber, String dexName) {
        return new File(dexParentFolder, dexName + dexNumber + DEX_SUFFIX);
    }

    public abstract boolean isRetainMainBundleRes();


    /**
     * 获取新版本的apkFileList
     *
     * @return
     */
    public ApkFileList getNewApkFileList() {
        String newApkFileListStr = null;
        try {
            if (null != input.newApkFileList && input.newApkFileList.exists()) {
                newApkFileListStr = FileUtils.readFileToString(input.newApkFileList);
                if (StringUtils.isNoneBlank(newApkFileListStr)) {
                    return JSON.parseObject(newApkFileListStr, ApkFileList.class);
                }
            }
        } catch (IOException e) {
        }

        return null;
    }



    /**
     * get base apkFileList
     *
     * @return
     */
    public ApkFileList getBaseApkFileList() {
        String baseApkFileListStr = null;
        try {
            if (null != ((TpatchInput)input).baseApkFileList && ((TpatchInput)input).baseApkFileList.exists()) {
                baseApkFileListStr = FileUtils.readFileToString(((TpatchInput)input).baseApkFileList);
                if (StringUtils.isNoneBlank(baseApkFileListStr)) {
                    return JSON.parseObject(baseApkFileListStr, ApkFileList.class);
                }
            }
        } catch (IOException e) {
        }

        return null;
    }


}
