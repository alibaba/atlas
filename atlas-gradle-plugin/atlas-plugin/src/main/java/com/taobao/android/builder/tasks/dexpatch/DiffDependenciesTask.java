package com.taobao.android.builder.tasks.dexpatch;

import com.alibaba.fastjson.JSON;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.build.gradle.internal.core.GradleVariantConfiguration;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.build.gradle.internal.variant.ApkVariantOutputData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.dependency.AtlasDependencyTree;
import com.taobao.android.builder.dependency.model.AwbBundle;
import com.taobao.android.builder.dependency.output.DependencyJson;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import com.taobao.android.builder.tools.MD5Util;
import com.taobao.android.builder.tools.bundleinfo.model.BasicBundleInfo;
import com.taobao.android.builder.tools.bundleinfo.model.BundleInfo;
import com.taobao.android.builder.tools.classinject.FrameworkProperties;
import com.taobao.android.builder.tools.manifest.ManifestFileUtils;
import com.taobao.android.object.ArtifactBundleInfo;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.dom4j.*;
import org.dom4j.io.SAXReader;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.Optional;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * @author lilong
 * @create 2017-05-03 On the afternoon of 2:22
 */

public class DiffDependenciesTask extends BaseTask {
    private AppVariantOutputContext appVariantOutputContext;
    private File apDir;
    private File manifestFile;
    private File outJsonFile;
    private static final String MAIN_DEX = "com.taobao.maindex";
    private DependencyJson apDependencyJson;

    public DiffDependenciesTask() {
    }

    @OutputDirectory
    @Optional
    public File getOutJsonFile() {
        return this.outJsonFile;
    }

    @InputFile
    @Optional
    public File getManifestFile() {
        return this.manifestFile;
    }


    @InputDirectory
    @Optional
    public File getApDir() {
        return this.apDir;
    }

    File modifyAwbFolder;

    File modifyMaindexFolder;


    @TaskAction
    public void generate() throws IOException {
        if (DexPatchContext.diffResults.size() > 0) {
            for (DiffResult diff : DexPatchContext.diffResults) {

                System.out.println("modify: " + diff.bundleName + ":" + diff.artifactName + ":" + diff.baseVersion + "->" + diff.newVersion);
            }
        } else {
            throw new IOException("No modified dependency found!");
        }

        File atlasJson = new File(getApDir(), "atlasFrameworkProperties.json");
        List<BasicBundleInfo> bundleInfos = null;
        if (atlasJson.exists()) {
            FrameworkProperties atlasFrameworkProperties = JSON.parseObject(
                    FileUtils.readFileToString(atlasJson),
                    FrameworkProperties.class);
            bundleInfos = atlasFrameworkProperties.bundleInfo;
            DexPatchContext.srcMainMd5 = atlasFrameworkProperties.unit_tag;

        }
        if (null != DexPatchContext.diffResults) {
            try {
                this.appVariantOutputContext.artifactBundleInfos = this.getArtifactBundleInfo(bundleInfos, this.getManifestFile(), this.getApDir());

            } catch (Exception var2) {
                throw new GradleException(var2.getMessage());
            }
        }
//
        for (DiffResult diffResult : DexPatchContext.diffResults) {
            if (diffResult.bundleName.equals(MAIN_DEX)) {
                File artifactFolder = DexPatchContext.getInstance().getBundleArtifactFolder(diffResult.bundleName, diffResult.artifactName, true);
                artifactFolder.mkdirs();
                continue;
            }
            Iterator<ArtifactBundleInfo> artifactBundleInfos = appVariantOutputContext.artifactBundleInfos.iterator();
            while (artifactBundleInfos.hasNext()) {
                ArtifactBundleInfo artifactBundleInfo = artifactBundleInfos.next();
                if (diffResult.bundleName.substring(diffResult.bundleName.indexOf(":") + 1).equals(artifactBundleInfo.getArtifactId())) {
                    diffResult.bundleName = artifactBundleInfo.getPkgName();
                    File artifactFolder = DexPatchContext.getInstance().getBundleArtifactFolder(diffResult.bundleName, diffResult.artifactName, false);
                    artifactFolder.mkdirs();
                    break;
                }
            }
        }
    }

