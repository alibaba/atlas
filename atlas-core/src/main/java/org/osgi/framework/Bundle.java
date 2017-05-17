
package org.osgi.framework;

import java.net.URL;

/**
 * An installed bundle in the Framework.
 *
 * <p>A <tt>Bundle</tt> object is the access point to define the life cycle
 * of an installed bundle. Each bundle installed in the OSGi environment
 * will have an associated <tt>Bundle</tt> object.
 *
 * <p>A bundle will have a unique identity, a <tt>long</tt>, chosen by the
 * Framework. This identity will not change during the life cycle of a bundle, even when
 * the bundle is updated. Uninstalling and then reinstalling the bundle will create
 * a new unique identity.
 *
 * <p>A bundle can be in one of six states:
 * <ul>
 * <li>{@link #UNINSTALLED}
 * <li>{@link #INSTALLED}
 * <li>{@link #RESOLVED}
 * <li>{@link #STARTING}
 * <li>{@link #STOPPING}
 * <li>{@link #ACTIVE}
 * </ul>
 * <p>Values assigned to these states have no specified ordering;
 * they represent bit values that may be ORed together to determine if
 * a bundle is in one of the valid states.
 *
 * <p>A bundle should only execute code when its state is one of
 * <tt>STARTING</tt>, <tt>ACTIVE</tt>, or <tt>STOPPING</tt>.
 * An <tt>UNINSTALLED</tt> bundle can not be set to another state; it
 * is a zombie and can only be reached because invalid references are kept somewhere.
 *
 * <p>The Framework is the only entity that is allowed to
 * create <tt>Bundle</tt> objects, and these objects are only valid
 * within the Framework that created them.
 *
 * @version $Revision: 1.1 $
 * @author Open Services Gateway Initiative
 */
public abstract interface Bundle
{
    /**
	 * This bundle is uninstalled and may not be used.
	 *
	 * <p>The <tt>UNINSTALLED</tt> state is only visible after a bundle
	 * is uninstalled; the bundle is in an unusable state
	 * and all references to the <tt>Bundle</tt> object should be released
	 * immediately.
	 * <p>The value of <tt>UNINSTALLED</tt> is 0x00000001.
	 */
    public static final int UNINSTALLED = 0x00000001;

    /**
	 * This bundle is installed but not yet resolved.
	 *
	 * <p>A bundle is in the <tt>INSTALLED</tt> state when it has been installed
	 * in the Framework but cannot run.
	 * <p>This state is visible if the bundle's code dependencies are not resolved.
	 * The Framework may attempt to resolve an <tt>INSTALLED</tt> bundle's
	 * code dependencies and move the bundle to the <tt>RESOLVED</tt> state.
	 * <p>The value of <tt>INSTALLED</tt> is 0x00000002.
	 */
    public static final int INSTALLED = 0x00000002;

    /**
	 * This bundle is resolved and is able to be started.
	 *
	 * <p>A bundle is in the <tt>RESOLVED</tt> state when the Framework has successfully
	 * resolved the bundle's dependencies. These dependencies include:
	 * <ul>
	 * <li>The bundle's class path from its {@link Constants#BUNDLE_CLASSPATH} Manifest header.
	 * <li>The bundle's package dependencies from
	 * its {@link Constants#EXPORT_PACKAGE}and {@link Constants#IMPORT_PACKAGE} Manifest headers.
	 * </ul>
	 * <p>Note that the bundle is not active yet. A bundle must be put in the
	 * <tt>RESOLVED</tt> state before it can be started. The Framework may attempt to
	 * resolve a bundle at any time.
	 * <p>The value of <tt>RESOLVED</tt> is 0x00000004.
	 */
    public static final int RESOLVED = 0x00000004;


    public static final int STARTING = 0x00000008;


    public static final int STOPPING = 0x00000010;

    /**
	 * This bundle is now running.
	 *
	 * <p>A bundle is in the <tt>ACTIVE</tt> state when it has been successfully started.
	 * <p>The value of <tt>ACTIVE</tt> is 0x00000020.
	 */
    public static final int ACTIVE    = 0x00000020;

    /**
	 * Returns this bundle's current state.
	 *
	 * <p>A bundle can be in only one state at any time.
	 *
	 * @return An element of <tt>UNINSTALLED</tt>, <tt>INSTALLED</tt>,
	 * <tt>RESOLVED</tt>, <tt>STARTING</tt>, <tt>STOPPING</tt>,
	 * <tt>ACTIVE</tt>.
	 */
    public abstract int getState();


