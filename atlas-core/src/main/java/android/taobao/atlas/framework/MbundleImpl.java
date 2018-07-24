package android.taobao.atlas.framework;

import android.taobao.atlas.bundleInfo.AtlasBundleInfoManager;
import android.taobao.atlas.framework.bundlestorage.BundleArchive;
import android.util.Log;
import org.osgi.framework.BundleException;

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
        Framework.bundles.put(location,this);
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
        for (String bundle:bundles) {
            if (!AtlasBundleInfoManager.instance().getBundleInfo(bundle).isMBundle()){
                Log.e("MbundleImpl",this.location+" Mbundle can not dependency bundle--> "+bundle);
            }else {
                BundleImpl dependencyBundle = (BundleImpl)Atlas.getInstance().getBundle(bundle);
                if (dependencyBundle!= null){
                    try {
                        dependencyBundle.start();
                    } catch (BundleException e) {
                        e.printStackTrace();
                    }
                }else {
                    Log.e("MbundleImpl",location + " dependency -->"+bundle +" is not installed");
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public void optDexFile() {
        archive.optDexFile();
    }
}
