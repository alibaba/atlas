package com.taobao.android.reader;

/**
 * @author lilong
 * @create 2017-08-15 下午1:24
 */

public interface Reader<T> {

    public T read(String className,String member) throws Exception;

}
