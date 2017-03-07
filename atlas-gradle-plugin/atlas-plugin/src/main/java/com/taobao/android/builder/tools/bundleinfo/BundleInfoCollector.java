//package com.taobao.android.builder.tools.bundleinfo;
//
//import com.alibaba.fastjson.JSON;
//import com.alibaba.fastjson.TypeReference;
//import com.android.build.gradle.internal.api.AppVariantContext;
//import com.android.builder.dependency.ManifestDependency;
//import com.google.common.collect.Maps;
//import com.taobao.android.builder.AtlasBuildContext;
//import com.taobao.android.builder.dependency.AndroidDependencyTree;
//import com.taobao.android.builder.dependency.AwbBundle;
//import com.taobao.android.builder.tools.bundleinfo.model.BundleInfo;
//import com.taobao.android.builder.tools.manifest.ManifestFileUtils;
//import org.apache.commons.io.FileUtils;
//import org.apache.commons.lang.StringUtils;
//import org.dom4j.*;
//import org.dom4j.io.SAXReader;
//
//import java.io.File;
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//
///**
// * Created by wuzhong on 2016/11/24.
// *
// * 根据依赖树搜集所有的bundle信息
// */
//public class BundleInfoCollector {
//
//    public AppVariantContext appVariantContext;
//    public String apkVersion;
//    public static BundleInfoCollector bundleInfoContext;
//
//    public static BundleInfoCollector getInstance(AppVariantContext appVariantContext) {
//        if (null == bundleInfoContext) {
//            bundleInfoContext = new BundleInfoCollector(appVariantContext);
//        }
//        return bundleInfoContext;
//    }
//
//
//    private BundleInfoCollector(AppVariantContext appVariantContext) {
//        this.appVariantContext = appVariantContext;
//        apkVersion = appVariantContext.getVariantConfiguration().getVersionName();
//    }
//
//    public List<BundleInfo> getBundleList() throws IOException, DocumentException {
//
//        if (null != appVariantContext.bundleInfoList) {
//            return appVariantContext.bundleInfoList;
//        }
//
//        AndroidDependencyTree androidDependencyTree = AtlasBuildContext.androidDependencyTrees.get(appVariantContext.getScope().
//                getVariantConfiguration().getFullName());
//
//        /**
//         * name 是artifictId
//         */
//        Map<String, BundleInfo> bundleInfoMap = getBundleInfoMap();
//
//        List<BundleInfo> muppBundleInfos = new ArrayList<BundleInfo>();
//
//        for (AwbBundle awbBundle : androidDependencyTree.getAwbBundles()) {
//            BundleInfo muppBundleInfo = generateBundleInfo(awbBundle, bundleInfoMap);
//            muppBundleInfos.add(muppBundleInfo);
//        }
//
//        appVariantContext.bundleInfoList = muppBundleInfos;
//
//        return muppBundleInfos;
//
//    }
//
//
//    public void updateBundleList() throws IOException, DocumentException {
//
//        List<BundleInfo> bundleInfoList = getBundleList();
//
//
//
//
//    }
//
//    /**
//     * 解析manifest文件，得到BundleInfo
//     *
//     * @return
//     */
//    private BundleInfo generateBundleInfo(AwbBundle awbBundle, Map<String, BundleInfo> bundleInfoMap) throws DocumentException {
//
//        String artifactId = awbBundle.getResolvedCoordinates().getArtifactId();
//        BundleInfo bundleInfo = bundleInfoMap.get(artifactId);
//        if (null == bundleInfo) {
//            bundleInfo = new BundleInfo();
//        }
//
//        awbBundle.isRemote = appVariantContext.getAtlasExtension().getTBuildConfig().getOutOfApkBundles().contains(artifactId);
//        bundleInfo.setIsInternal(!awbBundle.isRemote);
//        bundleInfo.setVersion(apkVersion + "@" + awbBundle.getResolvedCoordinates().getVersion());
//        bundleInfo.setPkgName(awbBundle.getPackageName());
//
//        String applicationName = ManifestFileUtils.getApplicationName(awbBundle.getOrgManifestFile());
//        if (StringUtils.isNotEmpty(applicationName)) {
//            bundleInfo.setApplicationName(applicationName);
//        }
//
//        SAXReader reader = new SAXReader();
//        Document document = reader.read(awbBundle.getManifest());// 读取XML文件
//        Element root = document.getRootElement();// 得到根节点
//
//        List<? extends Node> metadataNodes = root.selectNodes("//meta-data");
//        for (Node node : metadataNodes) {
//            Element element = (Element) node;
//            Attribute attribute = element.attribute("name");
//            if (attribute.getValue().equals("label")) {
//                Attribute labelAttribute = element.attribute("value");
//                bundleInfo.setName(labelAttribute.getValue());
//            } else if (attribute.getValue().equals("description")) {
//                Attribute descAttribute = element.attribute("value");
//                bundleInfo.setDesc(descAttribute.getValue());
//            }
//        }
//
//        addComponents(bundleInfo, root);
//
//        for (ManifestDependency depLib : awbBundle.getManifestDependencies()) {
//            SAXReader reader2 = new SAXReader();
//            Document document2 = reader2.read(depLib.getManifest());// 读取XML文件
//            Element root2 = document2.getRootElement();// 得到根节点
//            addComponents(bundleInfo, root2);
//        }
//
//        return bundleInfo;
//
//    }
//
//    private void addComponents(BundleInfo bundleInfo, Element root) {
//        List<? extends Node> serviceNodes = root.selectNodes("//service");
//        for (Node node : serviceNodes) {
//            Element element = (Element) node;
//            Attribute attribute = element.attribute("name");
//            bundleInfo.getServices().add(attribute.getValue());
//        }
//        List<? extends Node> receiverNodes = root.selectNodes("//receiver");
//        for (Node node : receiverNodes) {
//            Element element = (Element) node;
//            Attribute attribute = element.attribute("name");
//            bundleInfo.getReceivers().add(attribute.getValue());
//        }
//        List<? extends Node> providerNodes = root.selectNodes("//provider");
//        for (Node node : providerNodes) {
//            Element element = (Element) node;
//            Attribute attribute = element.attribute("name");
//            bundleInfo.getContentProviders().add(attribute.getValue());
//        }
//        List<? extends Node> activityNodes = root.selectNodes("//activity");
//        for (Node node : activityNodes) {
//            Element element = (Element) node;
//            Attribute attribute = element.attribute("name");
//            bundleInfo.getActivities().add(attribute.getValue());
//        }
//    }
//
//    private Map<String, BundleInfo> getBundleInfoMap() throws IOException {
//        File baseBunfleInfoFile = new File(appVariantContext.getScope().getGlobalScope().getProject().getProjectDir(), "bundleBaseInfoFile.json");
//        Map<String, BundleInfo> bundleFileMap = Maps.newHashMap();
//        if (null != baseBunfleInfoFile && baseBunfleInfoFile.exists() && baseBunfleInfoFile.canRead()) {
//            String bundleBaseInfo = FileUtils.readFileToString(baseBunfleInfoFile, "utf-8");
//            bundleFileMap = JSON.parseObject(bundleBaseInfo, new TypeReference<Map<String, BundleInfo>>() {
//            });
//        }
//        return bundleFileMap;
//    }
//
//}
