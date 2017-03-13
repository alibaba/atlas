package com.android.builder.core;

import com.android.build.gradle.internal.dsl.AaptOptions;
import com.android.builder.internal.aapt.AaptException;
import com.android.builder.internal.aapt.AaptPackageConfig;
import com.android.builder.internal.aapt.v1.AaptV1;
import com.android.ide.common.process.ProcessExecutor;
import com.android.ide.common.process.ProcessInfoBuilder;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.sdklib.BuildToolInfo;
import com.android.utils.ILogger;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.gradle.api.GradleException;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * Created by wuzhong on 2017/2/6.
 */
public class AtlasAapt extends AaptV1 {
    /**
     * Creates a new entry point to the original {@code aapt}.
     *
     * @param processExecutor      the executor for external processes
     * @param processOutputHandler the handler to process the executed process' output
     * @param buildToolInfo        the build tools to use
     * @param logger               logger to use
     * @param processMode          the process mode to run {@code aapt} on
     * @param cruncherProcesses    if using build tools that support crunching processes, how many
     *                             processes to use; if set to {@code 0}, the default number will be used
     */
    public AtlasAapt(ProcessExecutor processExecutor,
                     ProcessOutputHandler processOutputHandler,
                     BuildToolInfo buildToolInfo,
                     ILogger logger,
                     PngProcessMode processMode,
                     int cruncherProcesses) {
        super(processExecutor,
              processOutputHandler,
              buildToolInfo,
              logger,
              processMode,
              cruncherProcesses);
    }



    @Override
    protected ProcessInfoBuilder makePackageProcessBuilder(AaptPackageConfig config) throws AaptException {
        ProcessInfoBuilder processInfoBuilder = super.makePackageProcessBuilder(config);

        List<String> args = null;
        try {
            args = (List<String>) FieldUtils.readField(processInfoBuilder, "mArgs", true);
        } catch (IllegalAccessException e) {
            throw new GradleException("getargs exception", e);
        }

        args.remove("--no-version-vectors");

        int indexD = args.indexOf("-D");
        if (indexD > 0){
            args.remove(indexD);
            args.remove(indexD);
        }

        //加入R.txt文件的生成
        String sybolOutputDir = config.getSymbolOutputDir().getAbsolutePath();
        if (!args.contains("--output-text-symbols") && null != sybolOutputDir) {
            args.add("--output-text-symbols");
            args.add(sybolOutputDir);
        }

        return processInfoBuilder;
    }
}
