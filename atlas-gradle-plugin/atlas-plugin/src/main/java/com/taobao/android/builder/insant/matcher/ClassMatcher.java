package com.taobao.android.builder.insant.matcher;

/**
 * 创建日期：2018/11/23 on 下午3:01
 * 描述:
 * 作者:zhayu.ll
 */
public class ClassMatcher implements Imatcher {

    private String mRule;
    public ClassMatcher(String rule) {
        this.mRule = rule;
    }

    @Override
    public boolean match(String s) {
        if(!s.endsWith(".class")){
            return false;
        }
        String className = s.replace("/", ".").substring(0, s.length() - 6);

        return mRule.equals(className);
    }

    @Override
    public String rule() {
        return mRule;
    }

    @Override
    public Imatcher superMatcher() {

        return new SubPackgeMatcher(mRule.substring(0,mRule.lastIndexOf("."))+".*");
    }
}
