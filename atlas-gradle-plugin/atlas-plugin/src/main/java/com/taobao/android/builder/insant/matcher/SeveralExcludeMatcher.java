package com.taobao.android.builder.insant.matcher;

import java.util.ArrayList;
import java.util.List;

/**
 * 创建日期：2019/2/27 on 下午3:43
 * 描述:
 * 作者:zhayu.ll
 */
public class SeveralExcludeMatcher implements Imatcher {


    private String rule;
    List<ExcludeMatcher>excludeMatchers = new ArrayList<>();

    public SeveralExcludeMatcher(String rule) {
        this.rule = rule;
        String[]rules = rule.split("\\|");
       for (int i = 0; i < rules.length; i ++){
           if (i == 0){
               excludeMatchers.add(new ExcludeMatcher(rules[i]));
           }else {
               excludeMatchers.add(new ExcludeMatcher("!"+rules[i]));

           }
       }
    }

    @Override
    public boolean match(String s) {
        boolean hasMatched = false;
        for (ExcludeMatcher excludeMatcher:excludeMatchers){
            if (!excludeMatcher.match(s)){
                return false;
            }else {
                hasMatched = true;
            }
        }


        return hasMatched;
    }

    @Override
    public String rule() {
        return rule;
    }

    @Override
    public Imatcher superMatcher() {
        return null;
    }
}
