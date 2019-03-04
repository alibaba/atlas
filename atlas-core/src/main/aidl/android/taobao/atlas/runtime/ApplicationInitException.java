package android.taobao.atlas.runtime;

/**
 * 创建日期：2019/3/4 on 下午1:54
 * 描述:
 * 作者:zhayu.ll
 */
public class ApplicationInitException extends Exception {

    public ApplicationInitException(Exception e) {
        super(e);
    }

    public ApplicationInitException(String format) {
        super(format);
    }
}
