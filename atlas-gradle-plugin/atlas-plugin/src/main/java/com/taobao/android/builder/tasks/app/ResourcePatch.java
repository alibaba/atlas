package com.taobao.android.builder.tasks.app;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.annotation.Nullable;

import com.android.apksig.apk.ApkUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteStreams;
import com.google.devrel.gmscore.tools.apk.arsc.Chunk;
import com.google.devrel.gmscore.tools.apk.arsc.PackageChunk;
import com.google.devrel.gmscore.tools.apk.arsc.ResourceFile;
import com.google.devrel.gmscore.tools.apk.arsc.ResourceTableChunk;
import com.google.devrel.gmscore.tools.apk.arsc.TypeChunk;
import com.google.devrel.gmscore.tools.apk.arsc.TypeChunk.Entry;
import com.google.devrel.gmscore.tools.apk.arsc.TypeSpecChunk;
import com.taobao.android.builder.tools.zip.ZipUtils;
import org.apache.commons.io.FileUtils;

/**
 * Created by chenhjohn on 2018/2/27.
 */

public class ResourcePatch {
    public static void makePatchable(@Nullable /*@ApkPath*/ File apk) throws IOException {
        ResourceTableChunk resourceTableChunk = providesResourceTableChunk(apk);
        PackageChunk packageChunk = Iterables.getOnlyElement(resourceTableChunk.getPackages());
        Collection<TypeChunk> typeChunks = packageChunk.getTypeChunks();
        for (TypeChunk typeChunk : typeChunks) {
            int totalEntryCount = typeChunk.getTotalEntryCount();
            Entry lastEntry = typeChunk.getEntries().get(totalEntryCount - 1);
            for (int i = 0; i < 256; i++) {
                typeChunk.put(totalEntryCount + i, lastEntry);
            }
        }
        Collection<TypeSpecChunk> typeSpecChunks = packageChunk.getTypeSpecChunks();
        for (TypeSpecChunk typeSpecChunk : typeSpecChunks) {
            for (int i = 0; i < 256; i++) {
                typeSpecChunk.add(0);
            }
        }
        byte[] bytes = resourceTableChunk.toByteArray();
        File file = new File(apk.getParentFile(), "resources.arsc" + "_tmp");
        FileUtils.writeByteArrayToFile(file, bytes);
        File tmpFile = new File(apk.getParentFile(), apk.getName() + "_tmp");
        ZipUtils.addFileToZipFile(apk, tmpFile, file, "resources.arsc", true);
        apk.delete();
        tmpFile.renameTo(apk);
    }

    public static ResourceTableChunk providesResourceTableChunk(@Nullable /*@ApkPath*/ File apk) throws IOException {
        Preconditions.checkNotNull(apk, "APK is required. Did you forget --apk=/my/app.apk?");
        byte[] resourceBytes = getFile(apk, "resources.arsc");
        if (resourceBytes == null) {
            throw new IOException(String.format("Unable to find %s in APK.", new Object[] {"resources.arsc"}));
        } else {
            List<Chunk> chunks = (new ResourceFile(resourceBytes)).getChunks();
            Preconditions.checkState(chunks.size() == 1,
                "%s should only have one root chunk.",
                new Object[] {"resources.arsc"});
            Chunk resourceTable = (Chunk)chunks.get(0);
            Preconditions.checkState(resourceTable instanceof ResourceTableChunk,
                "%s root chunk must be a ResourceTableChunk.",
                new Object[] {"resources.arsc"});
            return (ResourceTableChunk)resourceTable;
        }
    }

    /**
     * Returns a file whose name matches {@code filename}, or null if no file was found.
     *
     * @param apkFile  The file containing the apk zip archive.
     * @param filename The full filename (e.g. res/raw/foo.bar).
     * @return A byte array containing the contents of the matching file, or null if not found.
     * @throws IOException Thrown if there's a matching file, but it cannot be read from the apk.
     */
    public static byte[] getFile(File apkFile, String filename) throws IOException {
        Map<String, byte[]> files = getFiles(apkFile, Pattern.quote(filename));
        return files.get(filename);
    }

    /**
     * Returns all files in an apk that match a given regular expression.
     *
     * @param apkFile The file containing the apk zip archive.
     * @param regex   A regular expression to match the requested filenames.
     * @return A mapping of the matched filenames to their byte contents.
     * @throws IOException Thrown if a matching file cannot be read from the apk.
     */
    public static Map<String, byte[]> getFiles(File apkFile, String regex) throws IOException {
        return getFiles(apkFile, Pattern.compile(regex));
    }

    /**
     * Returns all files in an apk that match a given regular expression.
     *
     * @param apkFile The file containing the apk zip archive.
     * @param regex   A regular expression to match the requested filenames.
     * @return A mapping of the matched filenames to their byte contents.
     * @throws IOException Thrown if a matching file cannot be read from the apk.
     */
    public static Map<String, byte[]> getFiles(File apkFile, Pattern regex) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();  // Retain insertion order
        // Extract apk
        try (ZipFile apkZip = new ZipFile(apkFile)) {
            Enumeration<? extends ZipEntry> zipEntries = apkZip.entries();
            while (zipEntries.hasMoreElements()) {
                ZipEntry zipEntry = zipEntries.nextElement();
                // Visit all files with the given extension
                if (regex.matcher(zipEntry.getName()).matches()) {
                    // Map class name to definition
                    try (InputStream is = new BufferedInputStream(apkZip.getInputStream(zipEntry))) {
                        files.put(zipEntry.getName(), ByteStreams.toByteArray(is));
                    }
                }
            }
        }
        return files;
    }
}
