package com.taobao.android;

/*
 *
 *
 *                                  Apache License
 *                            Version 2.0, January 2004
 *                         http://www.apache.org/licenses/
 *
 *    TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION
 *
 *    1. Definitions.
 *
 *       "License" shall mean the terms and conditions for use, reproduction,
 *       and distribution as defined by Sections 1 through 9 of this document.
 *
 *       "Licensor" shall mean the copyright owner or entity authorized by
 *       the copyright owner that is granting the License.
 *
 *       "Legal Entity" shall mean the union of the acting entity and all
 *       other entities that control, are controlled by, or are under common
 *       control with that entity. For the purposes of this definition,
 *       "control" means (i) the power, direct or indirect, to cause the
 *       direction or management of such entity, whether by contract or
 *       otherwise, or (ii) ownership of fifty percent (50%) or more of the
 *       outstanding shares, or (iii) beneficial ownership of such entity.
 *
 *       "You" (or "Your") shall mean an individual or Legal Entity
 *       exercising permissions granted by this License.
 *
 *       "Source" form shall mean the preferred form for making modifications,
 *       including but not limited to software source code, documentation
 *       source, and configuration files.
 *
 *       "Object" form shall mean any form resulting from mechanical
 *       transformation or translation of a Source form, including but
 *       not limited to compiled object code, generated documentation,
 *       and conversions to other media types.
 *
 *       "Work" shall mean the work of authorship, whether in Source or
 *       Object form, made available under the License, as indicated by a
 *       copyright notice that is included in or attached to the work
 *       (an example is provided in the Appendix below).
 *
 *       "Derivative Works" shall mean any work, whether in Source or Object
 *       form, that is based on (or derived from) the Work and for which the
 *       editorial revisions, annotations, elaborations, or other modifications
 *       represent, as a whole, an original work of authorship. For the purposes
 *       of this License, Derivative Works shall not include works that remain
 *       separable from, or merely link (or bind by name) to the interfaces of,
 *       the Work and Derivative Works thereof.
 *
 *       "Contribution" shall mean any work of authorship, including
 *       the original version of the Work and any modifications or additions
 *       to that Work or Derivative Works thereof, that is intentionally
 *       submitted to Licensor for inclusion in the Work by the copyright owner
 *       or by an individual or Legal Entity authorized to submit on behalf of
 *       the copyright owner. For the purposes of this definition, "submitted"
 *       means any form of electronic, verbal, or written communication sent
 *       to the Licensor or its representatives, including but not limited to
 *       communication on electronic mailing lists, source code control systems,
 *       and issue tracking systems that are managed by, or on behalf of, the
 *       Licensor for the purpose of discussing and improving the Work, but
 *       excluding communication that is conspicuously marked or otherwise
 *       designated in writing by the copyright owner as "Not a Contribution."
 *
 *       "Contributor" shall mean Licensor and any individual or Legal Entity
 *       on behalf of whom a Contribution has been received by Licensor and
 *       subsequently incorporated within the Work.
 *
 *    2. Grant of Copyright License. Subject to the terms and conditions of
 *       this License, each Contributor hereby grants to You a perpetual,
 *       worldwide, non-exclusive, no-charge, royalty-free, irrevocable
 *       copyright license to reproduce, prepare Derivative Works of,
 *       publicly display, publicly perform, sublicense, and distribute the
 *       Work and such Derivative Works in Source or Object form.
 *
 *    3. Grant of Patent License. Subject to the terms and conditions of
 *       this License, each Contributor hereby grants to You a perpetual,
 *       worldwide, non-exclusive, no-charge, royalty-free, irrevocable
 *       (except as stated in this section) patent license to make, have made,
 *       use, offer to sell, sell, import, and otherwise transfer the Work,
 *       where such license applies only to those patent claims licensable
 *       by such Contributor that are necessarily infringed by their
 *       Contribution(s) alone or by combination of their Contribution(s)
 *       with the Work to which such Contribution(s) was submitted. If You
 *       institute patent litigation against any entity (including a
 *       cross-claim or counterclaim in a lawsuit) alleging that the Work
 *       or a Contribution incorporated within the Work constitutes direct
 *       or contributory patent infringement, then any patent licenses
 *       granted to You under this License for that Work shall terminate
 *       as of the date such litigation is filed.
 *
 *    4. Redistribution. You may reproduce and distribute copies of the
 *       Work or Derivative Works thereof in any medium, with or without
 *       modifications, and in Source or Object form, provided that You
 *       meet the following conditions:
 *
 *       (a) You must give any other recipients of the Work or
 *           Derivative Works a copy of this License; and
 *
 *       (b) You must cause any modified files to carry prominent notices
 *           stating that You changed the files; and
 *
 *       (c) You must retain, in the Source form of any Derivative Works
 *           that You distribute, all copyright, patent, trademark, and
 *           attribution notices from the Source form of the Work,
 *           excluding those notices that do not pertain to any part of
 *           the Derivative Works; and
 *
 *       (d) If the Work includes a "NOTICE" text file as part of its
 *           distribution, then any Derivative Works that You distribute must
 *           include a readable copy of the attribution notices contained
 *           within such NOTICE file, excluding those notices that do not
 *           pertain to any part of the Derivative Works, in at least one
 *           of the following places: within a NOTICE text file distributed
 *           as part of the Derivative Works; within the Source form or
 *           documentation, if provided along with the Derivative Works; or,
 *           within a display generated by the Derivative Works, if and
 *           wherever such third-party notices normally appear. The contents
 *           of the NOTICE file are for informational purposes only and
 *           do not modify the License. You may add Your own attribution
 *           notices within Derivative Works that You distribute, alongside
 *           or as an addendum to the NOTICE text from the Work, provided
 *           that such additional attribution notices cannot be construed
 *           as modifying the License.
 *
 *       You may add Your own copyright statement to Your modifications and
 *       may provide additional or different license terms and conditions
 *       for use, reproduction, or distribution of Your modifications, or
 *       for any such Derivative Works as a whole, provided Your use,
 *       reproduction, and distribution of the Work otherwise complies with
 *       the conditions stated in this License.
 *
 *    5. Submission of Contributions. Unless You explicitly state otherwise,
 *       any Contribution intentionally submitted for inclusion in the Work
 *       by You to the Licensor shall be under the terms and conditions of
 *       this License, without any additional terms or conditions.
 *       Notwithstanding the above, nothing herein shall supersede or modify
 *       the terms of any separate license agreement you may have executed
 *       with Licensor regarding such Contributions.
 *
 *    6. Trademarks. This License does not grant permission to use the trade
 *       names, trademarks, service marks, or product names of the Licensor,
 *       except as required for reasonable and customary use in describing the
 *       origin of the Work and reproducing the content of the NOTICE file.
 *
 *    7. Disclaimer of Warranty. Unless required by applicable law or
 *       agreed to in writing, Licensor provides the Work (and each
 *       Contributor provides its Contributions) on an "AS IS" BASIS,
 *       WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *       implied, including, without limitation, any warranties or conditions
 *       of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A
 *       PARTICULAR PURPOSE. You are solely responsible for determining the
 *       appropriateness of using or redistributing the Work and assume any
 *       risks associated with Your exercise of permissions under this License.
 *
 *    8. Limitation of Liability. In no event and under no legal theory,
 *       whether in tort (including negligence), contract, or otherwise,
 *       unless required by applicable law (such as deliberate and grossly
 *       negligent acts) or agreed to in writing, shall any Contributor be
 *       liable to You for damages, including any direct, indirect, special,
 *       incidental, or consequential damages of any character arising as a
 *       result of this License or out of the use or inability to use the
 *       Work (including but not limited to damages for loss of goodwill,
 *       work stoppage, computer failure or malfunction, or any and all
 *       other commercial damages or losses), even if such Contributor
 *       has been advised of the possibility of such damages.
 *
 *    9. Accepting Warranty or Additional Liability. While redistributing
 *       the Work or Derivative Works thereof, You may choose to offer,
 *       and charge a fee for, acceptance of support, warranty, indemnity,
 *       or other liability obligations and/or rights consistent with this
 *       License. However, in accepting such obligations, You may act only
 *       on Your own behalf and on Your sole responsibility, not on behalf
 *       of any other Contributor, and only if You agree to indemnify,
 *       defend, and hold each Contributor harmless for any liability
 *       incurred by, or claims asserted against, such Contributor by reason
 *       of your accepting any such warranty or additional liability.
 *
 *    END OF TERMS AND CONDITIONS
 *
 *    APPENDIX: How to apply the Apache License to your work.
 *
 *       To apply the Apache License to your work, attach the following
 *       boilerplate notice, with the fields enclosed by brackets "[]"
 *       replaced with your own identifying information. (Don't include
 *       the brackets!)  The text should be enclosed in the appropriate
 *       comment syntax for the file format. We also recommend that a
 *       file or class name and description of purpose be included on the
 *       same "printed page" as the copyright notice for easier
 *       identification within third-party archives.
 *
 *    Copyright 2016 Alibaba Group
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 *
 */

