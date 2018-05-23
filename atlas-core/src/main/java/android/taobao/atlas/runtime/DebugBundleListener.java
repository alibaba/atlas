package android.taobao.atlas.runtime;

import android.util.Log;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;

public class DebugBundleListener implements BundleListener {
    private static final String TAG = "DebugBundleListener";

    /**
     * Receives notification that a bundle has had a lifecycle change.
     *
     * @param event The <tt>BundleEvent</tt>.
     */
    @Override
    public void bundleChanged(BundleEvent event) {

        if (event.getTag() != null) {
            Log.d(TAG, "bundleChanged(" + event + ")");
        }
    }
}
