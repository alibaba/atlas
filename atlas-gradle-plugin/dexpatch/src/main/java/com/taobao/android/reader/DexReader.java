package com.taobao.android.reader;

import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.DexFile;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * @author lilong
 * @create 2017-08-15 下午1:47
 */

public class DexReader implements Reader {
    protected Collection classDefs = new HashSet();

    public DexReader(File file) throws IOException {
        if (file.exists()){
            org.jf.dexlib2.iface.DexFile dexFile = DexFileFactory.loadDexFile(file,Opcodes.getDefault());
            this.classDefs = dexFile.getClasses();
        }
    }
    public DexReader(List<File>files) throws IOException {
        for (File file:files){
            DexFile dexFile =DexFileFactory.loadDexFile(file, Opcodes.getDefault());
            classDefs.addAll(dexFile.getClasses());
        }
    }

    @Override
    public Collection read(String name,String memberName) {
        return classDefs;
    }
}
