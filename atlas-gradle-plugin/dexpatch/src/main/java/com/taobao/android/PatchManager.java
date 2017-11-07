package com.taobao.android;

import com.android.utils.ILogger;
import com.taobao.android.inputs.BaseInput;
import com.taobao.android.outputs.PatchFile;
import com.taobao.android.tools.*;

/**
 * @author lilong
 * @create 2017-11-03 上午12:03
 */

public class PatchManager {
    private BaseInput input;
    public void setLogger(ILogger logger) {
        this.logger = logger;
    }

    private ILogger logger;

    public PatchManager(BaseInput input) {
        this.input = input;
    }

    public PatchFile doPatch() throws Exception {
        AbstractTool abstractTool = null;
       if (input.patchType.equals(PatchType.TPATCH)){
           abstractTool = new TPatchTool();
       }else if (input.patchType.equals(PatchType.DEXPATCH)){
           abstractTool = new DexPatchTool();
       }else if (input.patchType.equals(PatchType.APATCH)){
           abstractTool = new APatchTool();
       }else if (input.patchType.equals(PatchType.HOTFIX)){
           abstractTool = new HotPatchTool();
       }
       abstractTool.setInput(input);
       abstractTool.setLogger(logger);

       return abstractTool.doPatch();
    }

}
