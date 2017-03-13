package com.taobao.android.builder.tasks.library;

import java.io.File;
import java.util.List;

import com.alibaba.fastjson.JSON;

import com.android.build.gradle.api.LibraryVariant;
import com.android.builder.model.SourceProvider;
import com.taobao.android.builder.tasks.awo.projectstrucure.ModuleInfo;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;

/**
 * Created by wuzhong on 2017/2/25.
 */
public class ModuleInfoWriter {

    private Project project;

    private LibraryVariant libraryVariant;

    public ModuleInfoWriter(Project project, LibraryVariant libraryVariant) {
        this.project = project;
        this.libraryVariant = libraryVariant;
    }

    public void write() {
        //生成项目的基本信息
        try {
            ModuleInfo moduleInfo = new ModuleInfo();
            moduleInfo.group = project.getGroup().toString();
            moduleInfo.name = project.getName();
            moduleInfo.version = project.getVersion().toString();

            moduleInfo.path = project.getProjectDir()
                .getAbsolutePath()
                .replace(project.getRootDir().getAbsolutePath(), "")
                .substring(1);

            //获取android manifst
            moduleInfo.packageName = libraryVariant.getApplicationId();

            List<SourceProvider> sourceProviders = libraryVariant.getSourceSets();

            for (SourceProvider sourceProvider : sourceProviders) {

                for (File java : sourceProvider.getJavaDirectories()) {
                    if (java.exists()) {
                        moduleInfo.java_SrcDirs.add(java.getAbsolutePath());
                    }
                }

                for (File res : sourceProvider.getResDirectories()) {
                    if (res.exists()) {
                        moduleInfo.res_srcDirs.add(res.getAbsolutePath());
                    }
                }

                for (File assets : sourceProvider.getAssetsDirectories()) {
                    if (assets.exists()) {
                        moduleInfo.assets_srcDirs.add(assets.getAbsolutePath());
                    }
                }
            }

            File moduleInfoFile = new File(project.getRootDir(), ".awo/" + moduleInfo.name + ".mi");
            moduleInfoFile.getParentFile().mkdirs();

            FileUtils.write(moduleInfoFile, JSON.toJSONString(moduleInfo, true));
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
