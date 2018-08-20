package com.taobao.android.inputs;

import java.util.HashSet;
import java.util.Set;

/**
 * @author lilong
 * @create 2017-11-02 下午11:57
 */

public class DexPatchInput extends TpatchInput {

    public Set<String>excludeClasses;

    public Set<String>patchClasses = new HashSet<>();
}
