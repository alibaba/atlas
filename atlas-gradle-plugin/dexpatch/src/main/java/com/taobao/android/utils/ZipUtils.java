/*
 * Copyright 2014 Taobao.com All right reserved. This software is the
 * confidential and proprietary information of Tlibaba.com ("Confidential
 * Information"). You shall not disclose such Confidential Information and shall
 * use it only in accordance with the terms of the license agreement you entered
 * into with Taobao.com.
 */
package com.taobao.android.utils;
/*
 *
 *
 *                                  Apache License
 *                            Version 2.0, January 2004
 *                         http://www.apache.org/licenses/
 *
 *    TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION
 *
 *    1. Definitions.
 *
 *       "License" shall mean the terms and conditions for use, reproduction,
 *       and distribution as defined by Sections 1 through 9 of this document.
 *
 *       "Licensor" shall mean the copyright owner or entity authorized by
 *       the copyright owner that is granting the License.
 *
 *       "Legal Entity" shall mean the union of the acting entity and all
 *       other entities that control, are controlled by, or are under common
 *       control with that entity. For the purposes of this definition,
 *       "control" means (i) the power, direct or indirect, to cause the
 *       direction or management of such entity, whether by contract or
 *       otherwise, or (ii) ownership of fifty percent (50%) or more of the
 *       outstanding shares, or (iii) beneficial ownership of such entity.
 *
 *       "You" (or "Your") shall mean an individual or Legal Entity
 *       exercising permissions granted by this License.
 *
 *       "Source" form shall mean the preferred form for making modifications,
 *       including but not limited to software source code, documentation
 *       source, and configuration files.
 *
 *       "Object" form shall mean any form resulting from mechanical
 *       transformation or translation of a Source form, including but
 *       not limited to compiled object code, generated documentation,
 *       and conversions to other media types.
 *
 *       "Work" shall mean the work of authorship, whether in Source or
 *       Object form, made available under the License, as indicated by a
 *       copyright notice that is included in or attached to the work
 *       (an example is provided in the Appendix below).
 *
 *       "Derivative Works" shall mean any work, whether in Source or Object
 *       form, that is based on (or derived from) the Work and for which the
 *       editorial revisions, annotations, elaborations, or other modifications
 *       represent, as a whole, an original work of authorship. For the purposes
 *       of this License, Derivative Works shall not include works that remain
 *       separable from, or merely link (or bind by name) to the interfaces of,
 *       the Work and Derivative Works thereof.
 *
 *       "Contribution" shall mean any work of authorship, including
 *       the original version of the Work and any modifications or additions
 *       to that Work or Derivative Works thereof, that is intentionally
 *       submitted to Licensor for inclusion in the Work by the copyright owner
 *       or by an individual or Legal Entity authorized to submit on behalf of
 *       the copyright owner. For the purposes of this definition, "submitted"
 *       means any form of electronic, verbal, or written communication sent
 *       to the Licensor or its representatives, including but not limited to
 *       communication on electronic mailing lists, source code control systems,
 *       and issue tracking systems that are managed by, or on behalf of, the
 *       Licensor for the purpose of discussing and improving the Work, but
 *       excluding communication that is conspicuously marked or otherwise
 *       designated in writing by the copyright owner as "Not a Contribution."
 *
 *       "Contributor" shall mean Licensor and any individual or Legal Entity
 *       on behalf of whom a Contribution has been received by Licensor and
 *       subsequently incorporated within the Work.
 *
 *    2. Grant of Copyright License. Subject to the terms and conditions of
 *       this License, each Contributor hereby grants to You a perpetual,
 *       worldwide, non-exclusive, no-charge, royalty-free, irrevocable
 *       copyright license to reproduce, prepare Derivative Works of,
 *       publicly display, publicly perform, sublicense, and distribute the
 *       Work and such Derivative Works in Source or Object form.
 *
 *    3. Grant of Patent License. Subject to the terms and conditions of
 *       this License, each Contributor hereby grants to You a perpetual,
 *       worldwide, non-exclusive, no-charge, royalty-free, irrevocable
 *       (except as stated in this section) patent license to make, have made,
 *       use, offer to sell, sell, import, and otherwise transfer the Work,
 *       where such license applies only to those patent claims licensable
 *       by such Contributor that are necessarily infringed by their
 *       Contribution(s) alone or by combination of their Contribution(s)
 *       with the Work to which such Contribution(s) was submitted. If You
 *       institute patent litigation against any entity (including a
 *       cross-claim or counterclaim in a lawsuit) alleging that the Work
 *       or a Contribution incorporated within the Work constitutes direct
 *       or contributory patent infringement, then any patent licenses
 *       granted to You under this License for that Work shall terminate
 *       as of the date such litigation is filed.
 *
 *    4. Redistribution. You may reproduce and distribute copies of the
 *       Work or Derivative Works thereof in any medium, with or without
 *       modifications, and in Source or Object form, provided that You
 *       meet the following conditions:
 *
 *       (a) You must give any other recipients of the Work or
 *           Derivative Works a copy of this License; and
 *
 *       (b) You must cause any modified files to carry prominent notices
 *           stating that You changed the files; and
 *
 *       (c) You must retain, in the Source form of any Derivative Works
 *           that You distribute, all copyright, patent, trademark, and
 *           attribution notices from the Source form of the Work,
 *           excluding those notices that do not pertain to any part of
 *           the Derivative Works; and
 *
 *       (d) If the Work includes a "NOTICE" text file as part of its
 *           distribution, then any Derivative Works that You distribute must
 *           include a readable copy of the attribution notices contained
 *           within such NOTICE file, excluding those notices that do not
 *           pertain to any part of the Derivative Works, in at least one
 *           of the following places: within a NOTICE text file distributed
 *           as part of the Derivative Works; within the Source form or
 *           documentation, if provided along with the Derivative Works; or,
 *           within a display generated by the Derivative Works, if and
 *           wherever such third-party notices normally appear. The contents
 *           of the NOTICE file are for informational purposes only and
 *           do not modify the License. You may add Your own attribution
 *           notices within Derivative Works that You distribute, alongside
 *           or as an addendum to the NOTICE text from the Work, provided
 *           that such additional attribution notices cannot be construed
 *           as modifying the License.
 *
 *       You may add Your own copyright statement to Your modifications and
 *       may provide additional or different license terms and conditions
 *       for use, reproduction, or distribution of Your modifications, or
 *       for any such Derivative Works as a whole, provided Your use,
 *       reproduction, and distribution of the Work otherwise complies with
 *       the conditions stated in this License.
 *
 *    5. Submission of Contributions. Unless You explicitly state otherwise,
 *       any Contribution intentionally submitted for inclusion in the Work
 *       by You to the Licensor shall be under the terms and conditions of
 *       this License, without any additional terms or conditions.
 *       Notwithstanding the above, nothing herein shall supersede or modify
 *       the terms of any separate license agreement you may have executed
 *       with Licensor regarding such Contributions.
 *
 *    6. Trademarks. This License does not grant permission to use the trade
 *       names, trademarks, service marks, or product names of the Licensor,
 *       except as required for reasonable and customary use in describing the
 *       origin of the Work and reproducing the content of the NOTICE file.
 *
 *    7. Disclaimer of Warranty. Unless required by applicable law or
 *       agreed to in writing, Licensor provides the Work (and each
 *       Contributor provides its Contributions) on an "AS IS" BASIS,
 *       WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *       implied, including, without limitation, any warranties or conditions
 *       of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A
 *       PARTICULAR PURPOSE. You are solely responsible for determining the
 *       appropriateness of using or redistributing the Work and assume any
 *       risks associated with Your exercise of permissions under this License.
 *
 *    8. Limitation of Liability. In no event and under no legal theory,
 *       whether in tort (including negligence), contract, or otherwise,
 *       unless required by applicable law (such as deliberate and grossly
 *       negligent acts) or agreed to in writing, shall any Contributor be
 *       liable to You for damages, including any direct, indirect, special,
 *       incidental, or consequential damages of any character arising as a
 *       result of this License or out of the use or inability to use the
 *       Work (including but not limited to damages for loss of goodwill,
 *       work stoppage, computer failure or malfunction, or any and all
 *       other commercial damages or losses), even if such Contributor
 *       has been advised of the possibility of such damages.
 *
 *    9. Accepting Warranty or Additional Liability. While redistributing
 *       the Work or Derivative Works thereof, You may choose to offer,
 *       and charge a fee for, acceptance of support, warranty, indemnity,
 *       or other liability obligations and/or rights consistent with this
 *       License. However, in accepting such obligations, You may act only
 *       on Your own behalf and on Your sole responsibility, not on behalf
 *       of any other Contributor, and only if You agree to indemnify,
 *       defend, and hold each Contributor harmless for any liability
 *       incurred by, or claims asserted against, such Contributor by reason
 *       of your accepting any such warranty or additional liability.
 *
 *    END OF TERMS AND CONDITIONS
 *
 *    APPENDIX: How to apply the Apache License to your work.
 *
 *       To apply the Apache License to your work, attach the following
 *       boilerplate notice, with the fields enclosed by brackets "[]"
 *       replaced with your own identifying information. (Don't include
 *       the brackets!)  The text should be enclosed in the appropriate
 *       comment syntax for the file format. We also recommend that a
 *       file or class name and description of purpose be included on the
 *       same "printed page" as the copyright notice for easier
 *       identification within third-party archives.
 *
 *    Copyright 2016 Alibaba Group
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 *
 */

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 解压工具类
 *
 * @author shenghua.nish 2014-6-17 上午9:48:32
 */
