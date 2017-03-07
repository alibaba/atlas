//package com.taobao.android.builder.tools.bundleinfo;
//
//import com.alibaba.fastjson.JSON;
//import com.alibaba.fastjson.TypeReference;
//import com.android.builder.dependency.LibraryBundle;
//import com.android.builder.dependency.ManifestDependency;
//import com.taobao.android.builder.dependency.AwbBundle;
//import com.taobao.android.builder.tools.MD5Util;
//import org.apache.commons.io.FileUtils;
//import org.dom4j.DocumentException;
//import org.gradle.api.GradleException;
//
//import java.io.File;
//import java.io.IOException;
//import java.util.*;
//
///**
// * BundleInfo的工具类
// * Created by shenghua.nish on 2016-04-12 下午9:20.
// */
//public class BundleInfoTools {
//
//    private Map<String, LibraryBundle> awbLibMap = new HashMap<String, LibraryBundle>();
//
//    private Map<String, MuppBundleInfo> bundleInfoMap = new HashMap<String, MuppBundleInfo>();
//    private Map<String, LibraryBundle> awbLibMap = new HashMap<String, LibraryBundle>();
//
//    /**
//     * Bunlde的基本信息
//     */
//    private File bundleBaseInfoFile;
//    private String apkVersion;
//    private List<AwbBundle> awbLibs;
//
//    public BundleInfoTools(File bundleBaseInfoFile, String apkVersion,
//                           List<AwbBundle> awbLibs) {
//        this.bundleBaseInfoFile = bundleBaseInfoFile;
//        this.apkVersion = apkVersion;
//        this.awbLibs = awbLibs;
//    }
//
//    /**
//     * 生成bundle Info
//     */
//    public Map<String, MuppBundleInfo> build() {
//        try {
//            initBaseBundleInfo();
//            initAwbBundleInfo();
//            return bundleInfoMap;
//        } catch (DocumentException e) {
//            throw new GradleException(e.getMessage(), e);
//        } catch (IOException e) {
//            throw new GradleException(e.getMessage(), e);
//        }
//    }
//
//
//    /**
//     * 得到当前bundle的AndroidManifest.xml
//     *
//     * @param libraryBundle
//     * @return
//     */
//    protected File getLibManifestFile(LibraryBundle libraryBundle) {
//        File libManifest = libraryBundle.getManifest();
//        return libManifest;
//    }
//
//    /**
//     * 初始化基本信息
//     *
//     * @throws IOException
//     */
//    private void initBaseBundleInfo() throws IOException {
//        if (null != bundleBaseInfoFile && bundleBaseInfoFile.exists()) {
//            String bundleBaseInfo = FileUtils.readFileToString(bundleBaseInfoFile, "utf-8");
//            bundleInfoMap = JSON.parseObject(bundleBaseInfo, new TypeReference<Map<String, MuppBundleInfo>>() {
//            });
//        }
//    }
//
//    /**
//     * 生成awb的bundle信息
//     */
//    private void initAwbBundleInfo() throws DocumentException {
//        Set<String> wholeArtifactIdSet = new HashSet<String>();
//        for (AwbBundle awbLib : awbLibs) {
//
//            File awbSoFile = awbLib.outputBundleFile;
//            String bundleName = awbLib.getResolvedCoordinates().getArtifactId();
//            wholeArtifactIdSet.add(bundleName);
//            MuppBundleInfo muppBundleInfo = bundleInfoMap.get(bundleName);
//            if (null == muppBundleInfo) {
//                muppBundleInfo = new MuppBundleInfo();
//            }
//            // 更新bundleInfo
//            muppBundleInfo = parseManifest(awbLib.getOrgManifestFile(), muppBundleInfo, true, true);
//            //获取awb依赖的manifest的信息
//            for (ManifestDependency depLib : awbLib.getManifestDependencies()) {
//                muppBundleInfo = parseManifest(depLib.getManifest(), muppBundleInfo, false, false);
//            }
//
//            // 获取版本信息
//            muppBundleInfo.setArtifactId(awbLib.getResolvedCoordinates().getArtifactId());
//            muppBundleInfo.setVersion(apkVersion + "@" + awbLib.getResolvedCoordinates().getVersion());
//            // 获取bundle的大小等信息
//            if (awbSoFile != null && awbSoFile.exists() && awbSoFile.isFile()) {
//                boolean hasSo = ZipUtils.isFolderExist(awbSoFile, "lib");
//                muppBundleInfo.setHasSO(hasSo);
//                muppBundleInfo.setMd5(MD5Util.getFileMD5(awbSoFile));
//                muppBundleInfo.setSize(FileUtils.sizeOf(awbSoFile));
//                muppBundleInfo.setPackageUrl(awbLib.packageUrl);
//            }
//
//            muppBundleInfo.setIsInternal(!awbLib.isRemote);
//
//            bundleInfoMap.put(bundleName, muppBundleInfo);
//            awbLibMap.put(bundleName, awbLib);
//        }
//        // 清理bundleBaseInfoFile 中配置，但是实际并没有的awb信息
//        List<String> delList = new ArrayList<String>();
//        for (String artifactId : bundleInfoMap.keySet()) {
//            if (!wholeArtifactIdSet.contains(artifactId)) {
//                delList.add(artifactId);
//            }
//        }
//        for (String key : delList) {
//            bundleInfoMap.remove(key);
//        }
//    }
//
//
//}