import com.alibaba.fastjson.JSON;
import com.android.utils.Pair;
import com.google.common.collect.Lists;
import com.google.common.io.LineReader;
import com.taobao.android.apatch.ApkPatch;
import com.taobao.android.apatch.utils.TypeGenUtil;
import com.taobao.android.differ.dex.ApkDiff;
import com.taobao.android.differ.dex.BundleDiffResult;
import com.taobao.android.differ.dex.PatchException;
import com.taobao.android.dx.merge.CollisionPolicy;
import com.taobao.android.dx.merge.DexMerger;
import com.taobao.android.object.ApkFileList;
import com.taobao.android.object.ArtifactBundleInfo;
import com.taobao.android.object.BuildPatchInfos;
import com.taobao.android.object.DexDiffInfo;
import com.taobao.android.object.DiffType;
import com.taobao.android.object.PatchBundleInfo;
import com.taobao.android.object.PatchInfo;
import com.taobao.android.smali.AfBakSmali;
import com.taobao.android.smali.SmaliMod;
import com.taobao.android.tpatch.manifest.AndroidManifestDiffFactory;
import com.taobao.android.task.ExecutorServicesHelper;
import com.taobao.android.tpatch.builder.PatchFileBuilder;
import com.taobao.android.tpatch.model.ApkBO;
import com.taobao.android.tpatch.model.BundleBO;
import com.taobao.android.tpatch.utils.HttpClientUtils;
import com.taobao.android.tpatch.utils.MD5Util;
import com.taobao.android.tpatch.utils.PatchUtils;
import com.taobao.android.tpatch.utils.PathUtils;
import com.taobao.android.utils.PathMatcher;
import com.taobao.android.utils.SmaliCodeUtils;
import com.taobao.android.utils.ZipUtils;

import com.taobao.common.dexpatcher.DexPatchApplier;
import com.taobao.common.dexpatcher.DexPatchGenerator;
import com.taobao.dex.Dex;
import com.taobao.update.UpdateInfo;
import org.antlr.runtime.RecognitionException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.jf.baksmali.baksmaliOptions;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.util.SyntheticAccessorResolver;
import org.jf.dexlib2.writer.builder.DexBuilder;
import org.jf.dexlib2.writer.io.FileDataStore;
import org.jf.util.ClassFileNameHandler;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.sql.Date;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 生成为atlas做动态部署的diff工具类
 * <p/>
 * TODO: 需要支持指定文件的过滤，包括dex文件，资源文件。不对指定文件做diff操作
 * Created by shenghua.nish on 2016-03-19 下午9:42.
 */