public class ZipUtils {

    /**
     * <p>
     * unzip.
     * </p>
     *
     * @param zipFile     a {@link File} object.
     * @param destination a {@link String} object.
     * @return a {@link List} object.
     */
    public static List<String> unzip(final File zipFile, final String destination) {
        return unzip(zipFile, destination, null);
    }

    /**
     * <p>
     * unzip.
     * </p>
     *
     * @param zipFile     a {@link File} object.
     * @param destination a {@link String} object.
     * @param encoding    a {@link String} object.
     * @return a {@link List} object.
     */
    public static List<String> unzip(final File zipFile, final String destination, String encoding) {
        List<String> fileNames = new ArrayList<String>();
        String dest = destination;
        if (!destination.endsWith("/")) {
            dest = destination + "/";
        }
        ZipFile file;
        try {
            file = null;
            if (null == encoding) file = new ZipFile(zipFile);
            else file = new ZipFile(zipFile, encoding);
            Enumeration<ZipArchiveEntry> en = file.getEntries();
            ZipArchiveEntry ze = null;
            while (en.hasMoreElements()) {
                ze = en.nextElement();
                File f = new File(dest, ze.getName());
                if (ze.isDirectory()) {
                    f.mkdirs();
                    continue;
                } else {
                    f.getParentFile().mkdirs();
                    InputStream is = file.getInputStream(ze);
                    OutputStream os = new FileOutputStream(f);
                    IOUtils.copy(is, os);
                    is.close();
                    os.close();
                    fileNames.add(f.getAbsolutePath());
                }
            }
            file.close();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        return fileNames;
    }

    /**
     * <p>
     * isZipFile.
     * </p>
     *
     * @param zipFile a {@link File} object.
     * @return a boolean.
     */
    public static boolean isZipFile(File zipFile) {
        try {
            ZipFile zf = new ZipFile(zipFile);
            boolean isZip = zf.getEntries().hasMoreElements();
            zf.close();
            return isZip;
        } catch (IOException e) {
            return false;
        }
    }


    private static String getFileName(File folder, File file) {
        String name = StringUtils.replace(file.getAbsolutePath(), folder.getAbsolutePath() + "/", "");
        return name;
    }

    /**
     * 解压zip文件中的某个文件到指定地方
     *
     * @param zipFile
     * @param path
     * @param destFolder
     * @throws IOException
     */
    public static File extractZipFileToFolder(File zipFile, String path, File destFolder) {
        ZipFile zip;
        File destFile = null;
        try {
            zip = new ZipFile(zipFile);
            ZipArchiveEntry zipArchiveEntry = zip.getEntry(path);
            if (null != zipArchiveEntry) {
                String name = zipArchiveEntry.getName();
                name = FilenameUtils.getName(name);
                destFile = new File(destFolder, name);
                destFolder.mkdirs();
                destFile.createNewFile();
                InputStream is = zip.getInputStream(zipArchiveEntry);
                FileOutputStream fos = new FileOutputStream(destFile);
                int length = 0;
                byte[] b = new byte[1024];
                while ((length = is.read(b, 0, 1024)) != -1) {
                    fos.write(b, 0, length);
                }
                is.close();
                fos.close();
            }
            if (null != zip) ZipFile.closeQuietly(zip);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return destFile;
    }

    /**
     * 增加文件到zip文件
     *
     * @param zipFile
     * @param file
     * @param destPath  要放的路径
     * @param overwrite 是否覆盖
     * @throws IOException
     */
    public static void addFileToZipFile(File zipFile, File outZipFile, File file, String destPath, boolean overwrite)
            throws IOException {
        byte[] buf = new byte[1024];
        ZipInputStream zin = new ZipInputStream(new FileInputStream(zipFile));
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(outZipFile));
        ZipEntry entry = zin.getNextEntry();
        boolean addFile = true;
        while (entry != null) {
            boolean addEntry = true;
            String name = entry.getName();
            if (StringUtils.equalsIgnoreCase(name, destPath)) {
                if (overwrite) {
                    addEntry = false;
                } else {
                    addFile = false;
                }
            }
            if (addEntry) {
                ZipEntry zipEntry = null;
                if (ZipEntry.STORED == entry.getMethod()) {
                    zipEntry = new ZipEntry(entry);
                } else {
                    zipEntry = new ZipEntry(name);
                }
                out.putNextEntry(zipEntry);
                int len;
                while ((len = zin.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
            entry = zin.getNextEntry();
        }

        if (addFile) {
            InputStream in = new FileInputStream(file);
            // Add ZIP entry to output stream.
            ZipEntry zipEntry = new ZipEntry(destPath);
            out.putNextEntry(zipEntry);
            // Transfer bytes from the file to the ZIP file
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            // Complete the entry
            out.closeEntry();
            in.close();
        }
        // Close the streams
        zin.close();
        out.close();
    }


    /**
     * 指定文件压缩成一个zip包，主要给solib发布使用
     *
     * @param output
     * @param srcDir
     * @throws Exception
     */
    public static void addFileAndDirectoryToZip(File output, File srcDir) throws Exception {
        if (output.isDirectory()) {
            throw new IOException("This is a directory!");
        }
        if (!output.getParentFile().exists()) {
            output.getParentFile().mkdirs();
        }

        if (!output.exists()) {
            output.createNewFile();
        }
        List fileList = getSubFiles(srcDir);
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(output));
        ZipEntry ze = null;
        byte[] buf = new byte[1024];
        int readLen = 0;
        for (int i = 0; i < fileList.size(); i++) {
            File f = (File) fileList.get(i);
            ze = new ZipEntry(getAbsFileName(srcDir.getPath(), f));
            ze.setSize(f.length());
            ze.setTime(f.lastModified());
            zos.putNextEntry(ze);
            InputStream is = new BufferedInputStream(new FileInputStream(f));
            while ((readLen = is.read(buf, 0, 1024)) != -1) {
                zos.write(buf, 0, readLen);
            }
            is.close();
        }
        zos.close();
    }

    public static List<String> listZipEntries(File zipFile) {
        List<String> list = new ArrayList<String>();
        ZipFile zip;
        try {
            zip = new ZipFile(zipFile);
            Enumeration<ZipArchiveEntry> en = zip.getEntries();
            ZipArchiveEntry ze = null;
            while (en.hasMoreElements()) {
                ze = en.nextElement();
                String name = ze.getName();
                list.add(name);
            }
            if (null != zip) ZipFile.closeQuietly(zip);
        } catch (IOException e) {
        }
        return list;
    }

    private static String getAbsFileName(String baseDir, File realFileName) {
        File real = realFileName;
        File base = new File(baseDir);
        String ret = real.getName();
        while (true) {
            real = real.getParentFile();
            if (real == null) {
                break;
            }
            if (real.equals(base)) {
                break;
            } else {
                ret = real.getName() + "/" + ret;
            }
        }
        return ret;
    }

    private static List<File> getSubFiles(File baseDir) {
        List<File> ret = new ArrayList<File>();
        File[] tmp = baseDir.listFiles();
        for (int i = 0; i < tmp.length; i++) {
            if (tmp[i].isFile()) ret.add(tmp[i]);
            if (tmp[i].isDirectory()) ret.addAll(getSubFiles(tmp[i]));
        }
        return ret;
    }

    /**
     * 判断在指定的zip目录下，指定的文件夹是否存在
     *
     * @param zipFile
     * @param pathName
     * @return
     */
    public static boolean isFolderExist(File zipFile, String pathName) {

        ZipFile file = null;
        try {
            file = new ZipFile(zipFile);
            Enumeration<ZipArchiveEntry> en = file.getEntries();
            while (en.hasMoreElements()) {
                ZipArchiveEntry entry = en.nextElement();
                String name = entry.getName();
                if (name.startsWith(pathName)) {
                    return true;
                }

            }
            return false;
        } catch (IOException e) {
        } finally {
            if (null != file) {
                try {
                    file.close();
                } catch (IOException e) {

                }
            }

        }
        return false;
    }

}
