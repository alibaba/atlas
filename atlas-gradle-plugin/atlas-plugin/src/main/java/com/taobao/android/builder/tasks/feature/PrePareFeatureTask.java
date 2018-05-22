package com.taobao.android.builder.tasks.feature;

import com.android.build.gradle.api.BaseVariantOutput;
import com.android.build.gradle.internal.api.VariantContext;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.taobao.android.builder.tasks.manager.MtlBaseTaskAction;
import org.gradle.api.tasks.TaskAction;

import javax.xml.ws.Action;

/**
 * PrePareFeatureTask
 *
 * @author zhayu.ll
 * @date 18/1/15
 * @time 下午4:32
 * @description  
 */
public class PrePareFeatureTask extends BaseTask {

    private VariantContext variantContext;

    @TaskAction
    public void action(){


    }






    public static class ConfigAction extends MtlBaseTaskAction<PrePareFeatureTask>{

        public ConfigAction(VariantContext variantContext, BaseVariantOutput baseVariantOutput) {
            super(variantContext, baseVariantOutput);
        }

        @Override
        public String getName() {
            return variantContext.getBaseVariantData().getTaskName("prepare","feature");
        }

        @Override
        public Class getType() {
            return PrePareFeatureTask.class;
        }

        @Override
        public void execute(PrePareFeatureTask task) {
            task.setVariantName(variantContext.getVariantName());
            task.variantContext = variantContext;

        }
    }
}
