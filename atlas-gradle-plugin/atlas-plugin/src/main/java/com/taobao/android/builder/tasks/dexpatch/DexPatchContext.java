package com.taobao.android.builder.tasks.dexpatch;

import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.tasks.dexpatch.builder.DexBuilder;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author lilong
 * @create 2017-05-03 So in the afternoon
 */

public class DexPatchContext {

    private static DexPatchContext dexPatchContext = null;
    public static String mainMd5;
    public static String srcMainMd5;
    private File outPutDir = null;
    private File diffFolder = null;
    File bundleFolder = null;
    public static DexBuilder dexBuilder = AtlasBuildContext.dexBuilder;


    public String getBaseVersion() {
        return baseVersion;
    }

    private String baseVersion = null;

    public static List<DiffDependenciesTask.DiffResult> diffResults = new ArrayList();

    public static Map<String, DiffDependenciesTask.DiffResult> modifyArtifact = new HashMap<String, DiffDependenciesTask.DiffResult>();


    private DexPatchContext() {

        baseVersion = System.getProperty("apVersion", "");
        if (StringUtils.isBlank(baseVersion)){
            baseVersion = System.getProperty("MUPP_DEXPATCH_BASE_VERSION", "");
        }
        if (StringUtils.isBlank(baseVersion)){
            baseVersion = System.getProperty("MUPP_VERSION_NAME","");
        }
    }

    public static DexPatchContext getInstance() {
        if (dexPatchContext == null) {
            synchronized (DexPatchContext.class) {
                if (dexPatchContext == null) {
                    dexPatchContext = new DexPatchContext();
                }
            }
        }
        return dexPatchContext;
    }

    public void init(BaseVariantOutputData baseVariantOutputData) {
        outPutDir = baseVariantOutputData.getScope().getVariantScope().getGlobalScope().getOutputsDir();
    }

    public File getDiffFolder() {
        diffFolder = new File(outPutDir, "diff");
        return diffFolder;
    }

    public File getBundleDiffFolder(boolean maindex) {

        if (diffFolder != null) {
            if (maindex) {
                bundleFolder = new File(diffFolder, "diffMainDex");
            } else {
                bundleFolder = new File(diffFolder, "diffAwbDex");

            }
        } else {
            if (maindex) {
                bundleFolder = new File(getDiffFolder(), "diffMainDex");
            } else {
                bundleFolder = new File(getDiffFolder(), "diffAwbDex");
            }
        }
        return bundleFolder;
    }


    public File getBundleFolder(String bundleName, boolean maindex) {

        return new File(getBundleDiffFolder(maindex), bundleName);
    }

    public File getBundleDexFile(String bundleName, boolean maindex) {
        if (getBundleFolder(bundleName, maindex) != null) {
            return new File(getBundleFolder(bundleName, maindex), "classes.dex");
        }
        return null;
    }

    public File getBundleClassJar(String bundleName, boolean maindex) {
        if (getBundleFolder(bundleName, maindex) != null) {
            return new File(getBundleFolder(bundleName, maindex), "classes.jar");
        }
        return null;
    }

    public File getBundleMergeJar(String bundleName, boolean maindex) {
        if (getBundleFolder(bundleName, maindex) != null) {
            return new File(getBundleFolder(bundleName, maindex), "merge.jar");
        }
        return null;
    }

    public File getBundleOptJar(String bundleName, boolean mainDex) {
        if (getBundleFolder(bundleName, mainDex) != null) {
            return new File(getBundleFolder(bundleName, mainDex), "opt.jar");
        }
        return null;
    }

    public File getBundleArtifactFolder(String bundleName, String artifactName, boolean maindex) {
        if (getBundleFolder(bundleName, maindex) != null) {
            return new File(getBundleFolder(bundleName, maindex), artifactName);
        }
        return null;
    }

