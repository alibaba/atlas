

package org.osgi.framework;

import java.util.EventListener;

/**
 * A <tt>BundleEvent</tt> listener.
 * 
 * <p>
 * <tt>BundleListener</tt> is a listener interface that may be implemented by
 * a bundle developer.
 * <p>
 * A <tt>BundleListener</tt> object is registered with the Framework using the
 * {@link BundleContext#addBundleListener}method. <tt>BundleListener</tt>s
 * are called with a <tt>BundleEvent</tt> object when a bundle has been
 * installed, started, stopped, updated, or uninstalled.
 * 
 * @version $Revision: 1.1 $
 * @author Open Services Gateway Initiative
 * @see BundleEvent
 */

public interface BundleListener extends EventListener {
	/**
	 * Receives notification that a bundle has had a lifecycle change.
	 * 
	 * @param event
	 *            The <tt>BundleEvent</tt>.
	 */
	public abstract void bundleChanged(final BundleEvent event);
}