public class TPatchTool extends BasePatchTool {
    public static boolean isTpatch = false;

    public static boolean debug;

    // 是否对awb的bundle进行增量的dex
    private boolean diffBundleDex = true;

    private boolean retainMainBundleRes = true;

    public static String pName;

    private ApkDiff apkDiff = new ApkDiff();

    private ApkDiff apkPatchInfos = new ApkDiff();


    private List<BundleDiffResult> bundleDiffResults = Collections.synchronizedList(new ArrayList<>());

    private List<BundleDiffResult> patchInfos = Collections.synchronizedList(new ArrayList<>());

    private final PathMatcher pathMatcher = new PathMatcher();

    private final String ANDROID_MANIFEST = "AndroidManifest.xml";


    // 不进入patch包的主bundle资源的资料列表,dex,lib将做另外的对比
    private static final String[] DEFAULT_NOT_INCLUDE_RESOURCES = new String[]{"*.dex",
            "lib/**",
            "META-INF/**"};

    private String[] notIncludeFiles;

    private String mainBundleName = "libcom_taobao_maindex";

    protected File baseApkFileList;

    public void setCreateAll(boolean createAll) {
        this.createAll = createAll;
    }

    private boolean createAll = false;

    protected File newApkFileList;

    public static File hisTpatchFolder;

    private boolean hasMainBundle;

    private List<String> noPatchBundles = Lists.newArrayList();

    private List<String>whiteList = new ArrayList<>();

    public void setVersionList(List<String> versionList) {
        this.versionList = versionList;
    }

    private List<String>versionList = new ArrayList<>();

    private Map<String, Map<String, ClassDef>> bundleClassMap = new ConcurrentHashMap<String, Map<String, ClassDef>>();

    public void setNoPatchBundles(List<String> noPatchBundles) {
        if (!noPatchBundles.isEmpty()) {
            this.noPatchBundles.addAll(noPatchBundles);
        }
    }

    public TPatchTool(ApkBO baseApkBO, ApkBO newApkBO, boolean diffBundleDex) {
        super(baseApkBO, newApkBO);
        this.diffBundleDex = diffBundleDex;
    }

    public TPatchTool(ApkBO baseApkBO, ApkBO newApkBO) {
        super(baseApkBO, newApkBO);
    }

    /**
     * 是否保留主bundle的资源文件（不包括so文件，so文件会进行增量对比)，如果支持atals的主bundle的资源动态更新，这个值应该为true
     *
     * @param retainMainBundleRes
     */
    public void setRetainMainBundleRes(boolean retainMainBundleRes) {
        this.retainMainBundleRes = retainMainBundleRes;
    }

    public boolean isRetainMainBundleRes() {
        return retainMainBundleRes;
    }

    public void setMainBundleName(String mainBundleName) {
        this.mainBundleName = mainBundleName;
    }

    /**
     * 设置不需要在patch包里出现的资源的列表
     *
     * @param notIncludeFiles
     */
    public void setNotIncludeFiles(String[] notIncludeFiles) {
        this.notIncludeFiles = notIncludeFiles;
    }

    public void setBaseApkFileList(File baseApkFileList) {
        this.baseApkFileList = baseApkFileList;
    }

    public void setNewApkFileList(File newApkFileList) {
        this.newApkFileList = newApkFileList;
    }

