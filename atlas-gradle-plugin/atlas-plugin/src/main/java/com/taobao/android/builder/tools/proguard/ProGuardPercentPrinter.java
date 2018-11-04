package com.taobao.android.builder.tools.proguard;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.api.AppVariantContext;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.Library;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Ordering;
import com.google.common.io.Files;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.tasks.transform.AtlasTransformUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ProGuardPercentPrinter {
    private final AppVariantContext appVariantContext;
    private final TransformInvocation invocation;
    private final Collection<File> transformInputs;
    private final Map<File, Library> fileLibraryMap;
    private final Map<File, File> injarsOutjarsMap;

    public ProGuardPercentPrinter(AppVariantContext appVariantContext,
            TransformInvocation invocation) {
        this.appVariantContext = appVariantContext;
        this.invocation = invocation;
        transformInputs =
                AtlasTransformUtils.getTransformInputs(this.appVariantContext, invocation);
        fileLibraryMap = buildFileLibraryMap();

        injarsOutjarsMap = buildgetInjarsOutjarsMap();
    }

    public Map<File, File> getInjarsOutjarsMap() {
        return injarsOutjarsMap;
    }

    private Map<File, File> buildgetInjarsOutjarsMap() {
        TransformOutputProvider outputProvider = invocation.getOutputProvider();
        Preconditions.checkNotNull(outputProvider);
        //if (!invocation.isIncremental()) {
        //    outputProvider.deleteAll();
        //}
        ImmutableMap.Builder<File, File> builder = ImmutableMap.builder();
        for (TransformInput input : invocation.getInputs()) {
            for (DirectoryInput dirInput : input.getDirectoryInputs()) {
                Path output = getOutputPath(invocation.getOutputProvider(), dirInput);
                //if (!dirInput.getFile().isDirectory()) {
                //    PathUtils.deleteIfExists(output);
                //}

                Path dirPath = dirInput.getFile().toPath();
                builder.put(dirPath.toFile(), output.toFile());
            }
            for (JarInput jarInput : input.getJarInputs()) {
                if (invocation.isIncremental() && jarInput.getStatus() == Status.NOTCHANGED) {
                    continue;
                }
                Path output = getOutputPath(outputProvider, jarInput);
                //PathUtils.deleteIfExists(output);
                Path path = jarInput.getFile().toPath();
                builder.put(path.toFile(), output.toFile());
            }
        }
        return builder.build();
    }

    @NonNull
    private static Path getOutputPath(@NonNull TransformOutputProvider outputProvider,
            @NonNull QualifiedContent content) {
        return outputProvider.getContentLocation(content.getName(),
                content.getContentTypes(),
                content.getScopes(),
                content instanceof DirectoryInput ? Format.DIRECTORY : Format.JAR).toPath();
    }

    private Map<File, Library> buildFileLibraryMap() {
        return Stream.concat(AtlasBuildContext.androidDependencyTrees.get(
                appVariantContext.getScope()
                        .getVariantConfiguration()
                        .getFullName()).getAwbBundles().stream(),
                Stream.of(AtlasBuildContext.androidDependencyTrees.get(appVariantContext.getScope()
                        .getVariantConfiguration()
                        .getFullName()).getMainBundle())).flatMap(awbBundle -> Stream.concat(
                awbBundle.getAllLibraryAars().stream(),
                awbBundle.getJavaLibraries().stream())).collect(Collectors.toMap(library -> {

            try (ZipFile zip = new ZipFile(getFile(library))) {

                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry zipEntry = entries.nextElement();
                    if (!zipEntry.getName().endsWith(SdkConstants.DOT_CLASS)) {
                        continue;
                    }
                    try (BufferedInputStream inputStream = new BufferedInputStream(zip
                            .getInputStream(
                            zipEntry))) {

                    }
                    Optional<File> fileStream = transformInputs.stream().filter(file -> {
                        try (ZipFile zipFile = new ZipFile(file)) {
                            ZipEntry entry = zipFile.getEntry(zipEntry.getName());
                            if (entry != null) {
                                try (InputStream is = zipFile.getInputStream(entry)) {

                                }
                                return true;
                            }
                            return false;
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }).findFirst();
                    if (fileStream.isPresent()) {
                        return fileStream.get();
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return null;
        }, library -> library));
    }

    private static File getFile(Object library) {
        File file;
        if (library instanceof AndroidLibrary) {
            file = ((AndroidLibrary) library).getJarFile();
        } else if (library instanceof JavaLibrary) {
            file = ((JavaLibrary) library).getJarFile();
        } else {

            //file =;
            throw new IllegalArgumentException(
                    "unexpected library type: " + library.getClass().getName());
        }
        return file;
    }

    public void dispose(File inOutConfigration) {
        File proGuardPercentFile = new File(inOutConfigration.getParentFile(),
                "proGuardPercent.properties");
        try (PrintWriter pw = new PrintWriter(new BufferedOutputStream(new FileOutputStream(
                proGuardPercentFile)))) {
            for (Map.Entry<File, Double> entry : getProGuardPercentMap(inOutConfigration)
                    .entrySet()) {
                final File key = entry.getKey();
                final Double value = entry.getValue();
                Library library = fileLibraryMap.get(key);
                if (library == null) {
                    pw.print(library.getResolvedCoordinates());
                } else {
                    pw.print(key);
                }
                pw.print(": ");
                printPercent(pw, value);
                pw.println();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void printPercent(PrintWriter pw, double fraction) {
        fraction *= 100;
        if (fraction < 1) {
            pw.print(String.format("%.2f", fraction));
        } else if (fraction < 10) {
            pw.print(String.format("%.1f", fraction));
        } else {
            pw.print(String.format("%.0f", fraction));
        }
        pw.print("%");
    }

    private ImmutableMap<File, Double> getProGuardPercentMap(File inOutConfigration) {
        try {
            ImmutableMap.Builder<File, Double> builder = ImmutableMap.builder();
            builder.orderEntriesByValue(Ordering.natural());
            for (Map.Entry<File, File> entry : injarsOutjarsMap.entrySet()) {
                final File key = entry.getKey();
                final File value = entry.getValue();
                long keyLength = key.length();
                long valueLength = value.length();
                builder.put(key, (double) valueLength / (double) keyLength);
            }
            List<String> lines = Files.readLines(inOutConfigration, Charsets.UTF_8);
            final int N = lines.size();
            for (int i = 0; i < N; i += 2) {
                String s = lines.get(i);
                File injarsFile = getFile(s);
                File outjarsFile = new File(lines.get(i + 1));
                long injarsFileLength = injarsFile.length();
                long outjarsFileLength = outjarsFile.length();
                builder.put(injarsFile, (double) outjarsFileLength / (double) injarsFileLength);
            }
            return builder.build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private File getFile(String s) {
        final String[] parsed = s.split(" ");
        if (parsed.length < 2) {
            throw new IllegalArgumentException("Insufficient arguments");
        }

        //String s2 = parsed[0];
        return new File(parsed[1]);
    }
}
