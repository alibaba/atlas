package com.taobao.android.builder.insant.matcher;

/**
 * 创建日期：2019/1/13 on 上午11:13
 * 描述:
 * 作者:zhayu.ll
 */
public class ImplementsMatcher  implements Imatcher{

    private String rule;

    public ImplementsMatcher(String rule) {
        this.rule = rule;
    }

    @Override
    public boolean match(String s) {
        String iFace = rule.substring(rule.indexOf("implements")+11);
        return iFace.equals(s.replace("/","."));
    }

    @Override
    public String rule() {
        return rule;
    }

    @Override
    public Imatcher superMatcher() {
        return null;
    }

    public Class<?> getClazz(ClassLoader classLoader) throws ClassNotFoundException {
        String iFace = rule.substring(rule.indexOf("implements")+11);
        return Class.forName(iFace, false, classLoader);

    }
}