    /**
     * 生成patch文件
     *
     * @param outPatchDir        生成patch文件的目录
     * @param createPatchJson
     * @param outPatchJson
     * @param createHistoryPatch
     * @param patchHistoryUrl
     * @param productName
     * @return 得到最后的patch的地址
     */
    public File doPatch(File outPatchDir,
                        boolean createPatchJson,
                        File outPatchJson,
                        boolean createHistoryPatch,
                        String patchHistoryUrl,
                        String productName) throws Exception {
        isTpatch = true;
        pName = productName;
         hisTpatchFolder = new File(outPatchDir.getParentFile().getParentFile().getParentFile().getParentFile(),"hisTpatch");
         System.out.println();hisTpatchFolder.getAbsolutePath();
        final File diffTxtFile = new File(outPatchDir, "diff.json");
        final File patchInfoFile = new File(outPatchDir, "patchInfo.json");
        final File patchTmpDir = new File(outPatchDir, "tpatch-tmp");
        File mainDiffFolder = new File(patchTmpDir, mainBundleName);
//        FileUtils.cleanDirectory(outPatchDir);
        patchTmpDir.mkdirs();
        FileUtils.cleanDirectory(patchTmpDir);
        mainDiffFolder.mkdirs();
        File lastPatchFile = null;
        readWhiteList(outPatchDir.getParentFile().getParentFile().getParentFile());
        lastPatchFile = getLastPatchFile(baseApkBO.getVersionName(), productName, outPatchDir);
        PatchUtils.getTpatchClassDef(lastPatchFile, bundleClassMap);

        // 解压apk
        File unzipFolder = unzipApk(outPatchDir);
        final File newApkUnzipFolder = new File(unzipFolder, NEW_APK_UNZIP_NAME);
        final File baseApkUnzipFolder = new File(unzipFolder, BASE_APK_UNZIP_NAME);

        // 得到主bundle的dex diff文件
        File mianDiffDestDex = new File(mainDiffFolder, DEX_NAME);
        File tmpDexFile = new File(patchTmpDir, mainBundleName + "-dex");
        createBundleDexPatch(newApkUnzipFolder,
                baseApkUnzipFolder,
                mianDiffDestDex,
                tmpDexFile,
                true);

        // 是否保留主bundle的资源文件
        if (isRetainMainBundleRes()) {
            copyMainBundleResources(newApkUnzipFolder,
                    baseApkUnzipFolder,
                    new File(patchTmpDir, mainBundleName));
        }

        ExecutorServicesHelper executorServicesHelper = new ExecutorServicesHelper();
        String taskName = "diffBundleTask";
        // 判断主bundle的so和awb的插件
        Collection<File> soFiles = FileUtils.listFiles(newApkUnzipFolder, new String[]{"so"}, true);
        if (splitDiffBundle != null) {
            for (Pair<BundleBO, BundleBO> bundle : splitDiffBundle) {
                processBundleFiles(bundle.getSecond().getBundleFile(), bundle.getFirst().getBundleFile(), patchTmpDir);

            }
        }
        for (final File soFile : soFiles) {
            final String relativePath = PathUtils.toRelative(newApkUnzipFolder,
                    soFile.getAbsolutePath());
            if (null != notIncludeFiles && pathMatcher.match(notIncludeFiles, relativePath)) {
                continue;
            }
            executorServicesHelper.submitTask(taskName, new Callable<Boolean>() {

                @Override
                public Boolean call() throws Exception {
                    File destFile = new File(patchTmpDir, mainBundleName + "/" +
                            relativePath);
                    File baseSoFile = new File(baseApkUnzipFolder, relativePath);
                    if (whiteList.contains(soFile.getName())){
                        FileUtils.copyFile(soFile, destFile);
                    }else if (PatchUtils.isBundleFile(soFile)) { // 如果是bundle文件
                        processBundleFiles(soFile, baseSoFile, patchTmpDir);
                    } else {
                        if (isFileModify(soFile, baseSoFile)) {
                            FileUtils.copyFile(soFile, destFile);
                        }
                    }
                    return true;
                }
            });
        }

        executorServicesHelper.waitTaskCompleted(taskName);

        executorServicesHelper.stop();
        // 压缩patch文件夹，得到tpatch文件
        File patchFile = createTPatchFile(outPatchDir, patchTmpDir);

        PatchInfo curPatchInfo = createBasePatchInfo(patchFile);
        BuildPatchInfos buildPatchInfos = null;
        // 生成多版本的tpatch文件
            buildPatchInfos = createIncrementPatchFiles(productName,
                    patchFile,
                    outPatchDir,
                    newApkUnzipFolder,
                    curPatchInfo,
                    patchHistoryUrl);


        buildPatchInfos.getPatches().add(curPatchInfo);
        buildPatchInfos.setBaseVersion(baseApkBO.getVersionName());
        buildPatchInfos.setDiffBundleDex(diffBundleDex);

        if (createPatchJson) {
            FileUtils.writeStringToFile(outPatchJson, JSON.toJSONString(buildPatchInfos));
        }

        for (PatchInfo patchInfo:buildPatchInfos.getPatches()) {
            UpdateInfo updateInfo = new UpdateInfo(patchInfo,buildPatchInfos.getBaseVersion());
            File updateJson = new File(outPatchDir, "update-"+patchInfo.getTargetVersion()+".json");
            FileUtils.writeStringToFile(updateJson, JSON.toJSONString(updateInfo, true));
        }

        // 删除临时的目录
        FileUtils.deleteDirectory(patchTmpDir);
        apkDiff.setBaseApkVersion(baseApkBO.getVersionName());
        apkDiff.setNewApkVersion(newApkBO.getVersionName());
        apkDiff.setBundleDiffResults(bundleDiffResults);
        apkDiff.setNewApkMd5(MD5Util.getFileMD5String(newApkBO.getApkFile()));
        apkDiff.setFileName(newApkBO.getApkName());
        apkPatchInfos.setBaseApkVersion(baseApkBO.getVersionName());
        apkPatchInfos.setNewApkVersion(newApkBO.getVersionName());
        apkPatchInfos.setBundleDiffResults(patchInfos);
        apkPatchInfos.setFileName(patchFile.getName());
        apkPatchInfos.setNewApkMd5(MD5Util.getFileMD5String(patchFile));
        FileUtils.writeStringToFile(diffTxtFile, JSON.toJSONString(apkDiff));
        FileUtils.writeStringToFile(patchInfoFile, JSON.toJSONString(apkPatchInfos));
        FileUtils.copyFileToDirectory(diffTxtFile, outPatchDir.getParentFile(), true);
        FileUtils.copyFileToDirectory(newApkBO.getApkFile(), outPatchDir.getParentFile(), true);
//        FileUtils.deleteDirectory(unzipFolder);
        return patchFile;
    }

    private void readWhiteList(File parentFile) throws Exception {
        File whiteListFile = new File(parentFile,"DiffWhiteList.txt");
        if (whiteListFile.exists()){
            BufferedReader br = null;
                br = new BufferedReader(new InputStreamReader(new FileInputStream(whiteListFile),
                        "UTF-8"));
            String lineTxt = null;
            while ((lineTxt = br.readLine()) != null) {
                    whiteList.add(lineTxt);
                }
            br.close();
        }
    }


    private File createTPatchFile(File outPatchDir, File patchTmpDir) throws IOException {
        // 首先压缩主bundle,先判断主bundle里有没有文件
        File mainBundleFoder = new File(patchTmpDir, mainBundleName);
        File mainBundleFile = new File(patchTmpDir, mainBundleName + ".so");
        if (FileUtils.listFiles(mainBundleFoder, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE)
                .size() > 0) {
            hasMainBundle = true;
            zipBundle(mainBundleFoder, mainBundleFile);
        }
        FileUtils.deleteDirectory(mainBundleFoder);

        // 再压缩各自的bundle
        File patchFile = new File(outPatchDir,
                "patch-" + newApkBO.getVersionName() + "@" + baseApkBO.getVersionName() + ".tpatch");
        if (patchFile.exists()) {
            FileUtils.deleteQuietly(patchFile);
        }
        zipBundle(patchTmpDir, patchFile);
        FileUtils.deleteDirectory(patchTmpDir);
        return patchFile;
    }

