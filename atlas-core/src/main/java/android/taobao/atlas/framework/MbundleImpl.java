package android.taobao.atlas.framework;

import android.taobao.atlas.bundleInfo.AtlasBundleInfoManager;
import android.taobao.atlas.bundleInfo.BundleListing;
import android.taobao.atlas.framework.bundlestorage.BundleArchive;
import android.taobao.atlas.util.log.impl.AtlasMonitor;
import android.util.Log;
import org.osgi.framework.BundleException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            BundleListing.BundleInfo bundleInfo = AtlasBundleInfoManager.instance().getBundleInfo(bundle);
            if (bundleInfo!= null && !bundleInfo.isMBundle()){
                Map<String,Object> detailMap = new HashMap<>();
                detailMap.put("source",location);
                detailMap.put("dependency",bundle);
                detailMap.put("method","checkValidate()");
                AtlasMonitor.getInstance().report(AtlasMonitor.BUNDLE_DEPENDENCY_ERROR, detailMap, new IllegalArgumentException());
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
