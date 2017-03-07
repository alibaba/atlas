
package org.osgi.framework;

/**
 * A synchronous <tt>BundleEvent</tt> listener.
 * 
 * <p>
 * <tt>SynchronousBundleListener</tt> is a listener interface that may be
 * implemented by a bundle developer.
 * <p>
 * A <tt>SynchronousBundleListener</tt> object is registered with the
 * Framework using the {@link BundleContext#addBundleListener}method.
 * <tt>SynchronousBundleListener</tt> objects are called with a
 * <tt>BundleEvent</tt> object when a bundle has been installed, started,
 * stopped, updated, or uninstalled.
 * <p>
 * Unlike normal <tt>BundleListener</tt> objects,
 * <tt>SynchronousBundleListener</tt>s are synchronously called during bundle
 * life cycle processing. The bundle life cycle processing will not proceed
 * until all <tt>SynchronousBundleListener</tt>s have completed.
 * <tt>SynchronousBundleListener</tt> objects will be called prior to
 * <tt>BundleListener</tt> objects.
 * <p>
 * <tt>AdminPermission</tt> is required to add or remove a
 * <tt>SynchronousBundleListener</tt> object.
 * 
 * @version $Revision: 1.1 $
 * @author Open Services Gateway Initiative
 * @since 1.1
 * @see BundleEvent
 */

public abstract interface SynchronousBundleListener extends BundleListener {
}