    public abstract void start() throws BundleException;

    public abstract void stop() throws BundleException;

    /**
	 * Uninstalls this bundle.
	 *
	 * <p>This method causes the Framework to notify other bundles that this bundle
	 * is being uninstalled, and then puts this bundle into the <tt>UNINSTALLED</tt>
	 * state. The Framework will remove any resources related to this
	 * bundle that it is able to remove.
	 *
	 * <p>If this bundle has exported any packages, the Framework will
	 * continue to make these packages available to their importing bundles
	 * until the <tt>PackageAdmin.refreshPackages</tt> method has been called
	 * or the Framework is relaunched.
	 *
	 * <p>The following steps are required to uninstall a bundle:
	 * <ol>
	 * <li>If this bundle's state is <tt>UNINSTALLED</tt> then
	 * an <tt>IllegalStateException</tt> is thrown.
	 *
	 * <li>If this bundle's state is <tt>ACTIVE</tt>, <tt>STARTING</tt> or <tt>STOPPING</tt>,
	 * this bundle is stopped as described in the <tt>Bundle.stop</tt> method.
	 * If <tt>Bundle.stop</tt> throws an exception, a Framework event of type
	 * {@link FrameworkEvent#ERROR}is broadcast containing the exception.
	 *
	 * <li>This bundle's state is set to <tt>UNINSTALLED</tt>.
	 *
	 * <li>A bundle event of type {@link BundleEvent#UNINSTALLED}is broadcast.
	 *
	 * <li>This bundle and any persistent storage area provided for this bundle
	 * by the Framework are removed.
	 * </ol>
	 *
	 * <b>Preconditions</b>
	 * <ul>
	 * <li><tt>getState()</tt> not in {<tt>UNINSTALLED</tt>}.
	 * </ul>
	 * <b>Postconditions, no exceptions thrown</b>
	 * <ul>
	 * <li><tt>getState()</tt> in {<tt>UNINSTALLED</tt>}.
	 * <li>This bundle has been uninstalled.
	 * </ul>
	 * <b>Postconditions, when an exception is thrown</b>
	 * <ul>
	 * <li><tt>getState()</tt> not in {<tt>UNINSTALLED</tt>}.
	 * <li>This Bundle has not been uninstalled.
	 * </ul>
	 *
	 * @exception BundleException If the uninstall failed.
	 * This can occur if another thread is attempting to change the bundle's state
	 * and does not complete in a timely manner.
	 * @exception java.lang.IllegalStateException If this
	 * bundle has been uninstalled or this bundle tries to change its own state.
	 * @exception java.lang.SecurityException If the caller does not have
	 * the appropriate <tt>AdminPermission</tt>, and the Java Runtime Environment
	 * supports permissions.
	 * @see #stop()
	 */
    public abstract void uninstall() throws BundleException;

    /**
	 * Returns this bundle's identifier. The bundle is assigned a unique identifier by the Framework
	 * when it is installed in the OSGi environment.
	 *
	 * <p>A bundle's unique identifier has the following attributes:
	 * <ul>
	 * <li>Is unique and persistent.
	 * <li>Is a <tt>long</tt>.
	 * <li>Its value is not reused for another bundle, even after the bundle is uninstalled.
	 * <li>Does not change while the bundle remains installed.
	 * <li>Does not change when the bundle is updated.
	 * </ul>
	 *
	 * <p>This method will continue to return this bundle's unique identifier
	 * while this bundle is in the <tt>UNINSTALLED</tt> state.
	 *
	 * @return The unique identifier of this bundle.
	 */
    public abstract long getBundleId();


    public abstract String getLocation();

    /**
	 * Find the specified resource in this bundle.
	 *
	 * This bundle's class loader is called to search for the named resource.
	 * If this bundle's state is <tt>INSTALLED</tt>, then only this bundle will
	 * be searched for the specified resource. Imported packages cannot be searched
	 * when a bundle has not been resolved.
	 *
	 * @param name The name of the resource.
	 * See <tt>java.lang.ClassLoader.getResource</tt> for a description of
	 * the format of a resource name.
	 * @return a URL to the named resource, or <tt>null</tt> if the resource could
	 * not be found or if the caller does not have
	 * the <tt>AdminPermission</tt>, and the Java Runtime Environment supports permissions.
	 *
	 * @since 1.1
	 * @exception java.lang.IllegalStateException If this bundle has been uninstalled.
	 */
    public abstract URL getResource(String name);

}

