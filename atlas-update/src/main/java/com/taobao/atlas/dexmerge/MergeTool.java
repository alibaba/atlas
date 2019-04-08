package com.taobao.atlas.dexmerge;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.taobao.atlas.runtime.RuntimeVariables;
import android.taobao.atlas.startup.patch.releaser.DexReleaser;
import android.text.TextUtils;
import android.util.Log;
import com.alibaba.patch.PatchUtils;
import com.taobao.atlas.dex.ClassDef;
import com.taobao.atlas.dex.Dex;
import com.taobao.atlas.dex.ProtoId;
import com.taobao.atlas.dex.util.FileUtils;
import com.taobao.atlas.dexmerge.dx.merge.CollisionPolicy;
import com.taobao.atlas.dexmerge.dx.merge.DexMerger;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Created by lilong on 16/7/5.
 */
public class MergeTool {
    private static final int BUFFEREDSIZE = 1024;


    private static String noPatchDexIndex;

    private static int patchVersion = 1;


    private static TreeMap<Integer, List<String>> classes = new TreeMap();


    public static void mergePrepare(File oldBundle, List<ZipEntry> entryList, String patchName, File targetBundle, boolean isDiff, MergeExcutorServices.PrepareCallBack prepareCallBack) throws IOException, MergeException {
        if (oldBundle.exists() && entryList != null) {
            ZipFile sourceZip = new ZipFile(oldBundle);
            try {
                File tempFile = File.createTempFile(patchName, null, targetBundle.getParentFile());
                tempFile.deleteOnExit();
                if (patchName.equals(MergeConstants.MAIN_DEX)) {
                    createNewMainApkInternal(sourceZip, entryList, tempFile, isDiff, prepareCallBack);
                } else {
                    createNewBundleInternal(patchName, sourceZip, entryList, tempFile, isDiff, prepareCallBack);
                }
                if (tempFile.exists()) {
                    if (!tempFile.renameTo(targetBundle)) throw new IOException("merge failed!");
//                        deleteDir(patchBundle);
                }
            } catch (IOException e) {
                targetBundle.delete();
                targetBundle = null;
                throw new IOException(e);
            } finally {
                try {
                    sourceZip.close();
                } catch (Throwable e) {
                }
            }
        }

    }

