package android.taobao.atlas.util;

import java.io.Closeable;
import java.util.zip.ZipFile;

/**
 * Created by guanjie on 2017/5/5.
 */

public class IOUtil {
    public static void quietClose(Closeable closeable){
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Throwable e) {
            }
        }
    }

    public static void quietClose(ZipFile zip){
        try {
            if (zip != null) {
                zip.close();
            }
        }catch(Throwable e){
        }
    }
}