    /**
     * 将一个文件夹转换为so
     *
     * @param toZipFolder
     * @param soOutputFile
     */
    private void zipBundle(File toZipFolder, File soOutputFile) throws IOException {
        FileOutputStream fileOutputStream = null;
        JarOutputStream jos = null;
        try {
            // 生成so文件
            Manifest manifest = createManifest();
            fileOutputStream = new FileOutputStream(soOutputFile);
            jos = new JarOutputStream(new BufferedOutputStream(fileOutputStream), manifest);
            jos.setLevel(9);
            //            jos.setComment(baseApkVersion+"@"+newApkVersion);
            // Add ZIP entry to output stream.
            File[] files = toZipFolder.listFiles();
            for (File file : files) {
                if (file.isDirectory()) {
                    addDirectory(jos, file, file.getName());
                } else {
                    addFile(jos, file);
                }
            }
        } finally {
            IOUtils.closeQuietly(jos);
            if (null != fileOutputStream) {
                IOUtils.closeQuietly(fileOutputStream);
            }
        }
    }

    /**
     * 处理bundle的patch文件
     *
     * @param newBundleFile
     * @param baseBundleFile
     * @param patchTmpDir
     * @param diffTxtFile
     */
    private void processBundleFiles(File newBundleFile,
                                    File baseBundleFile,
                                    File patchTmpDir) throws IOException, RecognitionException, PatchException {
        String bundleName = FilenameUtils.getBaseName(newBundleFile.getName());
        File destPatchBundleDir = new File(patchTmpDir, bundleName);
        final File newBundleUnzipFolder = new File(newBundleFile.getParentFile(), bundleName);
        final File baseBundleUnzipFolder = new File(baseBundleFile.getParentFile(), bundleName);

        if (null != baseBundleFile &&
                baseBundleFile.isFile() &&
                baseBundleFile.exists() &&
                !noPatchBundles.contains(baseBundleFile.getName()
                        .replace("_", ".")
                        .substring(3,
                                baseBundleFile.getName().length() -
                                        3)) &&
                diffBundleDex) {
            // 解压文件
            // 判断dex的差异性
            ZipUtils.unzip(newBundleFile, newBundleUnzipFolder.getAbsolutePath());
            ZipUtils.unzip(baseBundleFile, baseBundleUnzipFolder.getAbsolutePath());
            File destDex = new File(destPatchBundleDir, DEX_NAME);
            File tmpDexFolder = new File(patchTmpDir, bundleName + "-dex");
            createBundleDexPatch(newBundleUnzipFolder,
                    baseBundleUnzipFolder,
                    destDex,
                    tmpDexFolder,
                    false);

            // 比较其他资源文件的差异性
            Collection<File> newBundleResFiles = FileUtils.listFiles(newBundleUnzipFolder,
                    new IOFileFilter() {

                        @Override
                        public boolean accept(File file) {
                            // 不包括dex文件
                            if (file.getName()
                                    .endsWith(
                                            ".dex")) {
                                return false;
                            }
                            String relativePath = PathUtils
                                    .toRelative(
                                            newBundleUnzipFolder,
                                            file.getAbsolutePath());
                            if (null !=
                                    notIncludeFiles &&
                                    pathMatcher.match(
                                            notIncludeFiles,
                                            relativePath)) {
                                return false;
                            }
                            return true;
                        }

                        @Override
                        public boolean accept(File file,
                                              String s) {
                            return accept(new File(
                                    file,
                                    s));
                        }
                    },
                    TrueFileFilter.INSTANCE);

            for (File newBundleResFile : newBundleResFiles) {
                String resPath = PathUtils.toRelative(newBundleUnzipFolder,
                        newBundleResFile.getAbsolutePath());
                File baseBundleResFile = new File(baseBundleUnzipFolder, resPath);
                File destResFile = new File(destPatchBundleDir, resPath);
                if (baseBundleResFile.exists()) {
                    if (isFileModify(newBundleResFile,
                            baseBundleResFile,
                            bundleName,
                            resPath)) { // 修改的资源
                        FileUtils.copyFile(newBundleResFile, destResFile);
                    }
                } else {// 新增的资源
                    FileUtils.copyFile(newBundleResFile, destResFile);
                }
            }
        } else { // 新增的bundle，直接全量解压
            FileUtils.copyFileToDirectory(newBundleFile, patchTmpDir);
        }
        if (!isModifyBundle(newBundleFile.getName()) && !createAll) {
            FileUtils.deleteDirectory(destPatchBundleDir);
            Iterator<BundleDiffResult> iterator = patchInfos.iterator();
            while (iterator.hasNext()) {
                BundleDiffResult bundleDiffResult = iterator.next();
                if (bundleDiffResult.getBundleName().equals(bundleName.substring(3).replace("_", "."))) {
                    iterator.remove();
                }
            }
        }
    }