    public File getBundleArtifactDex(String bundleName, String artifactName, boolean maindex) {
        if (getBundleArtifactFolder(bundleName, artifactName, maindex) != null) {
            return new File(getBundleArtifactFolder(bundleName, artifactName, maindex), "classes.dex");
        }
        return null;
    }

    public File getBundleArtifactObfDex(String artifactName, String bundleName, boolean maindex) {
        if (getBundleArtifactFolder(bundleName, artifactName, maindex) != null) {
            return new File(getBundleArtifactFolder(bundleName, artifactName, maindex), "obf.dex");
        }
        return null;
    }

    public File getBundleArtifactJar(String artifactName, String bundleName, boolean maindex) {
        if (getBundleArtifactFolder(bundleName, artifactName, maindex) != null) {
            File classJar =  new File(getBundleArtifactFolder(bundleName, artifactName, maindex), "classes.jar");
            return classJar;
        }
        return null;
    }

    public File getBundleArtifactOptJar(String artifactName, String bundleName, boolean maindex) {
        if (getBundleArtifactFolder(bundleName, artifactName, maindex) != null) {
            File optJar =  new File(getBundleArtifactFolder(bundleName, artifactName, maindex), "opt.jar");
            if (optJar.exists()){
                return optJar;
            }else {
                return getBundleArtifactJar(artifactName,bundleName,maindex);
            }
        }
        return null;
    }

    public File getBundleObfDex(String bundleName, boolean maindex) {
        if (getBundleFolder(bundleName, maindex) != null) {
            return new File(getBundleFolder(bundleName, maindex), "obf.dex");
        }
        return null;
    }

    public File getBundleDiffDex(String bundleName, boolean maindex) {
        if (getBundleFolder(bundleName, maindex) != null) {
            return new File(getBundleFolder(bundleName, maindex), "diff.dex");
        }
        return null;
    }

    public File getPatchFile(String version) {
        File fullPatchFile = new File(getDiffFolder().getParentFile(), "full-patch");
        if (!fullPatchFile.exists()) {
            fullPatchFile.mkdirs();
        }
        return new File(fullPatchFile, version + ".patch");
    }


    public File getBundleBaseArtifactOptJar(DiffDependenciesTask.DiffResult diffResult) {
        File baseOptJar = new File(outPutDir, "baseFiles/" + diffResult.artifactName);
        if (baseOptJar.exists()) {
            return new File(baseOptJar, "opt.jar");
        } else {
            baseOptJar.mkdirs();
        }
        return new File(baseOptJar, "opt.jar");
    }


    public File getBaseLibBundleDexFile(String key) {

        File baseBundlefolder = new File(outPutDir, "baseFiles/" + "lib" + key.replace(".", "_"));
        if (!baseBundlefolder.exists()) {
            baseBundlefolder.mkdirs();
        }
        return new File(baseBundlefolder, "classes.dex");

    }

    public File getBaseBundleDexFile(String key) {

        File baseBundlefolder = new File(outPutDir, "baseFiles/" + key);
        if (!baseBundlefolder.exists()) {
            baseBundlefolder.mkdirs();
        }
        return new File(baseBundlefolder, "classes.dex");

    }

    public File getBundleBaseArtifactJar(String artifactName, String bundleName, boolean isMaindex) {
        File baseOptJar = new File(outPutDir, "baseFiles/" + artifactName);
        if (baseOptJar.exists()) {
            return new File(baseOptJar, "classes.jar");
        } else {
            baseOptJar.mkdirs();
        }
        return new File(baseOptJar, "classes.jar");
    }

    public File getBaseFileFolder() {

        return new File(outPutDir, "baseFiles/");
    }

    public File getBundleArtifactObfJar(String artifactName, String bundleName, boolean isMaindex) {
        if (getBundleArtifactFolder(bundleName, artifactName, isMaindex) != null) {
            return new File(getBundleArtifactFolder(bundleName, artifactName, isMaindex), "proguard.jar");
        }
        return null;
    }
}
