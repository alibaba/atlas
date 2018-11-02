package com.taobao.android.builder.hook.dex;

import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.build.gradle.internal.api.VariantContext;
import com.android.builder.core.DexOptions;
import com.android.builder.core.ErrorReporter;
import com.android.builder.dexing.*;
import com.android.builder.utils.FileCache;
import com.android.ide.common.process.ProcessOutputHandler;
import com.taobao.android.builder.dependency.model.AwbBundle;
import com.taobao.android.builder.tasks.transform.dex.AtlasDexArchiveBuilderCacheHander;

import com.taobao.android.builder.tasks.transform.dex.AtlasMainDexMerger;
import com.taobao.android.builder.tasks.transform.dex.AwbDexsMerger;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * AtlasD8Creator
 *
 * @author zhayu.ll
 * @date 18/4/2
 */
public class AtlasD8Creator {

    private Collection<File>inputFiles;

    private File dexOut;

    private boolean multidex;

    private File mainDexList;

    public File getMainDexOut() {
        return mainDexOut;
    }

    public void setMainDexOut(File mainDexOut) {
        this.mainDexOut = mainDexOut;
    }

    private File mainDexOut;

    private DexOptions dexOptions;

    private int miniSdk;

    private ProcessOutputHandler processOutputHandler;

    private FileCache fileCache;

    private AtlasDexArchiveBuilderCacheHander cacheHander;

    private DexArchiveBuilder dexArchiveBuilder;

    private VariantContext variantContext;

    private boolean isDebuggable;

    private AppVariantOutputContext appVariantOutputContext;

    private static final LoggerWrapper logger =
            LoggerWrapper.getLogger(AtlasD8Creator.class);

    public AtlasD8Creator(Collection<File> inputFiles, File dexOutputFile, boolean multidex, File mainDexList, DexOptions dexOptions, int minSdkVersion, FileCache fileCache, ProcessOutputHandler processOutputHandler, VariantContext variantContext, AppVariantOutputContext variantOutputContext) {
        this.inputFiles = inputFiles;
        this.dexOut = dexOutputFile;
        this.multidex = multidex;
        this.mainDexList = mainDexList;
        this.dexOptions = dexOptions;
        this.processOutputHandler = processOutputHandler;
        this.fileCache = fileCache;
        this.miniSdk = minSdkVersion;
        this.isDebuggable = ((AppVariantContext) variantContext).getVariantData().getVariantConfiguration().getBuildType().isDebuggable();
       this.cacheHander =
                new AtlasDexArchiveBuilderCacheHander(variantContext.getProject(),
                        fileCache, dexOptions, minSdkVersion, isDebuggable, DexerTool.D8);
        this.variantContext = variantContext;
        this.appVariantOutputContext = variantOutputContext;
        dexArchiveBuilder = DexArchiveBuilder.createD8DexBuilder(minSdkVersion,isDebuggable);

    }

    public void create(AwbBundle awbBundle){
        inputFiles.stream().parallel().forEach(file -> {
            if (file.exists() && file.isDirectory()){
                logger.warning("d8 to dex:"+file.getAbsolutePath());
                try (ClassFileInput input = ClassFileInputs.fromPath(file.toPath())) {
                    dexArchiveBuilder.convert(
                            input.entries(ClassFileInput.CLASS_MATCHER),
                            dexOut.toPath(),
                            false);
                } catch (DexArchiveBuilderException ex) {
                    throw new DexArchiveBuilderException("Failed to process " + file.toPath().toString(), ex);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }else {
                logger.warning("d8 to dex:"+file.getAbsolutePath());
                File out = new File(dexOut, FilenameUtils.getBaseName(file.getName())+".dex");
                try {
                    File cacheDex = cacheHander.getCachedVersionIfPresent(file);
                    if (cacheDex == null ||!cacheDex.exists()){
                        try (ClassFileInput input = ClassFileInputs.fromPath(file.toPath())) {
                            dexArchiveBuilder.convert(
                                    input.entries(ClassFileInput.CLASS_MATCHER),
                                    out.toPath(),
                                    false);
                        } catch (DexArchiveBuilderException ex) {
                            throw new DexArchiveBuilderException("Failed to process " + file.toPath().toString(), ex);
                        }
                        if (out.exists()){
                            cacheHander.populateCache(file,out);
                        }
                    }else {
                        logger.warning("hit d8 cache:"+file.getAbsolutePath());
                        Files.copy(
                                cacheDex.toPath(),
                                out.toPath(),
                                StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }

        });

        ErrorReporter errorReporter = variantContext.getScope().getGlobalScope().getAndroidBuilder().getErrorReporter();
        if (!awbBundle.isMainBundle()) {
            AwbDexsMerger awbDexsMerger = new AwbDexsMerger(DexingType.MONO_DEX, null, errorReporter, DexMergerTool.D8, miniSdk, isDebuggable, appVariantOutputContext);
            awbDexsMerger.merge(awbBundle);
        }else {
            AtlasMainDexMerger atlasMainDexMerger = new AtlasMainDexMerger(multidex ? DexingType.LEGACY_MULTIDEX:DexingType.MONO_DEX,variantContext.getProject().files(mainDexList),errorReporter,DexMergerTool.D8,miniSdk,isDebuggable,appVariantOutputContext);
            List<File>dexFiles = new ArrayList();
            File[][]mergeDexs = {new File[0]};
            dexFiles.addAll(FileUtils.listFiles(dexOut,new String[]{"dex"},true));
            mergeDexs[0] = dexOut.listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return pathname.isDirectory()||pathname.getName().endsWith(".dex");
                }
            });

            atlasMainDexMerger.merge(dexFiles,mainDexOut,mergeDexs);
        }

    }
}
