

package org.osgi.framework;

/**
 * A Framework exception used to indicate that a bundle lifecycle problem occurred.
 *
 * <p><tt>BundleException</tt> object is created by the Framework to denote an exception condition
 * in the lifecycle of a bundle.
 * <tt>BundleException</tt>s should not be created by bundle developers.
 *
 * @version $Revision: 1.1 $
 * @author Open Services Gateway Initiative
 */

public class BundleException extends Exception
{
    /**
     * Nested exception.
     */
    private transient Throwable throwable;

    public BundleException(Throwable throwable) {
        super(throwable);
        this.throwable = throwable;
    }

    /**
     * Creates a <tt>BundleException</tt> that wraps another exception.
     *
     * @param msg The associated message.
     * @param throwable The nested exception.
     */
    public BundleException(String msg, Throwable throwable)
    {
        super(msg, throwable);
        this.throwable = throwable;
    }

    /**
     * Creates a <tt>BundleException</tt> object with the specified message.
     *
     * @param msg The message.
     */
    public BundleException(String msg)
    {
        super(msg);
        this.throwable = null;
    }
    
}


