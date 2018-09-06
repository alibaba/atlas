package com.taobao.android.inputs;

import com.google.common.collect.Lists;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author lilong
 * @create 2017-11-02 下午11:51
 */

public class TpatchInput extends BaseInput {


    public boolean retainMainBundleRes = true;

    public boolean createAll = false;

    public boolean diffNativeSo;

    public boolean diffBundleSo;

    public File baseApkFileList;

    public File newApkFileList;

    public boolean hasMainBundle;

    public List<String> noPatchBundles = Lists.newArrayList();

    public List<String> versionList = new ArrayList<String>();

    public String productName;

    public String hisPatchUrl;

    public boolean createHisPatch;

    public String LAST_PATCH_URL;

    public File outPatchDir;

    public File outPutJson;

    public File bundleWhiteList;

    public boolean newPatch = true;

    //dexpatch name:com.taobao.maindex
    public String mainBundleName = "libcom_taobao_maindex";




}
