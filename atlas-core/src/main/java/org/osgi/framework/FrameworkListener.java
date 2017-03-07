
package org.osgi.framework;

import java.util.EventListener;

/**
 * A <tt>FrameworkEvent</tt> listener.
 * 
 * <p>
 * <tt>FrameworkListener</tt> is a listener interface that may be implemented
 * by a bundle developer. A <tt>FrameworkListener</tt> object is registered
 * with the Framework using the {@link BundleContext#addFrameworkListener}method.
 * <tt>FrameworkListener</tt> objects are called with a
 * <tt>FrameworkEvent</tt> objects when the Framework starts and when
 * asynchronous errors occur.
 * 
 * @version $Revision: 1.1 $
 * @author Open Services Gateway Initiative
 * @see FrameworkEvent
 */

public abstract interface FrameworkListener extends EventListener {

	/**
	 * Receives notification of a general <tt>FrameworkEvent</tt> object.
	 * 
	 * @param event
	 *            The <tt>FrameworkEvent</tt> object.
	 */
	public abstract void frameworkEvent(final FrameworkEvent event);
}