    /**
     * 复制主bundle的增量资源
     *
     * @param newApkUnzipFolder
     * @param baseApkUnzipFolder
     * @param patchTmpDir
     * @throws IOException
     */
    private void copyMainBundleResources(final File newApkUnzipFolder,
                                         final File baseApkUnzipFolder,
                                         File patchTmpDir) throws IOException {
        boolean resoureModified = false;

        Collection<File> retainFiles = FileUtils.listFiles(newApkUnzipFolder, new IOFileFilter() {

            @Override
            public boolean accept(File file) {
                String relativePath = PathUtils.toRelative(newApkUnzipFolder,
                        file.getAbsolutePath());
                if (pathMatcher.match(DEFAULT_NOT_INCLUDE_RESOURCES, relativePath)) {
                    return false;
                }
                if (null != notIncludeFiles && pathMatcher.match(notIncludeFiles, relativePath)) {
                    return false;
                }
                return true;
            }

            @Override
            public boolean accept(File file, String s) {
                return accept(new File(file, s));
            }
        }, TrueFileFilter.INSTANCE);

        for (File retainFile : retainFiles) {
            String relativePath = PathUtils.toRelative(newApkUnzipFolder,
                    retainFile.getAbsolutePath());
            File baseFile = new File(baseApkUnzipFolder, relativePath);
            if (isFileModify(retainFile, baseFile)) {
                resoureModified = true;
                File destFile = new File(patchTmpDir, relativePath);
                FileUtils.copyFile(retainFile, destFile);
            }
        }
        if (resoureModified) {
            File AndroidMenifestFile = new File(newApkUnzipFolder, ANDROID_MANIFEST);
            FileUtils.copyFileToDirectory(AndroidMenifestFile, patchTmpDir);
        }
    }

    /**
     * 得到bundle的dex diff文件，支持多dex
     *
     * @param newApkUnzipFolder
     * @param baseApkUnzipFolder
     * @param destDex
     * @param tmpDexFile
     * @param diffTxtFile
     * @return
     * @throws IOException
     * @throws RecognitionException
     */
    private File createBundleDexPatch(File newApkUnzipFolder,
                                      File baseApkUnzipFolder,
                                      File destDex,
                                      File tmpDexFile,
                                      boolean mainDex) throws IOException, RecognitionException, PatchException {
        List<File> dexs = Lists.newArrayList();
        // 比较主bundle的dex
        if (!tmpDexFile.exists()) {
            tmpDexFile.mkdirs();
        }
        List<File> baseDexFiles = getFolderDexFiles(baseApkUnzipFolder);
        List<File> newDexFiles = getFolderDexFiles(newApkUnzipFolder);
        File dexDiffFile = new File(tmpDexFile, "diff.dex");
        TPatchDexTool dexTool = new TPatchDexTool(baseDexFiles,
                newDexFiles,
                DEFAULT_API_LEVEL,
                bundleClassMap.get(tmpDexFile.getName().substring(0,
                        tmpDexFile.getName()
                                .length() -
                                4)),
        mainDex);
        DexDiffInfo dexDiffInfo = dexTool.createTPatchDex(dexDiffFile);
        if (dexDiffFile.exists()) {
            dexs.add(dexDiffFile);
            BundleDiffResult bundleDiffResult = new BundleDiffResult();
            if (mainDex) {
                bundleDiffResult.setBundleName("com.taobao.maindex");

            } else {
                bundleDiffResult.setBundleName(baseApkUnzipFolder.getName().substring(3).replace("_", "."));
            }
            bundleDiffResults.add(bundleDiffResult);
            patchInfos.add(bundleDiffResult);
            dexDiffInfo.save(bundleDiffResult);
        }
        if (dexs.size() > 0) {
            FileUtils.copyFile(dexs.get(0), destDex);
        }

        FileUtils.deleteDirectory(tmpDexFile);
        return destDex;
    }


