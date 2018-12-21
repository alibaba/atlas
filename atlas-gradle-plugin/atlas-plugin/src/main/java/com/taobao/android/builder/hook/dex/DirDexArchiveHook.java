package com.taobao.android.builder.hook.dex;

import com.android.annotations.NonNull;
import com.android.builder.dexing.DexArchive;
import com.android.builder.dexing.DexArchiveEntry;
import com.android.builder.dexing.DexArchives;
import com.android.utils.FileUtils;
import com.android.utils.PathUtils;
import com.google.common.collect.ImmutableList;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;

/**
 * 创建日期：2018/12/21 on 下午1:25
 * 描述:
 * 作者:zhayu.ll
 */
public class DirDexArchiveHook implements DexArchive {

    @NonNull
    private final Path rootDir;

    public DirDexArchiveHook(@NonNull Path rootDir) {
        this.rootDir = rootDir;
    }

    @NonNull
    @Override
    public Path getRootPath() {
        return rootDir;
    }

    @Override
    public void addFile(@NonNull String relativePath, byte[] bytes, int offset, int end)
            throws IOException {
        Path finalPath = rootDir.resolve(relativePath);
        Files.createDirectories(finalPath.getParent());
        try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(finalPath))) {
            os.write(bytes, offset, end);
            os.flush();
        }
    }

    @Override
    public void removeFile(@NonNull String relativePath) throws IOException {
        Path finalPath = rootDir.resolve(relativePath);
        if (Files.isDirectory(finalPath)) {
            FileUtils.deleteDirectoryContents(finalPath.toFile());
        }
        Files.deleteIfExists(finalPath);
    }

    @Override
    @NonNull
    public List<DexArchiveEntry> getFiles() throws IOException {
        ImmutableList.Builder<DexArchiveEntry> builder = ImmutableList.builder();

        Iterator<Path> files =
                Files.walk(getRootPath()).filter(DexArchives.DEX_ENTRY_FILTER).iterator();

        while (files.hasNext()) {
            builder.add(createEntry(files.next()));
        }

        return builder.build();
    }

    @Override
    public void close() throws IOException {
        // do nothing
    }

    private DexArchiveEntry createEntry(@NonNull Path dexFile) throws IOException {
        byte[] content = Files.readAllBytes(dexFile);
        Path relativePath = getRootPath().relativize(dexFile);

        return new DexArchiveEntry(content, PathUtils.toSystemIndependentPath(relativePath));
    }
}
