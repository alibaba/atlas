package com.taobao.android.reader;

import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.iface.ClassDef;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * @author lilong
 * @create 2017-08-15 下午1:47
 */

public class DexReader implements Reader {
    protected Set<? extends ClassDef>classDefs;

    public DexReader(File file) throws IOException {
        if (file.exists()){
            org.jf.dexlib2.iface.DexFile dexFile = DexFileFactory.loadDexFile(file,19,true);
            this.classDefs = dexFile.getClasses();
        }
    }

    @Override
    public Set<? extends ClassDef> read(String name,String memberName) {
        return classDefs;
    }
}
