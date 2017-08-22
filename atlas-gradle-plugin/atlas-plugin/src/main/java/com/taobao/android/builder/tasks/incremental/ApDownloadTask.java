package com.taobao.android.builder.tasks.incremental;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.utils.FileUtils;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.extension.TBuildType;
import com.taobao.android.builder.tools.EnvHelper;
import com.taobao.android.builder.tools.MD5Util;
import de.undercouch.gradle.tasks.download.Download;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Project;

import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by chenhjohn on 2017/7/16.
 */

public class ApDownloadTask extends Download {
    private static final Pattern MTL_PATTERN = Pattern.compile("buildConfigId=(\\d+)");

    private File apPath;

    private boolean refreshAp;

    private String mtlUrl;

    @Override
    public void download() throws IOException {
        try {
            if (!refreshAp && apPath.exists()) {
                return;
            }
        } catch (Exception e) {
            //just ignore
        }
        String downloadUrl = getDownloadUrl(mtlUrl);
        checkNotNull(downloadUrl, "Missing ap downloadUrl for mtlConfigUrl" + mtlUrl);
        src(downloadUrl);
        dest(apPath);
        super.download();
    }

    private static String getDownloadUrl(String mtlConfigUrl) throws IOException {
        Matcher matcher = MTL_PATTERN.matcher(mtlConfigUrl);
        String configId = "";
        if (matcher.find()) {
            configId = matcher.group(1);
        }

        String apiUrl = "http://" + AtlasBuildContext.sBuilderAdapter.tpatchHistoryUrl
            + "/rpc/androidPlugin/getAp.json?buildConfigId=" + configId;

        URL api = new URL(apiUrl);
        BufferedReader in = new BufferedReader(new InputStreamReader(api.openStream()));

        String inputLine = in.readLine();
        in.close();

        return inputLine.trim().replace("\"", "").replace("\\", "");
    }

    public static class ConfigAction implements TaskConfigAction<ApDownloadTask> {

        private static final String PROPERTY_PROPERTIES_FILE_AWO = "awoconfig.properties";

        private static final String PROP_AWO = "awoprop";

        private static final String MTL_URL = "mtl_url";

        private static final String AP_PATH = "ap_path";

        private static final String REFRESH_AP = "refreshAp";

        private static final String SUPPORT_DYN = "support_dyn";

        private static final String SUPPORT_APK = "support_apk";

        private final TBuildType buildType;

        public ConfigAction(TBuildType buildType) {
            this.buildType = buildType;
        }

        @Override
        @NonNull
        public String getName() {
            return "ApDownload";
        }

        @Override
        @NonNull
        public Class<ApDownloadTask> getType() {
            return ApDownloadTask.class;
        }

        @Override
        public void execute(ApDownloadTask task) {
            Project project = task.getProject();
            String mtlUrl = null;
            File apPath = null;
            boolean refreshAp = false;
            String awoProp = EnvHelper.getEnv(PROP_AWO);
            File propfile;
            if (!StringUtils.isEmpty(awoProp)) {
                propfile = new File(awoProp);
            } else {
                propfile = project.file(PROPERTY_PROPERTIES_FILE_AWO);
            }
            if (propfile.exists()) {
                Properties properties = new Properties();
                try {
                    properties.load(new FileInputStream(propfile));
                } catch (IOException ex) {
                    throw new RuntimeException(awoProp + ": trouble reading", ex);
                }

                String apProperty = properties.getProperty(AP_PATH);
                if (apProperty != null) {
                    apPath = new File(apProperty);
                }
                refreshAp = "true".equals(properties.getProperty(REFRESH_AP));
                mtlUrl = properties.getProperty(MTL_URL);
            }
            if (project.hasProperty(MTL_URL)) {
                mtlUrl = (String)project.property(MTL_URL);
                if (project.hasProperty(AP_PATH)) {
                    apPath = new File((String)project.property(AP_PATH));
                }
                if (project.hasProperty(REFRESH_AP)) {
                    refreshAp = "true".equals(project.property(REFRESH_AP));
                }
            }
            if (mtlUrl == null) {
                task.setEnabled(false);
            }
            if (apPath == null) {
                apPath = FileUtils.join(project.getBuildDir(), FD_INTERMEDIATES, "awo", MD5Util.getMD5(mtlUrl) + ".ap");
            }
            task.mtlUrl = mtlUrl;
            task.apPath = apPath;
            task.refreshAp = refreshAp;

            buildType.setBaseApFile(apPath);
        }
    }
}
