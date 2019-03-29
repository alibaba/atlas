package com.taobao.android.builder.tasks.transform.dex;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.dexing.DexArchiveMerger;
import com.android.builder.dexing.DexMergerTool;
import com.android.builder.dexing.DexingType;
import com.android.dx.command.dexer.DxContext;
import com.android.ide.common.process.ProcessOutput;
import com.android.tools.r8.AtlasD8Merger;
import com.android.tools.r8.CompilationMode;
import com.taobao.android.builder.hook.dex.AtlasDexArchiveMerger;
import com.taobao.android.builder.hook.dex.DexArchiveMergerHook;
import com.taobao.android.builder.tools.ReflectUtils;
import sun.security.krb5.internal.PAData;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;

/**
 * DexMergeTransformCallable
 *
 * @author zhayu.ll
 * @date 18/4/3
 */
public class DexMergeTransformCallable implements Callable<Void>{
    @NonNull
    private final DexingType dexingType;
    @NonNull private final ProcessOutput processOutput;
    @NonNull private final File dexOutputDir;
    @NonNull private final Iterable<Path> dexArchives;
    @NonNull private final ForkJoinPool forkJoinPool;
    @Nullable
    private final Path mainDexList;
    @NonNull private final DexMergerTool dexMerger;
    private final int minSdkVersion;
    private final boolean isDebuggable;

    public DexMergeTransformCallable(
            @NonNull DexingType dexingType,
            @NonNull ProcessOutput processOutput,
            @NonNull File dexOutputDir,
            @NonNull Iterable<Path> dexArchives,
            @Nullable Path mainDexList,
            @NonNull ForkJoinPool forkJoinPool,
            @NonNull DexMergerTool dexMerger,
            int minSdkVersion,
            boolean isDebuggable) {
        this.dexingType = dexingType;
        this.processOutput = processOutput;
        this.dexOutputDir = dexOutputDir;
        this.dexArchives = dexArchives;
        this.mainDexList = mainDexList;
        this.forkJoinPool = forkJoinPool;
        this.dexMerger = dexMerger;
        this.minSdkVersion = minSdkVersion;
        this.isDebuggable = isDebuggable;
    }

    @Override
    public Void call() throws Exception {
        DexArchiveMerger merger;
        switch (dexMerger) {
            case DX:
                DxContext dxContext =
                        new DxContext(
                                processOutput.getStandardOutput(), processOutput.getErrorOutput());
                merger = DexArchiveMerger.createDxDexMerger(dxContext,forkJoinPool);
                ReflectUtils.updateField(merger,"mergingStrategy",new AtlasDexArchiveMerger.AtlasDexRefMergingStrategy());
//                merger = new DexArchiveMergerHook(dxContext,new AtlasDexArchiveMerger.AtlasDexRefMergingStrategy(),forkJoinPool);
                break;
            case D8:
                int d8MinSdkVersion = minSdkVersion;
                if (d8MinSdkVersion < 21 && dexingType == DexingType.NATIVE_MULTIDEX) {
                    // D8 has baked-in logic that does not allow multiple dex files without
                    // main dex list if min sdk < 21. When we deploy the app to a device with api
                    // level 21+, we will promote legacy multidex to native multidex, but the min
                    // sdk version will be less than 21, which will cause D8 failure as we do not
                    // supply the main dex list. In order to prevent that, it is safe to set min
                    // sdk version to 21.
                    d8MinSdkVersion = 21;
                }
                merger = new AtlasD8Merger(
                        processOutput.getErrorOutput(),
                        d8MinSdkVersion,
                        isDebuggable ? CompilationMode.DEBUG : CompilationMode.RELEASE);
                break;
            default:
                throw new AssertionError("Unknown dex merger " + dexMerger.name());
        }

        merger.mergeDexArchives(dexArchives, dexOutputDir.toPath(), mainDexList, dexingType);
        return null;
    }
}
