package com.taobao.android.builder.tasks.helper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.taobao.android.builder.extension.AtlasExtension;
import com.taobao.android.builder.tools.guide.AtlasConfigField;
import com.taobao.android.builder.tools.guide.AtlasConfigHelper;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;

/**
 * Created by wuzhong on 2017/3/18.
 *
 * @author wuzhong
 * @date 2017/03/18
 */
public class AtlasListTask extends DefaultTask {

    @TaskAction
    public void executeTask() {

        Project project = getProject();

        AtlasExtension atlasExtension = project.getExtensions().getByType(AtlasExtension.class);

        List<AtlasConfigField> list = null;
        try {
            list = AtlasConfigHelper.readConfig(atlasExtension, "atlas");
        } catch (Throwable e) {
            e.printStackTrace();
            return;
        }

        List<String> lines = new ArrayList<>();
        lines.add(" 功能  | 配置名称 |  类型 | 值 ");
        lines.add(" ------------- | ------------- | ------------- | ------------- ");

        for (AtlasConfigField configField : list) {
            lines.add(
                configField.desc + "  | " + configField.name + " | " + configField.type + "  | "
                    + configField.value);
        }

        for (String line : lines) {
            project.getLogger().error(line);
        }

        File file = new File(project.getProjectDir(), "atlasConfigList.MD");

        try {
            FileUtils.writeLines(file, lines);
            project.getLogger().error(file.getAbsolutePath() + " has generated");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
