package com.taobao.android.builder.insant.matcher;

/**
 * 创建日期：2018/11/23 on 下午3:03
 * 描述:
 * 作者:zhayu.ll
 */
public class NoMatcher implements Imatcher {
    @Override
    public boolean match(String s) {
        return false;
    }

    @Override
    public String rule() {
        return null;
    }

    @Override
    public Imatcher superMatcher() {
        return new NoMatcher();
    }
}
