package com.taobao.android.builder.tasks.awo.projectstrucure;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by wuzhong on 2017/2/25.
 */
public class ModuleInfo {

    public String packageName;

    public String group;

    public String name;

    public String type;

    public String version;

    //模块相对于根目录的路径
    public String path;

    //java源代码目录
    public List<String> java_SrcDirs = new ArrayList();

    public List<String> res_srcDirs = new ArrayList();

    public List<String> assets_srcDirs = new ArrayList();
}
