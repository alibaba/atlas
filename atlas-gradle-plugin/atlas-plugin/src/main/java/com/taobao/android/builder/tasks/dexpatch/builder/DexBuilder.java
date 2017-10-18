package com.taobao.android.builder.tasks.dexpatch.builder;

import com.taobao.android.builder.extension.DexConfig;
import com.taobao.android.builder.extension.TBuildType;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @author lilong
 * @create 2017-05-04 On the morning of the name
 */

public interface DexBuilder {

    public void setInput(File file, String bundleName);


    public List<File> getOutput(String bundleName);


    public void excute() throws IOException;


    void init(DexConfig dexConfig);

    void clear();

    Map<String, List<File>> getOutputs();

    void obfDex(File mappingFile, File bundleListCfg, File baseApkFile, TBuildType buildType, boolean b);
}
