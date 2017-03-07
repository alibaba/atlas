package com.taobao.atlas.dexmerge;

/**
 * Created by xieguo on 15-9-16.
 */
public class MergeException extends Exception{
    /**
     * Nested exception.
     */
    private transient Throwable throwable;


    /**
     * Creates a <tt>LowDiskException</tt> that wraps another exception.
     *
     * @param msg The associated message.
     * @param throwable The nested exception.
     */
    public MergeException(String msg, Throwable throwable)
    {
        super(msg, throwable);
        this.throwable = throwable;
    }

    /**
     * Creates a <tt>LowDiskException</tt> object with the specified message.
     *
     * @param msg The message.
     */
    public MergeException(String msg)
    {
        super(msg);
        this.throwable = null;
    }

    /**
     * Returns any nested exceptions included in this exception.
     *
     * @return The nested exception; <tt>null</tt> if there is
     * no nested exception.
     */
    public Throwable getNestedException()
    {
        return(throwable);
    }
}