    /**
     * 获取基准patch包的patchInfo对象
     *
     * @param fileName
     * @return
     */
    public PatchInfo createBasePatchInfo(File file) {
        PatchInfo patchInfo = new PatchInfo();
        patchInfo.setPatchVersion(newApkBO.getVersionName());
        patchInfo.setTargetVersion(baseApkBO.getVersionName());
        patchInfo.setFileName(file.getName());
        Set<String> modifyBundles = new HashSet<>();
        ZipFile zipFile = newZipFile(file);
        Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
        while (enumeration.hasMoreElements()) {
            ZipEntry zipEntry = enumeration.nextElement();
            if (zipEntry.getName().startsWith("lib") && zipEntry.getName().indexOf("/") != -1) {
                modifyBundles.add(zipEntry.getName().substring(3, zipEntry.getName().indexOf("/")).replace("_", "."));
            } else if (zipEntry.getName().endsWith(".so") && zipEntry.getName().indexOf("/") == -1) {
                modifyBundles.add(zipEntry.getName().substring(3, zipEntry.getName().lastIndexOf(".")).replace("_", "."));
            }

        }

        for (ArtifactBundleInfo artifactBundleInfo : artifactBundleInfos) {
            if (artifactBundleInfo.getMainBundle()) {
                if (DiffType.MODIFY.equals(artifactBundleInfo.getDiffType()) || hasMainBundle) {
                    PatchBundleInfo patchBundleInfo = new PatchBundleInfo();
                    patchBundleInfo.setNewBundle(DiffType.ADD.equals(artifactBundleInfo.getDiffType()));
                    patchBundleInfo.setMainBundle(true);
                    patchBundleInfo.setVersion(artifactBundleInfo.getVersion());
                    patchBundleInfo.setName(mainBundleName);
                    patchBundleInfo.setSrcUnitTag(artifactBundleInfo.getSrcUnitTag());
                    patchBundleInfo.setUnitTag(artifactBundleInfo.getUnitTag());
                    patchBundleInfo.setApplicationName(artifactBundleInfo.getApplicationName());
                    patchBundleInfo.setArtifactId(artifactBundleInfo.getArtifactId());
                    patchBundleInfo.setPkgName(artifactBundleInfo.getPkgName());
                    patchBundleInfo.setDependency(artifactBundleInfo.getDependency());
                    patchBundleInfo.setBaseVersion(artifactBundleInfo.getBaseVersion());
                    patchInfo.getBundles().add(patchBundleInfo);
                    continue;
                }
            } else if (DiffType.MODIFY.equals(artifactBundleInfo.getDiffType()) ||
                    DiffType.ADD.equals(artifactBundleInfo.getDiffType())) {
                PatchBundleInfo patchBundleInfo = new PatchBundleInfo();
                patchBundleInfo.setNewBundle(DiffType.ADD.equals(artifactBundleInfo.getDiffType()));
                patchBundleInfo.setMainBundle(false);
                patchBundleInfo.setSrcUnitTag(artifactBundleInfo.getSrcUnitTag());
                patchBundleInfo.setUnitTag(artifactBundleInfo.getUnitTag());
                patchBundleInfo.setVersion(artifactBundleInfo.getVersion());
                patchBundleInfo.setName(artifactBundleInfo.getName());
                patchBundleInfo.setApplicationName(artifactBundleInfo.getApplicationName());
                patchBundleInfo.setArtifactId(artifactBundleInfo.getArtifactId());
                patchBundleInfo.setPkgName(artifactBundleInfo.getPkgName());
                patchBundleInfo.setDependency(artifactBundleInfo.getDependency());
                patchBundleInfo.setBaseVersion(artifactBundleInfo.getBaseVersion());
                patchInfo.getBundles().add(patchBundleInfo);
            } else if (modifyBundles.contains(artifactBundleInfo.getPkgName())) {
                PatchBundleInfo patchBundleInfo = new PatchBundleInfo();
                patchBundleInfo.setNewBundle(false);
                patchBundleInfo.setMainBundle(false);
                patchBundleInfo.setSrcUnitTag(artifactBundleInfo.getSrcUnitTag());
                patchBundleInfo.setUnitTag(artifactBundleInfo.getUnitTag());
                patchBundleInfo.setVersion(artifactBundleInfo.getVersion());
                patchBundleInfo.setName(artifactBundleInfo.getName());
                patchBundleInfo.setApplicationName(artifactBundleInfo.getApplicationName());
                patchBundleInfo.setArtifactId(artifactBundleInfo.getArtifactId());
                patchBundleInfo.setPkgName(artifactBundleInfo.getPkgName());
                patchBundleInfo.setDependency(artifactBundleInfo.getDependency());
                patchBundleInfo.setBaseVersion(artifactBundleInfo.getBaseVersion());
                patchInfo.getBundles().add(patchBundleInfo);
            }
        }

        try {
            zipFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return patchInfo;
    }

    private ZipFile newZipFile(File file) {
        try {
            return new ZipFile(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 生成增量的patch文件
     */
    private BuildPatchInfos createIncrementPatchFiles(String productionName,
                                                      File curTPatchFile,
                                                      File targetDirectory,
                                                      File newApkUnzipFolder,
                                                      PatchInfo curPatchInfo,
                                                      String patchHistoryUrl) throws IOException, PatchException {
        BuildPatchInfos historyBuildPatchInfos = null;
        String response = null;
        if (!StringUtils.isEmpty(patchHistoryUrl)) {
            String patchHisUrl = patchHistoryUrl +
                    "?baseVersion=" +
                    baseApkBO.getVersionName() +
                    "&productIdentifier=" +
                    productionName;
              response = HttpClientUtils.getUrl(patchHisUrl);

        }else {
            File localPatchInfo = new File(hisTpatchFolder,"patchs.json");
            if (localPatchInfo.exists()) {
                response = FileUtils.readFileToString(localPatchInfo);
            }

        }
        historyBuildPatchInfos = JSON.parseObject(response, BuildPatchInfos.class);


        Map<String, File> awbBundleMap = new HashMap<String, File>();
        for (ArtifactBundleInfo artifactBundleInfo : artifactBundleInfos) {
            String bundleFileSoName = "lib" +
                    artifactBundleInfo.getPkgName().replace('.', '_') +
                    ".so";
            File bundleFile = new File(newApkUnzipFolder,
                    "lib" +
                            "/" +
                            "armeabi" +
                            "/" +
                            bundleFileSoName);
            if (bundleFile.exists()) {
                awbBundleMap.put(artifactBundleInfo.getArtifactId(), bundleFile);
            }
        }
        PatchFileBuilder patchFileBuilder = new PatchFileBuilder(historyBuildPatchInfos,
                curTPatchFile,
                curPatchInfo,
                awbBundleMap,
                targetDirectory,
                baseApkBO.getVersionName());
        patchFileBuilder.setNoPatchBundles(noPatchBundles);
        patchFileBuilder.setHistroyVersionList(versionList);

        return patchFileBuilder.createHistoryTPatches(diffBundleDex, logger);
    }

    /**
     * 创建tpatch的manifest信息
     *
     * @return
     */
    private Manifest createManifest() {
        Manifest manifest = new Manifest();
        Attributes main = manifest.getMainAttributes();
        main.putValue("Manifest-Version", "1.0");
        main.putValue("Created-By", "1.0 (DexPatch)");
        main.putValue("Created-Time", new Date(System.currentTimeMillis()).toGMTString());
        return manifest;
    }

    /**
     * 判断2个文件是否修改过
     *
     * @param newFile  新的文件,不能为空
     * @param baseFile
     * @return
     */
    private synchronized boolean isFileModify(File newFile, File baseFile) throws IOException {
        if (null == baseFile || !baseFile.exists()) {
            return true;
        }

        String newFileMd5 = MD5Util.getFileMD5String(newFile);
        String baseFileMd5 = MD5Util.getFileMD5String(baseFile);
        if (StringUtils.equals(newFileMd5, baseFileMd5)) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * 判断2个文件是否变动过
     *
     * @param newFile
     * @param baseFile
     * @param bundleFileName
     * @param filePath
     * @return
     * @throws IOException
     */
    private synchronized boolean isFileModify(File newFile,
                                              File baseFile,
                                              String bundleFileName,
                                              String filePath) throws IOException {
        if (null == baseFile || !baseFile.exists()) {
            return true;
        }

        String newFileMd5 = MD5Util.getFileMD5String(newFile);
        String baseFileMd5 = MD5Util.getFileMD5String(baseFile);
        newFileMd5 = getBundleFileMappingMd5(getNewApkFileList(),
                bundleFileName,
                filePath,
                newFileMd5);
        baseFileMd5 = getBundleFileMappingMd5(getBaseApkFileList(),
                bundleFileName,
                filePath,
                baseFileMd5);
        if (StringUtils.equals(newFileMd5, baseFileMd5)) {
            return false;
        } else if (newFile.getName().equals(ANDROID_MANIFEST)) {
            return isManifestModify(baseFile, newFile);

        } else {
            return true;
        }
    }

    private boolean isManifestModify(File baseFile, File newFile) {
        AndroidManifestDiffFactory androidManifestDiffFactory = new AndroidManifestDiffFactory();
        try {
            androidManifestDiffFactory.diff(baseFile, newFile);
            for (AndroidManifestDiffFactory.DiffItem diffItem : androidManifestDiffFactory.diffResuit) {
                if (diffItem.Component instanceof com.taobao.android.tpatch.manifest.Manifest.Activity ||
                        diffItem.Component instanceof com.taobao.android.tpatch.manifest.Manifest.Service) {
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 从apkFileList对象中获取之前映射的md5
     *
     * @param apkFileList
     * @param bundleFileName,如果为null,则表示是主bundle
     * @param filePath
     * @param curMd5                             当前这个文件的md5值
     * @return
     */
    private String getBundleFileMappingMd5(ApkFileList apkFileList,
                                           String bundleFileName,
                                           String filePath,
                                           String curMd5) {
        if (null == apkFileList) {
            return curMd5;
        }
        String bundleName = null;
        if (null != bundleFileName) {
            bundleName = getBundleName(bundleFileName);
            if (null != bundleName) {
                String mappingMd5 = apkFileList.getAwbFile(bundleName, filePath);
                if (null != mappingMd5) {
                    return mappingMd5;
                }
            }
        } else { // 主bundle
            String mappingMd5 = apkFileList.getMainBundle().get(filePath);
            if (null != mappingMd5) {
                return mappingMd5;
            }
        }
        return curMd5;
    }

    /**
     * 往jar文件里增加文件
     *
     * @param jos
     * @param file
     */
    private void addFile(JarOutputStream jos, File file) throws IOException {
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
        } finally {
            IOUtils.closeQuietly(in);
        }
    }

    /**
     * Adds a directory to a {@link} with a directory prefix.
     *
     * @param jos       ZipArchiver to use to archive the file.
     * @param directory The directory to add.
     * @param prefix    An optional prefix for where in the Jar file the directory's contents should go.
     */
    protected void addDirectory(JarOutputStream jos,
                                File directory,
                                String prefix) throws IOException {
        if (directory != null && directory.exists()) {
            Collection<File> files = FileUtils.listFiles(directory,
                    TrueFileFilter.INSTANCE,
                    TrueFileFilter.INSTANCE);
            byte[] buf = new byte[8064];
            for (File file : files) {
                if (file.isDirectory()) {
                    continue;
                }
                String path = prefix +
                        "/" +
                        PathUtils.toRelative(directory, file.getAbsolutePath());
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
                } finally {
                    IOUtils.closeQuietly(in);
                }
            }
        }
    }

    /**
     * 获取新版本的apkFileList
     *
     * @return
     */
    public ApkFileList getNewApkFileList() {
        String newApkFileListStr = null;
        try {
            if (null != newApkFileList && newApkFileList.exists()) {
                newApkFileListStr = FileUtils.readFileToString(newApkFileList);
                if (StringUtils.isNoneBlank(newApkFileListStr)) {
                    return JSON.parseObject(newApkFileListStr, ApkFileList.class);
                }
            }
        } catch (IOException e) {
        }

        return null;
    }

    /**
     * 获取基线版本的apkFileList
     *
     * @return
     */
    public ApkFileList getBaseApkFileList() {
        String baseApkFileListStr = null;
        try {
            if (null != baseApkFileList && baseApkFileList.exists()) {
                baseApkFileListStr = FileUtils.readFileToString(baseApkFileList);
                if (StringUtils.isNoneBlank(baseApkFileListStr)) {
                    return JSON.parseObject(baseApkFileListStr, ApkFileList.class);
                }
            }
        } catch (IOException e) {
        }

        return null;
    }



    public static void main(String[] args) throws Exception {
//        File file = new File("/Users/lilong/Downloads/tpatch-diff/lib/armeabi/libcom_alibaba_wdk_txd/classes.dex");
        File file1 = new File("/Users/lilong/Downloads/10004583@taobao_android_6.6.0/lib/armeabi/libcom_etao_feimagesearch/classes.dex");
        Dex dex = new Dex(file1);
//        dex.getTableOfContents().typeLists.size
//       TPatchDexTool.removeDebugInfo(file);
//        TPatchDexTool.removeDebugInfo(file1);
//        DexPatchGenerator dexPatchGenerator = new DexPatchGenerator(file1,file);
//        dexPatchGenerator.executeAndSaveTo(new File("/Users/lilong/Downloads/1.dex"));
//        DexPatchApplier dexPatchApplier = new DexPatchApplier(file1,new File("/Users/lilong/Downloads/1.dex"));
//        dexPatchApplier.executeAndSaveTo(new File("/Users/lilong/Downloads/2.dex"));


    }



}
