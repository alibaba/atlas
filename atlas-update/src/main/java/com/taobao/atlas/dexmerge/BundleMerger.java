package com.taobao.atlas.dexmerge;

import android.os.Build;
import android.util.Log;
import com.alibaba.patch.PatchUtils;
import com.taobao.atlas.dex.ClassDef;
import com.taobao.atlas.dex.Dex;
import com.taobao.atlas.dex.ProtoId;
import com.taobao.atlas.dex.util.FileUtils;
import android.taobao.atlas.runtime.RuntimeVariables;
import com.taobao.atlas.dexmerge.dx.merge.CollisionPolicy;
import com.taobao.atlas.dexmerge.dx.merge.DexMerger;
import com.taobao.atlas.update.util.PatchMerger;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Created by lilong on 16/7/5.
 */
public class BundleMerger {
    private static final int BUFFEREDSIZE = 8192;

    private static final byte[] BUFFER = new byte[BUFFEREDSIZE];

    private static final String MAIN_DEX = "com.taobao.maindex";

    private static final String CLASS = "classes";

    private static final String DEX_SUFFIX = ".dex";

    private static final String SO_SUFFIX = ".so";

    private static final String SO_PATCH_SUFFIX = ".patch";



    public static void mergePrepare(File oldBundle, List<ZipEntry> entryList, String patchName, File targetBundle, boolean isDiff, MergeExcutorServices.PrepareCallBack prepareCallBack) throws IOException, MergeException {
        if (oldBundle.exists() && entryList != null) {
            ZipFile sourceZip = new ZipFile(oldBundle);
            try {
                File tempFile = File.createTempFile(patchName, null, targetBundle.getParentFile());
                tempFile.deleteOnExit();
                if (patchName.equals(MAIN_DEX)) {
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
        ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(tempFile)));
        InputStream in;
        //先写入source中未变的文件
        int i = 1;
        File mainDexFile = new File(tempFile.getParentFile(), "libcom_taobao_maindex.zip");
        inputStreamToFile(MergeExcutorServices.sZipPatch.getInputStream(entryList.get(0)), mainDexFile);
        ZipFile zipFile = new ZipFile(mainDexFile);
        if (Build.VERSION.SDK_INT < 21) {
            //1.get all need patch classes
            List<String> patchClassNames = getPatchClasses(zipFile);

            //2.process source apk dexs,such as classes.dex,classes2.dex,classes3.dex ...

            i = processSourceDexsAndWrite(sourceZip,out,patchClassNames);

        }

        Enumeration entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry zipEnt = (ZipEntry) entries.nextElement();
            String name = zipEnt.getName();
            if (name.endsWith(DEX_SUFFIX)) {
                writePatchDex(zipFile,zipEnt,i,out);
                i++;
                continue;
            }


            if (name.endsWith(SO_PATCH_SUFFIX)){
                File patchNativeSoFile = new File(tempFile.getParentFile(),zipEnt.getName());
                if (!patchNativeSoFile.getParentFile().exists()){
                    patchNativeSoFile.getParentFile().mkdirs();
                }
                inputStreamToFile(zipFile.getInputStream(zipEnt), patchNativeSoFile);
                File oringalSoFile = findBaseSo(patchNativeSoFile.getName().replace(SO_PATCH_SUFFIX,""),name,sourceZip,patchNativeSoFile.getAbsolutePath());
                File newSo = new File(patchNativeSoFile.getParentFile(),patchNativeSoFile.getName().replace(SO_PATCH_SUFFIX,""));
                boolean result = true;
                if (MergeExcutorServices.patchMerger!= null){
                    result = MergeExcutorServices.patchMerger.merge(oringalSoFile,patchNativeSoFile,newSo);
                }
                if (!result){
                    throw new IOException("native so merge Failed!");
                }
                ZipEntry newEntry = new ZipEntry(name);
                out.putNextEntry(newEntry);
                 in = new FileInputStream(newSo);
                 write(in, out, BUFFER);
                 out.flush();
                 out.closeEntry();
                 newSo.delete();
                 continue;

            }

            ZipEntry newEntry = new ZipEntry(name);
            if (name.contains("raw/") || name.contains("assets/")) {
                newEntry.setMethod(ZipEntry.STORED);
                newEntry.setCrc(zipEnt.getCrc());
                newEntry.setSize(zipEnt.getSize());
            }
            out.putNextEntry(newEntry);
            in = zipFile.getInputStream(zipEnt);
            write(in, out, BUFFER);
            out.flush();
            out.closeEntry();

        }
        mainDexFile.delete();
        zipFile.close();
        closeQuitely(out);
    }

