package com.taobao.android.builder.insant.matcher;

/**
 * 创建日期：2018/11/23 on 下午3:01
 * 描述:
 * 作者:zhayu.ll
 */
public interface Imatcher {

        public boolean match(String s);

        public  String rule();


        public Imatcher superMatcher();

}
