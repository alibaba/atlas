

package org.osgi.framework;

import java.util.EventObject;
import java.util.Map;

/**
 * A Framework event describing a bundle lifecycle change.
 * <p><tt>BundleEvent</tt> objects are delivered to <tt>BundleListener</tt> objects when a change
 * occurs in a bundle's lifecycle. A type code is used to identify the event type
 * for future extendability.
 *
 * <p>OSGi reserves the right to extend the set of types.
 *
 * @version $Revision: 1.1 $
 * @author Open Services Gateway Initiative
 */

public class BundleEvent extends EventObject
{
    /**
     * Bundle that had a change occur in its lifecycle.
     */
    private transient Bundle bundle;

    /**
     * Type of bundle lifecycle change.
     */
    private transient int type;

    private long elapsed;
    private Map<String, Object> detail;

    /**
     * This bundle has been installed.
     * <p>The value of <tt>INSTALLED</tt> is 0x00000001.
     *
     * @see BundleContext#installBundle
     */
    public final static int INSTALLED = 0x00000001;

    /**
     * This bundle has been started.
     * <p>The value of <tt>STARTED</tt> is 0x00000002.
     *
     * @see Bundle#start
     */
    public final static int STARTED = 0x00000002;

    /**
     * This bundle has been stopped.
     * <p>The value of <tt>STOPPED</tt> is 0x00000004.
     *
     * @see Bundle#stop
     */
    public final static int STOPPED = 0x00000004;

    /**
     * This bundle has been updated.
     * <p>The value of <tt>UPDATED</tt> is 0x00000008.
     *
     * @see Bundle#update
     */
    public final static int UPDATED = 0x00000008;

    /**
     * This bundle has been uninstalled.
     * <p>The value of <tt>UNINSTALLED</tt> is 0x00000010.
     *
     * @see Bundle#uninstall
     */
    public final static int UNINSTALLED = 0x00000010;

    public final static int BEFORE_INSTALL = 10086;
    public final static int BEFORE_STARTED = 10087;
    public final static int DEXOPTED = 10088;
    public final static int ALL_DEXOPTED = 10089;
    public final static int ACTIVEED = 10090;

    /**
     * Creates a bundle event of the specified type.
     *
     * @param type The event type.
     * @param bundle The bundle which had a lifecycle change.
     */

    public BundleEvent(int type, Bundle bundle)
    {
        super(bundle);
        this.bundle = bundle;
        this.type = type;
    }

    public BundleEvent(int type, Bundle bundle, long elapsed, Map<String, Object> detail) {
        super(bundle);
        this.bundle = bundle;
        this.type = type;
        this.elapsed = elapsed;
        this.detail = detail;
    }

    /**
     * Returns the bundle which had a lifecycle change.
     * This bundle is the source of the event.
     *
     * @return A bundle that had a change occur in its lifecycle.
     */
    public Bundle getBundle()
    {
        return bundle;
    }

    /**
     * Returns the type of lifecyle event.
     * The type values are:
     * <ul>
     * <li>{@link #INSTALLED}
     * <li>{@link #STARTED}
     * <li>{@link #STOPPED}
     * <li>{@link #UPDATED}
     * <li>{@link #UNINSTALLED}
     * </ul>
     *
     * @return The type of lifecycle event.
     */

    public int getType()
    {
        return type;
    }

    public long getElapsed() {
        return elapsed;
    }

    public Map<String, Object> getDetail() {
        return detail;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("BundleEvent{");
        sb.append("bundle=").append(bundle);
        sb.append(", type=").append(typeToString());
        sb.append(", elapsed=").append(elapsed);
        sb.append(", detail=").append(detail);
        sb.append('}');
        return sb.toString();
    }

    private String typeToString() {
        switch (type) {
            case INSTALLED:
                return "INSTALLED";
            case STARTED:
                return "STARTED";
            case STOPPED:
                return "STOPPED";
            case UPDATED:
                return "UPDATED";
            case UNINSTALLED:
                return "UNINSTALLED";
            case BEFORE_INSTALL:
                return "BEFORE_INSTALL";
            case BEFORE_STARTED:
                return "BEFORE_STARTED";
            case DEXOPTED:
                return "DEXOPTED";
            case ALL_DEXOPTED:
                return "ALL_DEXOPTED";
            case ACTIVEED:
                return "ACTIVEED";

            default:
                return "UNKNOWN=" + type;
        }
    }
}


