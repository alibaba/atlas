package com.taobao.atlas.dex.util;

import java.util.Comparator;

/**
 * Created by shenghua.nish on 2016-01-05 下午4:41.
 */
public class NaturalOrdering implements Comparator<Comparable> {
    @Override
    public int compare(Comparable left, Comparable right) {
        return left.compareTo(right);

    }
}
