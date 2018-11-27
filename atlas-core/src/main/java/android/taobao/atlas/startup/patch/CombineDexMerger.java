package android.taobao.atlas.startup.patch;

import android.taobao.atlas.util.StringUtils;

import java.io.*;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * CombineDexMerger
 *
 * @author zhayu.ll
 * @date 18/5/10
 */
public class CombineDexMerger extends PatchMerger{


    private static final int BUFFER_SIZE = 0x4000;

    private static final String DEX_SUFFIX = ".dex";

    private static final String CLASS_SUFFIX = "classes";
    public CombineDexMerger(PatchVerifier patchVerifier) {
        super(patchVerifier);
    }

    @Override
    public boolean merge(File sourceFile, File patchFile, File newFile) {
        ZipOutputStream target = null;
        ZipFile patchZip = null;
        ZipFile rawZip = null;
        try {
             patchZip = new ZipFile(patchFile);
             rawZip = new ZipFile(sourceFile);
            boolean dexPatch = patchFile.getAbsolutePath().contains("dexpatch");
            target = new ZipOutputStream(new FileOutputStream(newFile));
            int dexIndex = 1;

            // first, copy source content
            Enumeration<? extends ZipEntry> sourceEntries = patchZip.entries();
            while (sourceEntries.hasMoreElements()) {
                ZipEntry e = sourceEntries.nextElement();
                //META-INF重复
                if (dexPatch && e.getName().startsWith("META-INF")) {
                    continue;
                }
                ZipEntry out = new ZipEntry(e.getName());
                target.putNextEntry(out);
                if (!e.isDirectory()) {
                    copy(patchZip.getInputStream(e), target);
                }
            }

            if (!dexPatch || (dexPatch && KernalBundle.kernalBundle == null)) {
                // second copy main dex from base.apk
                do {
                    ZipEntry entry = rawZip.getEntry(String.format("%s%s%s", CLASS_SUFFIX, dexIndex > 1 ? dexIndex : "", DEX_SUFFIX));
                    if (entry != null && !entry.isDirectory()) {
                        ZipEntry targetEntry = new ZipEntry(getUpdatedDexEntryName(entry.getName()));
                        target.putNextEntry(targetEntry);
                        copy(rawZip.getInputStream(entry), target);
                        dexIndex++;
                    } else {
                        return true;
                    }
                } while (true);
            } else {
                // second copy all from com.taobao.maindex.zip
                Enumeration<? extends ZipEntry> rawEntries = rawZip.entries();
                while (rawEntries.hasMoreElements()) {
                    ZipEntry e = rawEntries.nextElement();
                    ZipEntry out = e.getName().startsWith(CLASS_SUFFIX) && e.getName().endsWith(DEX_SUFFIX)
                            ? new ZipEntry(getUpdatedDexEntryName(e.getName()))
                            : new ZipEntry(e.getName());
                    target.putNextEntry(out);
                    if (!e.isDirectory()) {
                        copy(rawZip.getInputStream(e), target);
                    }
                }
                return true;
            }

        }catch (Exception e){
            e.printStackTrace();

        }finally {
            try {
                if (target != null) {
                    target.closeEntry();
                    target.close();
                }
                if (rawZip != null)
                    rawZip.close();
                if (patchZip != null){
                    patchZip.close();
                }
                if (patchVerifier!= null && newFile.exists()){
                    return patchVerifier.verify(newFile);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        return true;
    }

    private static String getUpdatedDexEntryName(String originalEntryName){
        if(originalEntryName.equals("classes.dex")){
            return "classes2.dex";
        }else{
            int dexIndex = Integer.parseInt(StringUtils.substringBetween(originalEntryName,"classes",".dex"));
            return String.format("classes%s.dex",++dexIndex);
        }
    }

    public static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] readContent = new byte[BUFFER_SIZE];
        int bytesRead;
        while ((bytesRead = input.read(readContent)) != -1) {
            output.write(readContent, 0, bytesRead);
        }
    }
}
