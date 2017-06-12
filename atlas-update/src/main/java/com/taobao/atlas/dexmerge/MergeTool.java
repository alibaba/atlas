package com.taobao.atlas.dexmerge;

import android.util.Log;
import com.taobao.atlas.dex.ClassDef;
import com.taobao.atlas.dex.Dex;
import com.taobao.atlas.dexmerge.dx.merge.CollisionPolicy;
import com.taobao.atlas.dexmerge.dx.merge.DexMerger;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Created by lilong on 16/7/5.
 */
public class MergeTool {
    private static final int BUFFEREDSIZE = 1024;

    public static void mergePrepare(File oldBundle, List<ZipEntry> entryList,String patchName, File targetBundle, boolean isDiff, MergeExcutorServices.PrepareCallBack prepareCallBack) throws IOException, MergeException {
        if (oldBundle.exists() && entryList!= null) {
            ZipFile sourceZip = new ZipFile(oldBundle);
            try {
                File tempFile = File.createTempFile(patchName, null, targetBundle.getParentFile());
                tempFile.deleteOnExit();
                if (patchName.equals("com.taobao.maindex")){
                  createNewMainApkInternal(sourceZip,entryList,tempFile,isDiff,prepareCallBack);
                }else {
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
        File mainDexFile = new File(tempFile.getParentFile(),"libcom_taobao_maindex.zip");
        inputStreamToFile(MergeExcutorServices.sZipPatch.getInputStream(entryList.get(0)),mainDexFile);
        ZipFile zipFile = new ZipFile(mainDexFile);
        Dex patchDex = new Dex(zipFile.getInputStream(zipFile.getEntry("classes.dex")));
        Iterator<ClassDef> iterators = patchDex.classDefs().iterator();
        List<String>patchClassNames = new ArrayList<String>();
        while (iterators.hasNext()){
            ClassDef classDef = iterators.next();
            int typeIndex = classDef.getTypeIndex();
            Log.e("MergeTool","merge class:"+patchDex.typeNames().get(typeIndex));
            patchClassNames.add(patchDex.typeNames().get(typeIndex));
        }
        while (e.hasMoreElements()){
            ZipEntry zipEnt = (ZipEntry) e.nextElement();
            String name = zipEnt.getName();
            if (isDiff && name.endsWith(".dex")) {
                i ++;
                InputStream inputStream = sourceZip.getInputStream(zipEnt);
                Dex dex = processDex(inputStream,patchClassNames);
                ZipEntry zipEntry = new ZipEntry(name);
                out.putNextEntry(zipEntry);
                write(new ByteArrayInputStream(dex.getBytes()), out, buffer);
                bo.flush();
            }
        }
        Enumeration entries = zipFile.entries();
       while (entries.hasMoreElements()) {
           ZipEntry zipEnt = (ZipEntry) entries.nextElement();
           String name = zipEnt.getName();
           if (name.endsWith(".dex")){
               ZipEntry zipEntry = new ZipEntry(String.format("%s%s%s","classes",i,".dex"));
               out.putNextEntry(zipEntry);
               write(zipFile.getInputStream(zipEnt),out,buffer);
               bo.flush();
               i++;
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
           write(in, out, buffer);
           bo.flush();

       }
        mainDexFile.delete();
        zipFile.close();
        closeQuitely(out);
        closeQuitely(bo);
    }



    private static Dex processDex(InputStream inputStream, List<String> patchClassNames) throws IOException {
        Dex oringnalDex = new Dex(inputStream);
        DexMerger dexMerger = new DexMerger(new Dex[]{oringnalDex,new Dex(0)}, CollisionPolicy.FAIL);
        dexMerger.setRemoveTypeClasses(patchClassNames);
        dexMerger.setCompactWasteThreshold(1);
        Dex dex = dexMerger.merge();
        return dex;
    }

    private static boolean isBundleFileUpdated(ZipEntry sourceEntry,List<ZipEntry> entryList){
        for(ZipEntry entry : entryList){
            if(entry.getName().contains(sourceEntry.getName())){
                return true;
            }
        }
        return false;
    }

    public static void createNewBundleInternal(String patchBundleName,ZipFile source, List<ZipEntry> entryList, File target, boolean isDiff, MergeExcutorServices.PrepareCallBack prepareCallBack) throws IOException, MergeException {

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

        while (e.hasMoreElements()) {
            ZipEntry zipEnt = (ZipEntry) e.nextElement();
            String name = zipEnt.getName();
            /**
             * 差量更新需要做dex merge, 过滤出classes.dex
             */
            if (isDiff && name.equals("classes.dex")) {
//                originalDex = zipEnt;
                isSourceHasDex = true;
                continue;
            }

            boolean toBeDeleted = isBundleFileUpdated(zipEnt,entryList);

            if (!toBeDeleted) {

                ZipEntry newEntry = new ZipEntry(name);
                if (MergeExcutorServices.os == MergeExcutorServices.OS.mac) {
                    if (name.contains("raw/") || name.contains("assets/")) {
                        newEntry.setMethod(ZipEntry.STORED);
                        newEntry.setCrc(zipEnt.getCrc());
                        newEntry.setSize(zipEnt.getSize());
                    }
                }else {
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
        for(ZipEntry entry : entryList){
            if(isDiff && (entry.getName().endsWith("classes.dex"))){
                patchDex = entry;
                isPatchHasDex =true;
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
            }else {
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
                ByteArrayOutputStream   outDexStream=new  ByteArrayOutputStream();
                dexMerge(patchBundleName,source, patchDex, outDexStream, prepareCallBack);
//                if (outDexStream.exists()) {
//                    /**
//                     * caculate the merged dex md5 and report
//                     */
//                    String md5 = Md5Utils.getFileMD5String(outDex);
//                    MonitorReport.getInstance().trace(source.getName(), md5, "" + outDex.length());
//                }
//                zip(out, outDex, outDex.getName(), bo);
                ByteArrayInputStream swapStream = new ByteArrayInputStream(outDexStream.toByteArray());
                ZipEntry entry = new ZipEntry("classes.dex");
                out.putNextEntry(entry);
                write(swapStream,out,buffer);
                bo.flush();
            } else if (isSourceHasDex) {
                // Patch has no classes.dex, just use the original dex
//                outDex = new File(patch, "classes.dex");
//                inputStreamToFile(source.getInputStream(originalDex), outDex);
//                zip(out, outDex, outDex.getName(), bo);
                ZipEntry entry = new ZipEntry("classes.dex");
                out.putNextEntry(entry);
                in = source.getInputStream(source.getEntry("classes.dex"));
                write(in,out,buffer);
                bo.flush();
            }
        }else {
        }

        closeQuitely(out);
        closeQuitely(bo);
    }

    private static void dexMerge(String bundleName,ZipFile source, ZipEntry patchDex, OutputStream newDexStream, MergeExcutorServices.PrepareCallBack prepareCallBack) throws IOException {


            prepareCallBack.prepareMerge(bundleName,source, patchDex, newDexStream);


    }


    private static void inputStreamToFile(InputStream ins, File file) throws IOException {
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
            for (int i=0; i<children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
        }
        return dir.delete();
    }

}
