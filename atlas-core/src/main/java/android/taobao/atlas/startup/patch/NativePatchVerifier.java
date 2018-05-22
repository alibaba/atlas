package android.taobao.atlas.startup.patch;

import android.taobao.atlas.util.FileUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * NativePatchVerifier
 *
 * @author zhayu.ll
 * @date 18/5/10
 */
public class NativePatchVerifier implements PatchVerifier{

    private Map<String,String>map = new HashMap<>();


    public NativePatchVerifier(File patchInfo) throws IOException {
       this(new FileInputStream(patchInfo));
    }
    public NativePatchVerifier(InputStream patchInfo) throws IOException {
            read(patchInfo);
    }
    @Override
    public boolean verify(File mergeFile) {
        if (map.containsKey(mergeFile.getName())){
            try {
                return map.get(mergeFile.getName()).equals(FileUtils.getMd5ByFile(mergeFile));
            } catch (IOException e) {
                return false;
            }
        }
        return true;
    }


    public void read(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.contains(":")) {
                map.put(line.split(":")[0], line.split(":")[1]);
            }
        }

    }
}
