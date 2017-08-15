package com.taobao.android.reader;


import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Field;

/**
 * @author lilong
 * @create 2017-08-15 下午1:18
 */

public class FieldReader implements Reader{
    private ClassDef classDef;
    private Reader reader;

    public FieldReader(Reader reader)throws Exception {
           if (reader instanceof ClassReader){
               this.reader = reader;
           }
    }
    public Field read(String className,String member) throws Exception {
        if (reader == null){
            return null;
        }
        classDef = (ClassDef) reader.read(className,null);
        Iterable<? extends Field> fields = classDef.getFields();
        for (Field field:fields){
            if (field.getName().equals(member)){
                return field;
            }
        }
    return null;
    }


}
