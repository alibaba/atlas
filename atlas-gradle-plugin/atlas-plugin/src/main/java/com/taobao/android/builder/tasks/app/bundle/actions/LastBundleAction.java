package com.taobao.android.builder.tasks.app.bundle.actions;

import com.alibaba.fastjson.JSON;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.utils.ILogger;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.dependency.model.AwbBundle;
import com.taobao.android.builder.tasks.transform.AtlasMergeJavaResourcesTransform;
import com.taobao.android.builder.tools.MD5Util;
import com.taobao.android.builder.tools.bundleinfo.DynamicBundleInfo;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Task;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;

/**
 * LastBundleAction
 *
 * @author zhayu.ll
 * @date 18/1/12
 * @time 下午6:49
 * @description  
 */
public class LastBundleAction implements Action<Task> {

    private ILogger logger = LoggerWrapper.getLogger(LastBundleAction.class);

    protected Map<AwbBundle, File> awbBundles;

    protected AppVariantOutputContext appVariantOutputContext;

    public LastBundleAction(Map<AwbBundle, File> awbMap, AppVariantOutputContext appVariantOutputContext) {
        this.awbBundles = awbMap;
        this.appVariantOutputContext = appVariantOutputContext;
    }

    @Override
    public void execute(Task o) {

        if (appVariantOutputContext.getSoMap().size() > 0) {
            File nativeBundleInfo = new File(appVariantOutputContext.getScope().getGlobalScope().getOutputsDir(),
                    "nativeInfo-" +
                            appVariantOutputContext.getVariantContext().getVariantConfiguration()
                                    .getVersionName() +
                            ".json");

            try {
                org.apache.commons.io.FileUtils.write(nativeBundleInfo, JSON.toJSONString(appVariantOutputContext.getSoMap().values()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (appVariantOutputContext.getVariantData().getName().endsWith("debug")) {
            if (awbBundles != null) {
                for (Map.Entry<AwbBundle, File> entry : awbBundles.entrySet()) {
                    String url = AtlasBuildContext.atlasApkProcessor.uploadBundle(appVariantOutputContext.getVariantContext().getProject(), entry.getValue(), entry.getKey(), appVariantOutputContext.getVariantContext().getBuildType());
                    if (StringUtils.isNotEmpty(url)) {
                        entry.getKey().bundleInfo.setUrl(url);
                    }
                    entry.getKey().bundleInfo.setMd5(MD5Util.getFileMD5(entry.getValue()));
                    entry.getKey().bundleInfo.setSize(entry.getValue().length());
                    AtlasBuildContext.atlasApkProcessor.removeBundle(appVariantOutputContext, entry.getKey(), entry.getValue());
                }

            }
            List<DynamicBundleInfo> dynamicBundleInfos = AtlasBuildContext.atlasApkProcessor.generateAllBundleInfo(awbBundles.keySet());
            File bundleInfoFile = new File(appVariantOutputContext.getScope().getGlobalScope().getOutputsDir(),
                    "bundleInfo-" +
                            appVariantOutputContext.getVariantContext().getVariantConfiguration()
                                    .getVersionName() +
                            ".json");

            try {
                org.apache.commons.io.FileUtils.write(bundleInfoFile, JSON.toJSONString(dynamicBundleInfos));
            } catch (IOException e) {
                e.printStackTrace();
            }

        } else {
            logger.info("do nothing when packageAwbs done in " + appVariantOutputContext.getVariantData().getName() + " build!");
        }
    }
}
