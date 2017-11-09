package com.taobao.android.inputs;

import com.android.utils.Pair;
import com.google.common.collect.Sets;
import com.taobao.android.PatchType;
import com.taobao.android.object.ArtifactBundleInfo;
import com.taobao.android.tpatch.model.ApkBO;
import com.taobao.android.tpatch.model.BundleBO;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * @author lilong
 * @create 2017-11-02 下午11:50
 */

public class BaseInput {

    public ApkBO baseApkBo;

    public ApkBO newApkBo;

    public Set<ArtifactBundleInfo> artifactBundleInfos = Sets.newHashSet();

    public PatchType patchType;

    public File baseApkFileList;

    public File newApkFileList;

    public File outPutFile;

    public List<Pair<BundleBO,BundleBO>> splitDiffBundle;

    public String[] notIncludeFiles;

    public boolean diffBundleDex;



}
