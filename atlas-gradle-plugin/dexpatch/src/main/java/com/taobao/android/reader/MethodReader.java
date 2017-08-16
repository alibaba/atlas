package com.taobao.android.reader;
import org.jf.dexlib2.iface.Method;

/**
 * @author lilong
 * @create 2017-08-15 下午4:35
 */

public class MethodReader implements Reader {

    private Reader reader;

    public MethodReader(Reader reader) {
        if (reader!= null){
            this.reader = reader;
        }
    }

    @Override
    public Method read(String className, String member) throws Exception {
        org.jf.dexlib2.iface.ClassDef classDef = (org.jf.dexlib2.iface.ClassDef) reader.read(className,null);
        for(Method method:classDef.getMethods()){
            if (method.getName().equals(member)){
                return method;
            }
        }
        return null;
    }
}
