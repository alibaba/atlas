package com.taobao.android.builder.extension;

/**
 * @author lilong
 * @create 2017-05-26 On the afternoon of mount zalmon
 */

public class DexConfig {

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    private String name;

    public DexConfig(String name){
        this.name = name;
    }

    private boolean useMyDex = false;

    public boolean isUseMyDex() {
        return useMyDex;
    }

    public void setUseMyDex(boolean useMyDex) {
        this.useMyDex = useMyDex;
    }

    public boolean isDexInProcess() {
        return dexInProcess;
    }

    public void setDexInProcess(boolean dexInProcess) {
        this.dexInProcess = dexInProcess;
    }

    private boolean dexInProcess = false;



}
