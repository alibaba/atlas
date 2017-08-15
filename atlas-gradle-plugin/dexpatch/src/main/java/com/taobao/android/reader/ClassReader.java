package com.taobao.android.reader;

import org.jf.dexlib2.iface.ClassDef;

import java.util.Set;

/**
 * @author lilong
 * @create 2017-08-15 下午1:52
 */

public class ClassReader implements Reader {
    private Set<ClassDef>classDefs;

    public ClassReader(Reader reader) throws Exception {
        if (reader!= null && reader instanceof DexReader){
            classDefs = (Set<ClassDef>) reader.read(null,null);
        }
    }
    public ClassDef read(String className,String memberName){
        if (classDefs == null){
            return null;
        }
        for (ClassDef classDef:classDefs){
            if (classDef.getType().equals(className)){
                return classDef;
            }
        }
        return null;
    }
}
