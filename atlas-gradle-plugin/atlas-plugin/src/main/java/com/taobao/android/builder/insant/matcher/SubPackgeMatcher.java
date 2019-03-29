package com.taobao.android.builder.insant.matcher;

/**
 * 创建日期：2018/11/23 on 下午3:02
 * 描述:
 * 作者:zhayu.ll
 */
public class SubPackgeMatcher implements Imatcher {

    private String mRule;
    public SubPackgeMatcher(String rule) {
            this.mRule = rule;
    }

    @Override
    public boolean match(String s) {
        if (s.endsWith(".class")) {
            String className = s.replace("/", ".").substring(0, s.length() - 6);
            String tempRule = mRule.substring(0, mRule.lastIndexOf("."));
            if (className.substring(0, className.lastIndexOf(".")).equals(tempRule)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String rule() {
        return mRule;
    }

    @Override
    public Imatcher superMatcher() {
        return null;
    }
}
