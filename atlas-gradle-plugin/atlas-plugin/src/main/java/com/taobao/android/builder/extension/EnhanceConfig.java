package com.taobao.android.builder.extension;

import java.io.File;

/**
 * EnhanceConfig
 *
 * @author zhayu.ll
 * @date 18/3/14
 */
public class EnhanceConfig {

    private String name;

    private String version;

    private boolean enabled;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    private File blackmap;

    private File bundlecfg;

    private File keepso;

    public File getEnhanceJar() {
        return enhanceJar;
    }

    public void setEnhanceJar(File enhanceJar) {
        this.enhanceJar = enhanceJar;
    }

    private File enhanceJar;

    public String getDependency() {
        return dependency.concat(":"+version);
    }

    public void setDependency(String dependency) {
        this.dependency = dependency;
    }

    private String dependency = "com.ali.mobisecObfuscate:taobao-obf-tool-chain";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public File getBlackmap() {
        return blackmap;
    }

    public void setBlackmap(File blackmap) {
        this.blackmap = blackmap;
    }

    public File getBundlecfg() {
        return bundlecfg;
    }

    public void setBundlecfg(File bundlecfg) {
        this.bundlecfg = bundlecfg;
    }

    public File getKeepso() {
        return keepso;
    }

    public void setKeepso(File keepso) {
        this.keepso = keepso;
    }

    public String getObfnamemode() {
        return obfnamemode;
    }

    public void setObfnamemode(String obfnamemode) {
        this.obfnamemode = obfnamemode;
    }

    public boolean isMovedebuginfo() {
        return movedebuginfo;
    }

    public void setMovedebuginfo(boolean movedebuginfo) {
        this.movedebuginfo = movedebuginfo;
    }

    public boolean isOpenj2c() {
        return openj2c;
    }

    public void setOpenj2c(boolean openj2c) {
        this.openj2c = openj2c;
    }

    public File getJ2ccfg() {
        return j2ccfg;
    }

    public void setJ2ccfg(File j2ccfg) {
        this.j2ccfg = j2ccfg;
    }

    public File getJ2cpolicy() {
        return j2cpolicy;
    }

    public void setJ2cpolicy(File j2cpolicy) {
        this.j2cpolicy = j2cpolicy;
    }

    private String obfnamemode;

    private boolean movedebuginfo;

    private boolean openj2c;

    private File j2ccfg;

    private File j2cpolicy;

    public EnhanceConfig(String name) {
        this.name = name;

    }
}
