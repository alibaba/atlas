package com.taobao.android.builder.tools.process;

import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.builder.core.AndroidBuilder;
import com.android.builder.model.BuildType;
import com.android.builder.model.SigningConfig;
import com.taobao.android.builder.dependency.model.AwbBundle;
import com.taobao.android.builder.extension.TBuildType;
import com.taobao.android.builder.tools.BuildHelper;
import com.taobao.android.builder.tools.bundleinfo.DynamicBundleInfo;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * ApkProcessor
 *
 * @author zhayu.ll
 * @date 18/1/10
 * @time 下午4:43
 * @description  
 */
public interface ApkProcessor {


    File securitySignApk(File dexFile,File androidManifestFile,TBuildType tBuildType,boolean bundle);

    String uploadBundle(Project project,File apkFile, AwbBundle awbBundle, TBuildType buildType);

     String uploadNativeSo(Project project,File apkFile, TBuildType buildType);


        void removeBundle(AppVariantOutputContext appVariantOutputContext,AwbBundle awbBundle,File bundleFile);

    List<DynamicBundleInfo> generateAllBundleInfo(Collection<AwbBundle> awbBundles);

    void signApk(File apkFile, SigningConfig signingConfig);

    File zipAlignApk(Project project,File apkFile , AndroidBuilder androidBuilder,TBuildType buildType);


    public abstract class DefaultApkProcessor implements ApkProcessor{

        @Override
        public File securitySignApk(File dexFile, File androidManifestFile,TBuildType tBuildType,boolean bundle){
            return null;

        }

        @Override
        public String uploadBundle(Project project,File apkFile,AwbBundle awbBundle,TBuildType buildType) {
            return null;
        }

        @Override
        public String uploadNativeSo(Project project, File apkFile, TBuildType buildType) {
            return null;
        }

        @Override
        public void removeBundle(AppVariantOutputContext appVariantOutputContext, AwbBundle awbBundle, File bundleFile) {
            File outofApkLocation = appVariantOutputContext.getAwbPackageOutAppOutputFile(
                    awbBundle);
            awbBundle.outputBundleFile = outofApkLocation;

            if (outofApkLocation.exists()) {
                outofApkLocation.delete();
            }
            if (!outofApkLocation.getParentFile().exists()) {
                outofApkLocation.getParentFile().mkdirs();
            }
            bundleFile.renameTo(outofApkLocation);
            //add to ap
            appVariantOutputContext.appBuildInfo.getOtherFilesMap().put(
                    "remotebundles/" + outofApkLocation.getName(), outofApkLocation);
        }

        @Override
        public List<DynamicBundleInfo> generateAllBundleInfo(Collection<AwbBundle> awbBundles) {
            List<DynamicBundleInfo> bundleInfoList = new ArrayList<>();
            if (awbBundles == null){
                return bundleInfoList;
            }

            for (AwbBundle awbBundle : awbBundles) {
                DynamicBundleInfo dynamicBundleInfo = new DynamicBundleInfo();
                if (awbBundle.isRemote && !awbBundle.isMBundle) {
                    dynamicBundleInfo.url = awbBundle.bundleInfo.getUrl();
                }
                dynamicBundleInfo.md5 = awbBundle.bundleInfo.getMd5();
                dynamicBundleInfo.size = awbBundle.bundleInfo.getSize();
                dynamicBundleInfo.name = awbBundle.getPackageName();
                bundleInfoList.add(dynamicBundleInfo);

            }
            return bundleInfoList;
        }

        @Override
        public void signApk(File apkFile, SigningConfig signingConfig) {

        }

        @Override
        public File zipAlignApk(Project project,File apkFile, AndroidBuilder androidBuilder,TBuildType buildType) {
            File zipSignApk = BuildHelper.doZipAlign(androidBuilder, project,apkFile);
            return zipSignApk;

        }
    }

    public static class AtlasApkProcessor extends DefaultApkProcessor{

        @Override
        public void removeBundle(AppVariantOutputContext appVariantOutputContext, AwbBundle awbBundle, File bundleFile) {
            if (awbBundle.isRemote && !awbBundle.isMBundle) {
                super.removeBundle(appVariantOutputContext, awbBundle, bundleFile);
            }
        }
    }

}
