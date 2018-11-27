package android.taobao.atlas.startup.patch;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;

/**
 * CombineDexVerifier
 *
 * @author zhayu.ll
 * @date 18/5/10
 */
public class CombineDexVerifier implements PatchVerifier {

    private static final String DEX_SUFFIX = ".dex";

    private static final String CLASS_SUFFIX = "classes";

    @Override
    public boolean verify(File mergeFile) {
        if (mergeFile != null && mergeFile.exists()) {
            if (!isNewBundleFileValid(mergeFile)) {
                return false;
            }else {
                return true;
            }
        }
            return false;
    }

    private static boolean isNewBundleFileValid(File bundleFile) {
        boolean result;
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(bundleFile);
            result = zipFile.getEntry(CLASS_SUFFIX + DEX_SUFFIX) != null && zipFile.getEntry(CLASS_SUFFIX + 2 + DEX_SUFFIX) != null;
        } catch (Throwable e) {
            return true;
        }finally {
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
}
}
