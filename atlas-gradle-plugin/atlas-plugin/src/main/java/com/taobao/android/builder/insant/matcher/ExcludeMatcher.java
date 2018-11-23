package com.taobao.android.builder.insant.matcher;

/**
 * 创建日期：2018/11/23 on 下午4:11
 * 描述:
 * 作者:zhayu.ll
 */
public class ExcludeMatcher implements Imatcher {

    private String mRule;

    public ExcludeMatcher(String rule) {
        this.mRule = rule;
    }

    @Override
    public boolean match(String s) {
        String tempRule = mRule.substring(1);
        Imatcher imatcher = MatcherCreator.create(tempRule);
        if (imatcher.match(s)) {
            return false;
        }else {
            return true;
        }
    }
}
