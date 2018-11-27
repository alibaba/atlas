package android.taobao.atlas.startup.patch;

import java.io.File;

/**
 * PatchVerifier
 *
 * @author zhayu.ll
 * @date 18/5/10
 */
public interface PatchVerifier {


    public boolean verify(File mergeFile);

}
