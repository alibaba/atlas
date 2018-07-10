package android.taobao.atlas.framework;

import android.taobao.atlas.bundleInfo.AtlasBundleInfoManager;
import android.taobao.atlas.framework.bundlestorage.BundleArchive;

import java.util.List;

/**
 * MbundleImpl
 *
 * @author zhayu.ll
 * @date 18/7/9
 */
public class MbundleImpl extends BundleImpl{

    public MbundleImpl(String location) throws Exception {
        super(location);
        this.archive = new MbundleArchive(location);
    }

    @Override
    public BundleArchive getArchive() {
        return archive ;
    }

    @Override
    public ClassLoader getClassLoader() {
        return Framework.getSystemClassLoader();
    }

    @Override
    public boolean checkValidate() {
        return true;
    }

    @Override
    public void optDexFile() {
        archive.optDexFile();
    }
}
