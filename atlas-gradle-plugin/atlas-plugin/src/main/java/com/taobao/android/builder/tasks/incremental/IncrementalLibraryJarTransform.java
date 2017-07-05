package com.taobao.android.builder.tasks.incremental;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.internal.tasks.LibraryJarTransform;
import com.android.build.gradle.internal.transforms.JarMerger;
import com.android.build.gradle.tasks.annotations.TypedefRemover;
import com.android.builder.packaging.ZipAbortException;
import com.android.builder.packaging.ZipEntryFilter;
import com.android.utils.FileUtils;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Closer;

/**
 * Created by chenhjohn on 2017/7/5.
 */

public class IncrementalLibraryJarTransform extends LibraryJarTransform {

    @NonNull
    private final File mainClassLocation;

    @NonNull
    private final File localJarsLocation;

    @NonNull
    private final String packagePath;

    private final boolean packageBuildConfig;

    @NonNull
    private final File typedefRecipe;

    @Nullable
    private List<ExcludeListProvider> excludeListProviders;

    public IncrementalLibraryJarTransform(File mainClassLocation, File localJarsLocation, File typedefRecipe,
                                          String packageName, boolean packageBuildConfig) {
        super(mainClassLocation, localJarsLocation, typedefRecipe, packageName, packageBuildConfig);
        this.mainClassLocation = mainClassLocation;
        this.localJarsLocation = localJarsLocation;
        this.typedefRecipe = typedefRecipe;
        this.packagePath = packageName.replace(".", "/");
        this.packageBuildConfig = packageBuildConfig;
    }

    public void addExcludeListProvider(ExcludeListProvider provider) {
        if (excludeListProviders == null) {
            excludeListProviders = Lists.newArrayList();
        }
        excludeListProviders.add(provider);
    }

    @Override
    public boolean isIncremental() {
        // TODO make incremental
        return true;
    }

    @Override
    public void transform(@NonNull TransformInvocation invocation)
        throws IOException, TransformException, InterruptedException {
        // non incremental transform, need to clear out outputs.
        // main class jar will get rewritten, just delete local jar folder content.
        FileUtils.deleteDirectoryContents(localJarsLocation);
        List<String> excludes = Lists.newArrayListWithExpectedSize(5);
        // these must be regexp to match the zip entries
        excludes.add(".*/R.class$");
        excludes.add(".*/R\\$(.*).class$");
        excludes.add(packagePath + "/Manifest.class$");
        excludes.add(packagePath + "/Manifest\\$(.*).class$");
        if (!packageBuildConfig) {
            excludes.add(packagePath + "/BuildConfig.class$");
        }
        if (excludeListProviders != null) {
            for (ExcludeListProvider provider : excludeListProviders) {
                List<String> list = provider.getExcludeList();
                if (list != null) {
                    excludes.addAll(list);
                }
            }
        }
        // create Pattern Objects.
        List<Pattern> patterns = excludes.stream().map(Pattern::compile).collect(Collectors.toList());
        Collection<TransformInput> inputs = invocation.getReferencedInputs();
        if (invocation.isIncremental()) {
            incrementalUpdate(inputs, patterns);
        } else {
            fullCopy(inputs, patterns);
        }
    }

    private void fullCopy(Collection<TransformInput> inputs, List<Pattern> patterns) throws IOException {
        // first look for what inputs we have. There shouldn't be that many inputs so it should
        // be quick and it'll allow us to minimize jar merging if we don't have to.
        List<QualifiedContent> mainScope = Lists.newArrayList();
        List<QualifiedContent> locaJlJarScope = Lists.newArrayList();
        for (TransformInput input : inputs) {
            for (QualifiedContent qualifiedContent : Iterables.concat(input.getJarInputs(),
                                                                      input.getDirectoryInputs())) {
                if (qualifiedContent.getScopes().contains(Scope.PROJECT)) {
                    // even if the scope contains both project + local jar, we treat this as main
                    // scope.
                    mainScope.add(qualifiedContent);
                } else {
                    locaJlJarScope.add(qualifiedContent);
                }
            }
        }
        // process main scope.
        if (mainScope.isEmpty()) {
            throw new RuntimeException("Empty Main scope for " + getName());
        }
        if (mainScope.size() == 1) {
            QualifiedContent content = mainScope.get(0);
            if (content instanceof JarInput) {
                copyJarWithContentFilter(content.getFile(), mainClassLocation, patterns);
            } else {
                jarFolderToRootLocation(content.getFile(), patterns);
            }
        } else {
            mergeToRootLocation(mainScope, patterns);
        }
        // process local scope
        processLocalJars(locaJlJarScope);
    }

    private void incrementalUpdate(Collection<TransformInput> inputs, List<Pattern> patterns) throws IOException {
        inputs.forEach(input -> input.getDirectoryInputs().forEach(directoryInput -> {
            directoryInput.getChangedFiles().forEach((file, status) -> {
                switch (status) {
                    case ADDED:
                    case CHANGED:
                        break;
                    case REMOVED:
                        break;
                }
            });
        }));
    }

