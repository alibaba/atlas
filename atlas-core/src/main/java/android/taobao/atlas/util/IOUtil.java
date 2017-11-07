package android.taobao.atlas.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

    public static void copyStream(InputStream in, OutputStream out) throws IOException {

        try {
            int c;
            byte[] by = new byte[1024];
            while ((c = in.read(by)) != -1) {
                out.write(by, 0, c);
            }
            out.flush();
        } catch (IOException e) {
            throw e;
        } finally {
            quietClose(out);
            quietClose(in);
        }
    }
}
