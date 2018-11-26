package com.taobao.android.builder.insant.matcher;

/**
 * 创建日期：2018/11/23 on 下午3:02
 * 描述:
 * 作者:zhayu.ll
 */
public class PackageMatcher implements Imatcher {

    private String rule;

    public PackageMatcher(String rule) {
        this.rule = rule;
    }

    @Override
    public boolean match(String s) {
        if (s.endsWith(".class")) {
            String className = s.replace("/", ".").substring(0, s.length() - 6);
            String tempRule = rule.substring(0, rule.lastIndexOf("."));
            if (className.startsWith(tempRule)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String rule() {
        return rule;
    }

    @Override
    public Imatcher superMatcher() {
        String tempRule = rule.substring(0, rule.lastIndexOf("."));
        return new PackageMatcher(tempRule.substring(0,tempRule.lastIndexOf("."))+".**");
    }
}