    private Set<ArtifactBundleInfo> getArtifactBundleInfo(List<BasicBundleInfo> bundleInfos, File mainfestFile, File apDir) throws IOException, DocumentException {
        HashSet artifactBundleInfos = new HashSet();
        if (null == apDir) {
            throw new GradleException("No Ap dependency found!");
        } else {
            File apManifest = new File(apDir, "AndroidManifest.xml");
            String apVersion = null;

            try {
                apVersion = ManifestFileUtils.getVersionName(apManifest);
            } catch (Exception var15) {
                throw new GradleException(var15.getMessage(), var15);
            }

            ArtifactBundleInfo mainBundleInfo = getMainArtifactBundInfo(mainfestFile);
            mainBundleInfo.setBaseVersion(apVersion);
            mainBundleInfo.setMainBundle(Boolean.valueOf(true));
            mainBundleInfo.setVersion(this.appVariantOutputContext.getVariantContext().getVariantConfiguration().getVersionName());
            artifactBundleInfos.add(mainBundleInfo);
            AtlasDependencyTree atlasDependencyTree = (AtlasDependencyTree) AtlasBuildContext.androidDependencyTrees.get(this.appVariantOutputContext.getVariantContext().getVariantConfiguration().getFullName());
            try {
                DexPatchContext.mainMd5 = MD5Util.getMD5(org.apache.commons.lang3.StringUtils.join(atlasDependencyTree.getMainBundle().getAllDependencies()));
            } catch (Exception e) {
                e.printStackTrace();
            }

            ArtifactBundleInfo awbBundleInfo;
            for (Iterator var9 = atlasDependencyTree.getAwbBundles().iterator(); var9.hasNext(); artifactBundleInfos.add(awbBundleInfo)) {
                AwbBundle awbBundle = (AwbBundle) var9.next();
                BundleInfo bundleInfo = awbBundle.bundleInfo;
                String bundleUnitTag = null;

                try {
                    bundleUnitTag = MD5Util.getMD5(org.apache.commons.lang3.StringUtils.join(awbBundle.getAllDependencies()));
                } catch (Exception e) {
                    e.printStackTrace();
                }


                awbBundleInfo = new ArtifactBundleInfo();
                awbBundleInfo.setMainBundle(Boolean.valueOf(false));
                awbBundleInfo.setName(awbBundle.getResolvedCoordinates().getArtifactId());
                awbBundleInfo.setPkgName(bundleInfo.getPkgName());
                awbBundleInfo.setApplicationName(bundleInfo.getApplicationName());
                awbBundleInfo.setArtifactId(awbBundle.getResolvedCoordinates().getArtifactId());
                awbBundleInfo.setName(bundleInfo.getName());

                for (BasicBundleInfo key : bundleInfos) {
                    if (key.getPkgName().equals(awbBundle.getPackageName())) {
                        awbBundleInfo.setSrcUnitTag(key.getUnique_tag());
                        awbBundleInfo.setBaseVersion(key.getVersion());
                        awbBundleInfo.setDependency(key.getDependency());
                    }
                }
                try {
                    awbBundleInfo.setUnitTag(MD5Util.getMD5(DexPatchContext.mainMd5 + bundleUnitTag));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                String version = bundleInfo.getVersion();
                if (version.indexOf("@") > 0) {
                    String[] libBundleName = version.split("@");
                    version = libBundleName[libBundleName.length - 1];
                }

                awbBundleInfo.setVersion(version);
                artifactBundleInfos.add(awbBundleInfo);
            }

            return artifactBundleInfos;
        }
    }


    private static ArtifactBundleInfo getMainArtifactBundInfo(File manifestFile) {
        ArtifactBundleInfo mainBundleInfo = new ArtifactBundleInfo();
        SAXReader reader = new SAXReader();
        Document document = null;

        try {
            document = reader.read(manifestFile);
        } catch (DocumentException var11) {
            throw new GradleException(var11.getMessage(), var11);
        }

        Element root = document.getRootElement();
        List metadataNodes = root.selectNodes("//meta-data");
        Iterator applicatNodes = metadataNodes.iterator();

        Attribute attr;
        Attribute versionName;
        while (applicatNodes.hasNext()) {
            Node pkgName = (Node) applicatNodes.next();
            Element node = (Element) pkgName;
            attr = node.attribute("name");
            if (attr.getValue().equals("label")) {
                versionName = node.attribute("value");
                mainBundleInfo.setName(versionName.getValue());
            }
        }

        List applicatNodes1 = root.selectNodes("//application");
        Iterator pkgName1 = applicatNodes1.iterator();

        while (pkgName1.hasNext()) {
            Node node1 = (Node) pkgName1.next();
            Element attr1 = (Element) node1;
            versionName = attr1.attribute("name");
            if (versionName != null) {
                mainBundleInfo.setApplicationName(versionName.getValue());
            }
        }

        if ("manifest".equalsIgnoreCase(root.getName())) {
            List pkgName2 = root.attributes();
            Iterator node2 = pkgName2.iterator();

            while (node2.hasNext()) {
                attr = (Attribute) node2.next();
                if (StringUtils.equalsIgnoreCase(attr.getName(), "versionName")) {
                    String versionName1 = attr.getValue();
                    mainBundleInfo.setVersion(versionName1);
                }
            }
        }

        String pkgName3 = root.attributeValue("package");
        mainBundleInfo.setPkgName(pkgName3);
        return mainBundleInfo;
    }

    public static class ConfigAction extends MtlBaseTaskAction<DiffDependenciesTask> {
        private AppVariantContext appVariantContext;

        public ConfigAction(AppVariantContext appVariantContext, BaseVariantOutputData baseVariantOutputData) {
            super(appVariantContext, baseVariantOutputData);
            this.appVariantContext = appVariantContext;
            DexPatchContext.getInstance().init(baseVariantOutputData);
        }

        public String getName() {
            return this.scope.getTaskName("DiffDependencies");
        }

        public Class<DiffDependenciesTask> getType() {
            return DiffDependenciesTask.class;
        }

        public void execute(DiffDependenciesTask diffDependenciesTask) {
            super.execute(diffDependenciesTask);
            final ApkVariantOutputData variantOutputData = (ApkVariantOutputData) this.scope.getVariantOutputData();
            GradleVariantConfiguration config = this.scope.getVariantScope().getVariantConfiguration();
            ConventionMappingHelper.map(diffDependenciesTask, "outJsonFile", new Callable() {
                public File call() throws Exception {
                    File file = DiffDependenciesTask.ConfigAction.this.scope.getVariantScope().getGlobalScope().getOutputsDir();
                    if (!file.exists()) {
                        file.mkdirs();
                    }

                    return file;
                }
            });
            ConventionMappingHelper.map(diffDependenciesTask, "manifestFile", new Callable() {
                public File call() throws Exception {
                    if (null != DiffDependenciesTask.ConfigAction.this.appVariantContext.apContext.getApExploredFolder()) {
                        if (!DiffDependenciesTask.ConfigAction.this.appVariantContext.apContext.getApExploredFolder().exists()) {
                            DiffDependenciesTask.ConfigAction.this.appVariantContext.apContext.getApExploredFolder().mkdirs();
                        }
                        DependencyJson dependencyJson = ((AtlasDependencyTree) AtlasBuildContext.androidDependencyTrees.get(DiffDependenciesTask.ConfigAction.this.scope.getVariantOutputData().variantData.getName())).getDependencyJson();
                        File baseDependencyFile = new File(DiffDependenciesTask.ConfigAction.this.appVariantContext.apContext.getApExploredFolder(), "dependencies.txt");
                        if (baseDependencyFile.exists()) {
                            DependencyJson apDependencyJson = JSON.parseObject(FileUtils.readFileToString(baseDependencyFile),
                                    DependencyJson.class);
                            diffDependenciesTask.apDependencyJson = apDependencyJson;
                            diff(apDependencyJson, dependencyJson);
                        }
                    }
                    return variantOutputData.manifestProcessorTask.getManifestOutputFile();
                }
            });

            ConventionMappingHelper.map(diffDependenciesTask, "apDir", new Callable() {
                public File call() throws Exception {
                    return null != DiffDependenciesTask.ConfigAction.this.appVariantContext.apContext.getApExploredFolder() && DiffDependenciesTask.ConfigAction.this.appVariantContext.apContext.getApExploredFolder().exists() ? DiffDependenciesTask.ConfigAction.this.appVariantContext.apContext.getApExploredFolder() : null;
                }
            });
            diffDependenciesTask.appVariantOutputContext = this.getAppVariantOutputContext();
        }
    }

    public static void diff(DependencyJson baseDependencyJson, DependencyJson newDependencyJson) {
        List<String> baseMainDependecies = baseDependencyJson.getMainDex();
        List<String> newMainDependecies = newDependencyJson.getMainDex();
        getDiffDependencies(baseMainDependecies, newMainDependecies, MAIN_DEX);
        Map<String, AwbDependency> baseAwbs = toAwbDependencies(baseDependencyJson.getAwbs());
        Map<String, AwbDependency> newAwbs = toAwbDependencies(newDependencyJson.getAwbs());
        for (String bundleName : newAwbs.keySet()) {
            AwbDependency newAwbDeps = newAwbs.get(bundleName);
            AwbDependency baseAwbDeps = baseAwbs.get(bundleName);
            if (null != baseAwbDeps) {
                if (baseAwbDeps.version.equals(newAwbDeps.version)) {
                    getDiffDependencies(baseAwbDeps.dependencies, newAwbDeps.dependencies, bundleName);

                } else {
                    DiffResult diff = new DiffResult();
                    diff.bundleName = bundleName;
                    diff.baseVersion = baseAwbDeps.version;
                    diff.newVersion = newAwbDeps.version;
                    diff.artifactName = bundleName.split(":")[1];
                    if (!DexPatchContext.modifyArtifact.containsKey(diff.artifactName)) {
                        DexPatchContext.diffResults.add(diff);
                        DexPatchContext.modifyArtifact.put(diff.artifactName, diff);
                    }
                    getDiffDependencies(baseAwbDeps.dependencies, newAwbDeps.dependencies, bundleName);
                }
            }
        }
    }

    private static Map<String, AwbDependency> toAwbDependencies(Map<String, ArrayList<String>> awbs) {
        Map<String, AwbDependency> maps = new HashMap<String, AwbDependency>();
        if (AtlasBuildContext.sBuilderAdapter.prettyDependencyFormat) {
            for (Map.Entry<String, ArrayList<String>> entry : awbs.entrySet()) {
                String key = entry.getKey();
                String name = null;
                String[] names = key.split(":");
                name = names[0] + ":" + names[1];
                String version = names[2].split("@")[0];
                AwbDependency awbDependency = new AwbDependency(version, name, entry.getValue());
                maps.put(name, awbDependency);
            }
        } else {
            for (Map.Entry<String, ArrayList<String>> entry : awbs.entrySet()) {
                String key = entry.getKey();
                String name = key.substring(0, key.lastIndexOf(":"));
                name = name.substring(0, name.lastIndexOf(":"));
                String version = key.substring(key.lastIndexOf(":") + 1);
                AwbDependency awbDependency = new AwbDependency(version, name, entry.getValue());
                maps.put(name, awbDependency);
            }
        }
        return maps;
    }


    private static void getDiffDependencies(List<String> baseDependecies, List<String> newDependecies, String bundleName) {

        Map<String, String> baseMap = new HashMap<String, String>();
        Map<String, String> newMap = new HashMap<String, String>();
        if (AtlasBuildContext.sBuilderAdapter.prettyDependencyFormat) {
            for (String dep : baseDependecies) {
                String[] names = dep.split(":");
                String name = names[0] + ":" + names[1];
                String version = names[2].split("@")[0];
                baseMap.put(name, version);
            }
            for (String dep : newDependecies) {
                String[] names = dep.split(":");
                String name = names[0] + ":" + names[1];
                String version = names[2].split("@")[0];
                newMap.put(name, version);
            }


        } else {
            for (String dep : baseDependecies) {
                String name = dep.substring(0, dep.lastIndexOf(":"));
                String version = dep.substring(dep.lastIndexOf(":") + 1);
                baseMap.put(name, version);
            }
            for (String dep : newDependecies) {
                String name = dep.substring(0, dep.lastIndexOf(":"));
                String version = dep.substring(dep.lastIndexOf(":") + 1);
                newMap.put(name, version);
            }
        }


        for (String key : newMap.keySet()) {
            String baseValue = baseMap.get(key);
            //For added direct neglect
            if (baseValue == null) {
                continue;
            }
            String newValue = newMap.get(key);
            if (!newValue.equals(baseValue)) {
                DiffResult diff = new DiffResult();
                diff.bundleName = bundleName;
                diff.artifactName = key.split(":")[1];
                diff.baseVersion = baseValue;
                diff.newVersion = newValue;
                if (bundleName.equals(MAIN_DEX)) {
                    diff.isMaindex = true;
                }
                if (!DexPatchContext.modifyArtifact.containsKey(diff.artifactName)) {
                    DexPatchContext.diffResults.add(diff);
                    DexPatchContext.modifyArtifact.put(diff.artifactName, diff);
                }
            }
        }
    }

    public static class AwbDependency {

        String version;
        String bundleName;
        ArrayList<String> dependencies;

        public AwbDependency(String version, String bundleName, ArrayList<String> dependencies) {
            this.version = version;
            this.bundleName = bundleName;
            this.dependencies = dependencies;
        }
    }

    public static class DiffResult {
        public String bundleName;
        public String artifactName;
        public String baseVersion;
        public String newVersion;
        public boolean isMaindex;
        public String bundleVersion;
        public String bundleArtifactName;

    }


}
