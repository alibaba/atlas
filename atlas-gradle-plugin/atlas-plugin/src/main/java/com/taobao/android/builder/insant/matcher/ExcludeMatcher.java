package com.taobao.android.builder.insant.matcher;

/**
 * 创建日期：2018/11/23 on 下午4:11
 * 描述:
 * 作者:zhayu.ll
 */
public class ExcludeMatcher implements Imatcher {

    private String mRule;

    private Imatcher imatcher;

    public ExcludeMatcher(String rule) {
        this.mRule = rule;
    }

    @Override
    public boolean match(String s) {
        String tempRule = mRule.substring(1);
        if (imatcher == null) {
            imatcher = MatcherCreator.create(tempRule);
        }

        if (imatcher.match(s)) {
            return false;
        }

       Imatcher sMatcher = imatcher.superMatcher();

        if (sMatcher.match(s)){
            return true;
        }else {
            return false;
        }
    }

    @Override
    public String rule() {
        return mRule;
    }

    @Override
    public Imatcher superMatcher() {
        String tempRule = mRule.substring(1);
        if (imatcher == null) {
            imatcher = MatcherCreator.create(tempRule);
        }
        String superRule = imatcher.superMatcher().rule();

        return new ExcludeMatcher("!".concat(superRule));

    }
}
