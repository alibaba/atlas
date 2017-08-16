package com.taobao.android.reader;

import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.reference.StringReference;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author lilong
 * @create 2017-08-15 下午1:26
 */

public class AtlasFrameworkPropertiesReader implements Reader {
    private Reader reader;
    private Map map;

    public AtlasFrameworkPropertiesReader(Reader reader,LinkedHashMap map) {
            this.reader = reader;
            this.map = map;
    }
    public LinkedHashMap<String,BundleListing.BundleInfo>read(String className,String memberName) throws Exception {

        if (reader!= null) {
            Method method = (Method) reader.read(className, memberName);
            if (method!= null){
                Iterable<? extends Instruction> instructions = method.getImplementation().getInstructions();
                for (Instruction instruction:instructions){
                    if (instruction instanceof ReferenceInstruction){
                        if (((ReferenceInstruction) instruction).getReferenceType()== 0){
                            StringReference s = (StringReference) ((ReferenceInstruction) instruction).getReference();
                            return BundleListingUtil.parseArray(s.getString(), (LinkedHashMap<String, BundleListing.BundleInfo>) map);
                        }
                    }
                }
            }
        }
        return null;
    }
}
