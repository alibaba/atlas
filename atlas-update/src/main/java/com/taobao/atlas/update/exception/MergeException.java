package com.taobao.atlas.update.exception;

/**
 * Created by wuzhong on 2016/11/23.
 */

public class MergeException extends Exception {

    public MergeException() {
    }

    public MergeException(String detailMessage) {
        super(detailMessage);
    }

    public MergeException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public MergeException(Throwable throwable) {
        super(throwable);
    }
}