    private static void writePatchDex(ZipFile zipFile, ZipEntry zipEnt, int i, ZipOutputStream out) throws IOException {
        ZipEntry zipEntry = new ZipEntry(String.format("%s%s%s", CLASS, i == 1?"":i, DEX_SUFFIX));
        out.putNextEntry(zipEntry);
        write(zipFile.getInputStream(zipEnt), out, BUFFER);
        out.flush();
        out.closeEntry();
    }

    private static int processSourceDexsAndWrite(ZipFile sourceZip,ZipOutputStream out,List<String> patchClassNames) throws IOException {
        int i = 1;
        java.util.Enumeration e = sourceZip.entries();
        while (e.hasMoreElements()) {
            ZipEntry zipEnt = (ZipEntry) e.nextElement();
            String name = zipEnt.getName();
            if (name.endsWith(DEX_SUFFIX)) {
                i++;
                InputStream inputStream = sourceZip.getInputStream(zipEnt);
                Dex dex = processDex(inputStream, patchClassNames);
                ZipEntry zipEntry = new ZipEntry(name);
                out.putNextEntry(zipEntry);
                write(new ByteArrayInputStream(dex.getBytes()), out, BUFFER);
                out.closeEntry();
                out.flush();
            }
        }
        return i;
    }

    private static List<String> getPatchClasses(ZipFile zipFile) throws IOException {
        Dex patchDex = new Dex(zipFile.getInputStream(zipFile.getEntry(CLASS + DEX_SUFFIX)));
        Iterator<ClassDef> iterators = patchDex.classDefs().iterator();
        List<String> patchClassNames = new ArrayList<String>();
        while (iterators.hasNext()) {
            ClassDef classDef = iterators.next();
            int typeIndex = classDef.getTypeIndex();
            Log.e("BundleMerger", "merge class:" + patchDex.typeNames().get(typeIndex));
            patchClassNames.add(patchDex.typeNames().get(typeIndex));
        }
        return patchClassNames;
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
                patchEntry.compareAndSet(null,entry);
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
        AtomicReference<ZipEntry>zipEntryAtomicReference = new AtomicReference<>();
        while (e.hasMoreElements()) {
            ZipEntry zipEnt = (ZipEntry) e.nextElement();
            String name = zipEnt.getName();
            /**
             * 差量更新需要做dex merge, 过滤出classes.dex
             */
            if (isDiff && name.equals(CLASS + DEX_SUFFIX)) {
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
            if (toBeDeleted && zipEnt.getName().endsWith(SO_SUFFIX) && zipEntryAtomicReference.get() != null && zipEntryAtomicReference.get().getName().endsWith(".patch")) {
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
            if (isDiff && (entry.getName().endsWith(CLASS + DEX_SUFFIX))) {
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
                ZipEntry entry = new ZipEntry(CLASS + DEX_SUFFIX);
                out.putNextEntry(entry);
                write(swapStream, out, buffer);
                bo.flush();
            } else if (isSourceHasDex) {
                // Patch has no classes.dex, just use the original dex
//                outDex = new File(patch, "classes.dex");
//                inputStreamToFile(source.getInputStream(originalDex), outDex);
//                zip(out, outDex, outDex.getName(), bo);
                ZipEntry entry = new ZipEntry(CLASS + DEX_SUFFIX);
                out.putNextEntry(entry);
                in = source.getInputStream(source.getEntry(CLASS + DEX_SUFFIX));
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


    private static void inputStreamToFile(InputStream ins, File file) {
        OutputStream os = null;
        try {
            if (file.exists()) {
                file.delete();
            }
            os = new FileOutputStream(file);
            int bytesRead = 0;
            byte[] buffer = new byte[8192];
            while ((bytesRead = ins.read(buffer, 0, 8192)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        }catch (Throwable e){
            e.printStackTrace();

        }finally {
            closeQuitely(os);
            closeQuitely(ins);
        }


    }


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

    private static File findBaseSo(String soName, String entryName, ZipFile rawZip, String targetPath) throws IOException {
        File libDir = new File(RuntimeVariables.androidApplication.getFilesDir().getParentFile(), "lib");
        if (new File(libDir, soName).exists() && new File(libDir, soName).canRead()) {
            return new File(libDir, soName);
        } else {
            File oringalSo = new File(new File(targetPath).getParentFile(), new File(targetPath).getName().replace(SO_PATCH_SUFFIX, ".old"));
            inputStreamToFile(rawZip.getInputStream(rawZip.getEntry(entryName.replace(SO_PATCH_SUFFIX, ""))), oringalSo);
            return oringalSo;
        }

    }

}
