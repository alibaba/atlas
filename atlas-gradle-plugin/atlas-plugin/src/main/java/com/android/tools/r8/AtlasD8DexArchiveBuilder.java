package com.android.tools.r8;

import com.android.annotations.NonNull;
import com.android.builder.dexing.ClassFileEntry;
import com.android.builder.dexing.DexArchiveBuilder;
import com.android.builder.dexing.DexArchiveBuilderException;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.OutputMode;
import com.google.common.util.concurrent.MoreExecutors;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.stream.Stream;

/**
 * AtlasD8DexArchiveBuilder
 *
 * @author zhayu.ll
 * @date 18/4/26
 */
public class AtlasD8DexArchiveBuilder extends DexArchiveBuilder{

    private int minSdkVersion;

    private boolean isDebug;

    private Path mainDexList;

    private boolean awb;

    public AtlasD8DexArchiveBuilder(int minSdkVersion, boolean isDebug,Path mainDexList,boolean awb){

        this.minSdkVersion = minSdkVersion;
        this.isDebug = isDebug;
        this.mainDexList = mainDexList;
        this.awb = awb;

    }

    @Override
    public void convert(Stream<ClassFileEntry> input, Path output, boolean isIncremental) throws DexArchiveBuilderException {
        try {
            Iterator<byte[]> data = input.map(AtlasD8DexArchiveBuilder::readAllBytes).iterator();
            if (!data.hasNext()) {
                // nothing to do here, just return
                return;
            }

            D8Command.Builder builder =
                    D8Command.builder()
                            .setMode(isDebug?CompilationMode.DEBUG:CompilationMode.RELEASE)
                            .setMinApiLevel(minSdkVersion)
                            .setOutputMode(OutputMode.Indexed);

            while (data.hasNext()) {
                builder.addClassProgramData(data.next());
            }

            if (!awb && isDebug && minSdkVersion < 21){
                if (!mainDexList.toFile().exists()){
                    createNew(mainDexList);
                }
                builder.addMainDexListFiles(mainDexList);
            }

            builder.setOutputPath(output);
            AtlasD8.run(builder.build(), MoreExecutors.newDirectExecutorService());
        } catch (Throwable e) {
            throw new DexArchiveBuilderException(e);
        }
    }


    //supply fake maindexlist for d8 compile,we will generate real maindexlist in next step for merge dex
    private void createNew(Path mainDexList) throws IOException {
        FileUtils.writeTextFile(mainDexList, Arrays.asList("android/taobao/atlas/bundleInfo/AtlasBundleInfoGenerator.class","android/taobao/atlas/framework/FrameworkProperties.class"));


    }

    @NonNull
    private static byte[] readAllBytes(@NonNull ClassFileEntry entry) {
        try {
            return entry.readAllBytes();
        } catch (IOException ex) {
            throw new DexArchiveBuilderException(ex);
        }
    }
}