    private static void createNewMainApkInternal(ZipFile sourceZip, List<ZipEntry> entryList, File tempFile, boolean isDiff, MergeExcutorServices.PrepareCallBack prepareCallBack) throws IOException {
        byte[] buffer = new byte[BUFFEREDSIZE];
        ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(tempFile)));
        BufferedOutputStream bo = new BufferedOutputStream(out);
        InputStream in;
        //先写入source中未变的文件
        java.util.Enumeration e = sourceZip.entries();
        int i = 1;
        File mainDexFile = new File(tempFile.getParentFile(), "libcom_taobao_maindex.zip");
        inputStreamToFile(MergeExcutorServices.sZipPatch.getInputStream(entryList.get(0)), mainDexFile);
        ZipFile zipFile = new ZipFile(mainDexFile);
        if (zipFile.getEntry(MergeConstants.DEX_PATCH_META) != null) {
            readPatchClasses(zipFile, zipFile.getEntry(MergeConstants.DEX_PATCH_META));
        }
        Enumeration entries = zipFile.entries();
        switch (patchVersion) {
            case 1:
                i = writePatch(entries, out, bo,zipFile, i);

                writeSourceApkDex(e, sourceZip,bo, out, i);
                break;

            case 2:
                while (e.hasMoreElements()) {
                        ZipEntry zipEnt = (ZipEntry) e.nextElement();
                        String name = zipEnt.getName();
                        if (name.endsWith(MergeConstants.DEX_SUFFIX)) {
                            String s = name.substring(7);
                            Integer sourceDexIndex = s.startsWith(".") ? 0 : Integer.valueOf(s.substring(0, s.indexOf(".")));
                            if (classes.get(sourceDexIndex) != null) {
                                Log.e("MergeTool", "process sourceDex:" + sourceDexIndex + " and classes size:" + classes.get(sourceDexIndex).size());
                                InputStream inputStream = sourceZip.getInputStream(zipEnt);
                                Dex dex = processDex(inputStream, classes.get(sourceDexIndex));
                                ZipEntry newEntry = new ZipEntry(zipEnt.getName());
                                out.putNextEntry(newEntry);
                                write(new ByteArrayInputStream(dex.getBytes()), out, buffer);
                                bo.flush();
                            } else if (isArt() && !isEnhanceDex(name)) {
                                ZipEntry newEntry = new ZipEntry(zipEnt.getName());
                                out.putNextEntry(newEntry);
                                write(sourceZip.getInputStream(zipEnt), out, buffer);
                                bo.flush();
                            }
                            i++;
                        }
                    }

                writePatch(entries, out, bo,zipFile, i);
                break;

            default:
                break;

        }

        mainDexFile.delete();
        zipFile.close();
        closeQuitely(out);
        closeQuitely(bo);


    }

    private static int writeSourceApkDex(Enumeration e, ZipFile sourceZip, BufferedOutputStream bo,ZipOutputStream out, int i) throws IOException {
        byte[] buffer = new byte[BUFFEREDSIZE];
        while (e.hasMoreElements()) {
            ZipEntry zipEnt = (ZipEntry) e.nextElement();
            String name = zipEnt.getName();
            if (!name.endsWith(MergeConstants.DEX_SUFFIX)) {
                continue;
            }
            ZipEntry newEntry = new ZipEntry(String.format("%s%s%s", MergeConstants.CLASS, i == 1 ? "" : i, MergeConstants.DEX_SUFFIX));
            if (isArt() && !isEnhanceDex(name)) {
                InputStream inputStream = sourceZip.getInputStream(zipEnt);
                out.putNextEntry(newEntry);
                write(inputStream, out, buffer);
                bo.flush();
            } else if (!isArt()) {
                String s = name.substring(7);
                Integer sourceDexIndex = s.startsWith(".") ? 0 : Integer.valueOf(s.substring(0, s.indexOf(".")));
                if (classes.get(sourceDexIndex) != null) {
                    Log.e("MergeTool", "process sourceDex:" + sourceDexIndex + " and classes size:" + classes.get(sourceDexIndex).size());
                    InputStream inputStream = sourceZip.getInputStream(zipEnt);
                    Dex dex = processDex(inputStream, classes.get(sourceDexIndex));
                    out.putNextEntry(newEntry);
                    write(new ByteArrayInputStream(dex.getBytes()), out, buffer);
                    bo.flush();
                }
            }
            i++;
        }

        return i;

    }

    private static int writePatch(Enumeration entries, ZipOutputStream out, BufferedOutputStream bo,ZipFile zipFile, int i) throws IOException {
        byte[] buffer = new byte[BUFFEREDSIZE];
        while (entries.hasMoreElements()) {
            ZipEntry zipEnt = (ZipEntry) entries.nextElement();
            String name = zipEnt.getName();
            if (name.endsWith(MergeConstants.DEX_SUFFIX)) {
                ZipEntry zipEntry = new ZipEntry(String.format("%s%s%s", MergeConstants.CLASS, i == 1 ? "" : i, MergeConstants.DEX_SUFFIX));
                out.putNextEntry(zipEntry);
                write(zipFile.getInputStream(zipEnt), out, buffer);
                bo.flush();
                i++;
                continue;
            }
            ZipEntry newEntry = new ZipEntry(name);
            if (name.contains("raw/") || name.contains("assets/") || name.equals("resources.arsc")) {
                newEntry.setMethod(ZipEntry.STORED);
                newEntry.setCrc(zipEnt.getCrc());
                newEntry.setSize(zipEnt.getSize());
            }
            out.putNextEntry(newEntry);
            InputStream in = zipFile.getInputStream(zipEnt);
            write(in, out, buffer);
            bo.flush();
        }

        return i;

    }

    private static boolean isEnhanceDex(String name) {

        return !TextUtils.isEmpty(noPatchDexIndex) && name.equals(String.format("classes%s.dex", noPatchDexIndex)) && (Build.VERSION.SDK_INT > 27 || Build.VERSION.SDK_INT < 21);

    }

    private static void readPatchClasses(ZipFile zipFile, ZipEntry entry) {
        try {
            InputStream is = zipFile.getInputStream(entry);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is));
            String s = null;
            while (!TextUtils.isEmpty(s = bufferedReader.readLine())) {
                String dexIndex = s.split("-")[0];
                String clazz = s.split("-")[1];
                if (!TextUtils.isEmpty(clazz) && clazz.equals(MergeConstants.NO_PATCH_DEX)) {
                    noPatchDexIndex = dexIndex;
                    continue;
                }

                if (!TextUtils.isEmpty(clazz) && clazz.equals(MergeConstants.PATCH_VERSION)) {
//                    patchVersion = Integer.valueOf(dexIndex);
//                    notifyPatchVersion(patchVersion);
                    continue;
                }

                if (classes.containsKey(Integer.valueOf(dexIndex))) {
                    classes.get(Integer.valueOf(dexIndex)).add(clazz);
                } else {
                    List<String> cc = new ArrayList<>();
                    cc.add(clazz);
                    classes.put(Integer.valueOf(dexIndex), cc);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            classes.clear();
        }

    }

    private static void notifyPatchVersion(int patchVersion) {
        Intent intent = new Intent("com.taobao.atlas.intent.PATCH_VERSION");
        intent.putExtra("patch_version", patchVersion);
        RuntimeVariables.androidApplication.sendBroadcast(intent);
    }


    private static Dex processDex(InputStream inputStream, List<String> patchClassNames) throws IOException {
        Dex oringnalDex = new Dex(inputStream);
        DexMerger dexMerger = new DexMerger(new Dex[]{oringnalDex, new Dex(0)}, CollisionPolicy.FAIL);
        dexMerger.setRemoveTypeClasses(patchClassNames);
        dexMerger.setCompactWasteThreshold(1);
        Dex dex = dexMerger.merge();
        return dex;
    }

    private static boolean isBundleFileUpdated(ZipEntry sourceEntry, List<ZipEntry> entryList, AtomicReference patchEntry) {
        for (ZipEntry entry : entryList) {
            if (entry.getName().contains(sourceEntry.getName())) {
                patchEntry.compareAndSet(null, entry);
                return true;
            }
        }
        return false;
    }

    public static void createNewBundleInternal(String patchBundleName, ZipFile source, List<ZipEntry> entryList, File target, boolean isDiff, MergeExcutorServices.PrepareCallBack prepareCallBack) throws IOException, MergeException {

        // get a temp file
        byte[] buffer = new byte[BUFFEREDSIZE];
        ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(target)));
        BufferedOutputStream bo = new BufferedOutputStream(out);
        InputStream in;
        //先写入source中未变的文件
        java.util.Enumeration e = source.entries();
        Boolean isSourceHasDex = false;
        Boolean isPatchHasDex = false;
        ZipEntry originalDex = null;
        ZipEntry patchDex = null;
        File outDex;
        AtomicReference<ZipEntry> zipEntryAtomicReference = new AtomicReference<>();
        while (e.hasMoreElements()) {
            ZipEntry zipEnt = (ZipEntry) e.nextElement();
            String name = zipEnt.getName();
            /**
             * 差量更新需要做dex merge, 过滤出classes.dex
             */
            if (isDiff && name.equals(MergeConstants.CLASS + MergeConstants.DEX_SUFFIX)) {
//                originalDex = zipEnt;
                isSourceHasDex = true;
                continue;
            }

            boolean toBeDeleted = isBundleFileUpdated(zipEnt, entryList, zipEntryAtomicReference);
            if (!toBeDeleted) {

                ZipEntry newEntry = new ZipEntry(name);
                if (MergeExcutorServices.os == MergeExcutorServices.OS.mac) {
                    if (name.contains("raw/") || name.contains("assets/")) {
                        newEntry.setMethod(ZipEntry.STORED);
                        newEntry.setCrc(zipEnt.getCrc());
                        newEntry.setSize(zipEnt.getSize());
                    }
                } else {
                    if (name.contains("raw\\") || name.contains("assets\\")) {
                        newEntry.setMethod(ZipEntry.STORED);
                        newEntry.setCrc(zipEnt.getCrc());
                        newEntry.setSize(zipEnt.getSize());
                    }
                }
                out.putNextEntry(newEntry);
                in = source.getInputStream(zipEnt);
                write(in, out, buffer);
                bo.flush();

            }
            if (toBeDeleted && zipEnt.getName().endsWith(MergeConstants.SO_SUFFIX) && zipEntryAtomicReference.get() != null && zipEntryAtomicReference.get().getName().endsWith(".patch")) {
                File oringalOut = new File(target.getParentFile(), zipEnt.getName());
                File patchOut = new File(target.getParentFile(), zipEnt.getName() + ".patch");
                File newFileOut = new File(target.getParentFile(), zipEnt.getName() + ".new.so");
                if (!oringalOut.getParentFile().exists()) {
                    oringalOut.getParentFile().mkdirs();
                }
                inputStreamToFile(source.getInputStream(zipEnt), oringalOut);
                inputStreamToFile(MergeExcutorServices.sZipPatch.getInputStream(zipEntryAtomicReference.get()), patchOut);
                errorCheck(oringalOut, patchOut);
                PatchUtils.applyPatch(oringalOut.getAbsolutePath(), newFileOut.getAbsolutePath(), patchOut.getAbsolutePath());
                errorCheck(newFileOut);
                if (!oringalOut.delete() || !newFileOut.renameTo(oringalOut)) {
                    throw new IOException("file deleted failed or file rename failed:" + oringalOut.getAbsolutePath() + " " + newFileOut.getAbsolutePath());
                }

                entryList.remove(zipEntryAtomicReference.getAndSet(null));
                out.putNextEntry(new ZipEntry(zipEnt.getName()));
                write(new BufferedInputStream(new FileInputStream(oringalOut)), out, buffer);
            }
        }

        if (!isSourceHasDex && isDiff) {
            throw new MergeException("Original bundle has no dex");
        }

        //最后写入patch中的内容
