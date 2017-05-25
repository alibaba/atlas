
package org.osgi.framework;

import java.util.EventListener;


public abstract interface FrameworkListener extends EventListener {

	/**
	 * Receives notification of a general <tt>FrameworkEvent</tt> object.
	 * 
	 * @param event
	 *            The <tt>FrameworkEvent</tt> object.
	 */
	public abstract void frameworkEvent(final FrameworkEvent event);
}