    private void mergeToRootLocation(@NonNull List<QualifiedContent> qualifiedContentList,
                                     @NonNull final List<Pattern> excludes) throws IOException {
        JarMerger jarMerger = new JarMerger(mainClassLocation);
        jarMerger.setFilter(archivePath -> checkEntry(excludes, archivePath));
        for (QualifiedContent content : qualifiedContentList) {
            if (content instanceof JarInput) {
                jarMerger.addJar(content.getFile());
            } else {
                jarMerger.addFolder(content.getFile());
            }
        }
        jarMerger.close();
    }

    private void processLocalJars(@NonNull List<QualifiedContent> qualifiedContentList) throws IOException {
        // first copy the jars (almost) as is, and remove them from the list.
        // then we'll make a single jars that contains all the folders.
        // Note that we do need to remove the resources from the jars since they have been merged
        // somewhere else.
        // TODO: maybe do the folders separately to handle incremental?
        ZipEntryFilter classOnlyFilter = path -> path.endsWith(SdkConstants.DOT_CLASS);
        Iterator<QualifiedContent> iterator = qualifiedContentList.iterator();
        while (iterator.hasNext()) {
            QualifiedContent content = iterator.next();
            if (content instanceof JarInput) {
                // we need to copy the jars but only take the class files as the resources have
                // been merged into the main jar.
                copyJarWithContentFilter(content.getFile(),
                                         new File(localJarsLocation, content.getFile().getName()),
                                         classOnlyFilter);
                iterator.remove();
            }
        }
        // now handle the folders.
        if (!qualifiedContentList.isEmpty()) {
            JarMerger jarMerger = new JarMerger(new File(localJarsLocation, "otherclasses.jar"));
            jarMerger.setFilter(classOnlyFilter);
            for (QualifiedContent content : qualifiedContentList) {
                jarMerger.addFolder(content.getFile());
            }
            jarMerger.close();
        }
    }

    private void classesToRootLocation(@NonNull File file, @NonNull List<File> classesfiles,
                                       @NonNull final List<Pattern> excludes) throws IOException {
        JarMerger jarMerger = new JarMerger(mainClassLocation);
    }

    private void jarFolderToRootLocation(@NonNull File file, @NonNull final List<Pattern> excludes) throws IOException {
        JarMerger jarMerger = new JarMerger(mainClassLocation);
        jarMerger.setFilter(archivePath -> checkEntry(excludes, archivePath));
        if (typedefRecipe.isFile()) {
            jarMerger.setTypedefRemover(new TypedefRemover().setTypedefFile(typedefRecipe));
        }
        jarMerger.addFolder(file);
        jarMerger.close();
    }

    private static void copyJarWithContentFilter(@NonNull File from, @NonNull File to,
                                                 @NonNull final List<Pattern> excludes) throws IOException {
        copyJarWithContentFilter(from, to, archivePath -> {
            return checkEntry(excludes, archivePath);
        });
    }

    private static void copyJarWithContentFilter(@NonNull File from, @NonNull File to, @Nullable ZipEntryFilter filter)
        throws IOException {
        byte[] buffer = new byte[4096];
        try (Closer closer = Closer.create()) {
            FileOutputStream fos = closer.register(new FileOutputStream(to));
            BufferedOutputStream bos = closer.register(new BufferedOutputStream(fos));
            ZipOutputStream zos = closer.register(new ZipOutputStream(bos));
            FileInputStream fis = closer.register(new FileInputStream(from));
            BufferedInputStream bis = closer.register(new BufferedInputStream(fis));
            ZipInputStream zis = closer.register(new ZipInputStream(bis));
            // loop on the entries of the intermediary package and put them in the final package.
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (filter != null && !filter.checkEntry(name)) {
                    continue;
                }
                JarEntry newEntry;
                // Preserve the STORED method of the input entry.
                if (entry.getMethod() == JarEntry.STORED) {
                    newEntry = new JarEntry(entry);
                } else {
                    // Create a new entry so that the compressed len is recomputed.
                    newEntry = new JarEntry(name);
                }
                newEntry.setLastModifiedTime(JarMerger.ZERO_TIME);
                newEntry.setLastAccessTime(JarMerger.ZERO_TIME);
                newEntry.setCreationTime(JarMerger.ZERO_TIME);
                // add the entry to the jar archive
                zos.putNextEntry(newEntry);
                // read the content of the entry from the input stream, and write it into the archive.
                int count;
                while ((count = zis.read(buffer)) != -1) {
                    zos.write(buffer, 0, count);
                }
                zos.closeEntry();
                zis.closeEntry();
            }
        } catch (ZipAbortException e) {
            throw new IOException(e);
        }
    }

    private static boolean checkEntry(@NonNull List<Pattern> patterns, @NonNull String name) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(name).matches()) {
                return false;
            }
        }
        return true;
    }
}