//        File[] patchFiles = patch.listFiles();
//        for (File patchFile : patchFiles) {
//            /**
//             * 差量更新需要做dex merge, 过滤出classes.dex
//             */
//            if (isDiff && patchFile.getName().equals("classes.dex")) {
//                patchDex = patchFile;
//                isPatchHasDex = true;
//                MergeExcutorServices.needMergeCount.incrementAndGet();
//                continue;
//            }
//            zip(out, patchFile, patchFile.getName(), bo);
//
//        }
        for (ZipEntry entry : entryList) {
            if (isDiff && (entry.getName().endsWith(MergeConstants.CLASS + MergeConstants.DEX_SUFFIX))) {
                patchDex = entry;
                isPatchHasDex = true;
                continue;
            }

            ZipEntry newEntry = null;
            if (MergeExcutorServices.os == MergeExcutorServices.OS.mac) {
                newEntry = new ZipEntry(entry.getName().substring(entry.getName().indexOf("/") + 1));
                if (newEntry.getName().contains("raw/") || newEntry.getName().contains("assets/")) {
                    newEntry.setMethod(ZipEntry.STORED);
                    newEntry.setCrc(entry.getCrc());
                    newEntry.setSize(entry.getSize());
                }
            } else {
                newEntry = new ZipEntry(entry.getName().substring(entry.getName().indexOf("\\") + 1));
                if (newEntry.getName().contains("raw\\") || newEntry.getName().contains("assets\\")) {
                    newEntry.setMethod(ZipEntry.STORED);
                    newEntry.setCrc(entry.getCrc());
                    newEntry.setSize(entry.getSize());
                }
            }
            out.putNextEntry(newEntry);
            in = MergeExcutorServices.sZipPatch.getInputStream(entry);
            write(in, out, buffer);
            bo.flush();
        }
        /**
         * 差量更新需要做dex merge
         */
        if (isDiff) {
            // Merge patch dex with origin dex
            if (isPatchHasDex && isSourceHasDex) {
                //发出merge申请
//                File outDexDir = new File(patch, "out");
                ByteArrayOutputStream outDexStream = new ByteArrayOutputStream();
                dexMerge(patchBundleName, source, patchDex, outDexStream, prepareCallBack);
//                if (outDexStream.exists()) {
//                    /**
//                     * caculate the merged dex md5 and report
//                     */
//                    String md5 = Md5Utils.getFileMD5String(outDex);
//                    MonitorReport.getInstance().trace(source.getName(), md5, "" + outDex.length());
//                }
//                zip(out, outDex, outDex.getName(), bo);
                ByteArrayInputStream swapStream = new ByteArrayInputStream(outDexStream.toByteArray());
                ZipEntry entry = new ZipEntry(MergeConstants.CLASS + MergeConstants.DEX_SUFFIX);
                out.putNextEntry(entry);
                write(swapStream, out, buffer);
                bo.flush();
            } else if (isSourceHasDex) {
                // Patch has no classes.dex, just use the original dex
//                outDex = new File(patch, "classes.dex");
//                inputStreamToFile(source.getInputStream(originalDex), outDex);
//                zip(out, outDex, outDex.getName(), bo);
                ZipEntry entry = new ZipEntry(MergeConstants.CLASS + MergeConstants.DEX_SUFFIX);
                out.putNextEntry(entry);
                in = source.getInputStream(source.getEntry(MergeConstants.CLASS + MergeConstants.DEX_SUFFIX));
                write(in, out, buffer);
                bo.flush();
            }
        } else {
        }

        closeQuitely(out);
        closeQuitely(bo);
    }

    private static void errorCheck(File... files) throws IOException {
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file == null) {
                throw new IOException("file is null");
            } else if (!file.exists()) {
                throw new IOException("file no exit:" + file.getAbsolutePath());
            }
        }
    }

    private static void dexMerge(String bundleName, ZipFile source, ZipEntry patchDex, OutputStream newDexStream, MergeExcutorServices.PrepareCallBack prepareCallBack) throws IOException {


        prepareCallBack.prepareMerge(bundleName, source, patchDex, newDexStream);


    }


    private static void inputStreamToFile(InputStream ins, File file) throws IOException {
        if (file.exists()) {
            file.delete();
        }
        OutputStream os = new FileOutputStream(file);
        int bytesRead = 0;
        byte[] buffer = new byte[8192];
        while ((bytesRead = ins.read(buffer, 0, 8192)) != -1) {
            os.write(buffer, 0, bytesRead);
        }
        os.close();
        ins.close();
    }

