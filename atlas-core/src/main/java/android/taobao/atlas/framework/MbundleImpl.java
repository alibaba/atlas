package android.taobao.atlas.framework;

import android.taobao.atlas.bundleInfo.AtlasBundleInfoManager;
import android.taobao.atlas.framework.bundlestorage.BundleArchive;
import android.util.Log;

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
        List<String>bundles = AtlasBundleInfoManager.instance().getBundleInfo(location).getTotalDependency();
        for (String location:bundles) {
            if (!AtlasBundleInfoManager.instance().getBundleInfo(location).isMBundle()){
                Log.e("MbundleImpl",this.location+" Mbundle can not dependent bundleImpl "+location);

            }
        }

        return true;
    }

    @Override
    public void optDexFile() {
        archive.optDexFile();
    }
}