//    private static void zip(ZipOutputStream out, File f, String base,
//                            BufferedOutputStream bo) throws IOException {
//        if (f.isDirectory()) {
//            File[] fl = f.listFiles();
//            if (fl.length == 0) {
//                out.putNextEntry(new ZipEntry(base + File.separator));
//            }
//            for (int i = 0; i < fl.length; i++) {
//                zip(out, fl[i], base + File.separator + fl[i].getName(), bo);
//            }
//        } else {
//            CRC32 crc = new CRC32();
//            ZipEntry newEntry = new ZipEntry(base);
//            int bytesRead;
//            byte[] buffer = new byte[BUFFEREDSIZE];
//            if(base.contains("raw/") || base.contains("assets/")){
//                BufferedInputStream bis = new BufferedInputStream(
//                        new FileInputStream(f));
//                crc.reset();
//                while ((bytesRead = bis.read(buffer)) != -1) {
//                    crc.update(buffer, 0, bytesRead);
//                }
//                closeQuitely(bis);
//                newEntry.setMethod(ZipEntry.STORED);
//                newEntry.setCrc(crc.getValue());
//                newEntry.setSize(f.length());
//            }
//            out.putNextEntry(newEntry);
//            FileInputStream in = new FileInputStream(f);
//            BufferedInputStream bi = new BufferedInputStream(in);
//            int b;
//            while ((b = bi.read()) != -1) {
//                bo.write(b);
//            }
//            bo.flush();
//            closeQuitely(bi);
//            closeQuitely(in);
//        }
//    }

    private static void write(InputStream in, OutputStream out, byte[] buffer) throws IOException {
        int length = in.read(buffer);
        while (length != -1) {
            out.write(buffer, 0, length);
            length = in.read(buffer);
        }
        closeQuitely(in);
    }


    private static void closeQuitely(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Throwable e) {
            }
        }
    }

    private static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
        }
        return dir.delete();
    }

    public static boolean isArt() {
//        return true;

        return Build.VERSION.SDK_INT > 20;

    }

}
