package com.taobao.android.builder.insant;

import com.android.build.gradle.internal.api.AppVariantContext;
import com.taobao.android.builder.insant.visitor.ModifyClassVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * com.taobao.android.builder.insant
 *
 * @author lilong
 * @time 2:37 PM
 * @date 2020/5/19
 */
public class ModifyClassFinder {

    private AppVariantContext variantContext;

    public ModifyClassFinder(AppVariantContext appVariantContext) {
        this.variantContext = appVariantContext;
    }

    public void parseClassPolicy(File file, CodeChange codeChange) {

        BufferedInputStream inputStream = null;
        try {
            inputStream = new BufferedInputStream(new FileInputStream(file));
            ClassReader classReader = new ClassReader(inputStream);
            classReader.accept(new ModifyClassVisitor(Opcodes.ASM5, codeChange), ClassReader.SKIP_CODE);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (!variantContext.getBuildType().getPatchConfig().isCreateIPatch()) {
            codeChange.py = PatchPolicy.NONE;
            return;
        }
    }

    public boolean parseJarPolicies(File file, Collection<CodeChange> codeChanges) throws IOException {
        JarFile jarFile = newJarFile(file);
        boolean hasChange = false;
        if (jarFile != null){
            Enumeration<JarEntry> enumeration = jarFile.entries();
            while (enumeration.hasMoreElements()){
                JarEntry jarEntry = enumeration.nextElement();
                if (!jarEntry.getName().endsWith(".class")){
                    continue;
                }
                CodeChange codeChange = new CodeChange();
                InputStream inputStream = jarFile.getInputStream(jarEntry);
                ClassReader classReader = new ClassReader(inputStream);
                classReader.accept(new ModifyClassVisitor(Opcodes.ASM5, codeChange), ClassReader.SKIP_CODE);
                if (!variantContext.getBuildType().getPatchConfig().isCreateIPatch()) {
                    codeChange.py = PatchPolicy.NONE;
                    return false;
                }else if (codeChange.getPy() != PatchPolicy.NONE){
                    hasChange = true;
                    codeChanges.add(codeChange);
                }
            }
            jarFile.close();
        }
        return hasChange;
    }

    private JarFile newJarFile(File file){
        if (file != null && file.exists()){
            try {
                return new JarFile(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;

    }


    public enum PatchPolicy {

        ADD, MODIFY, NONE
    }

    public static class CodeChange {

        private String code;

        public boolean isAnnotation() {
            return annotation;
        }

        public void setAnnotation(boolean annotation) {
            this.annotation = annotation;
        }

        private boolean annotation;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        private PatchPolicy py = PatchPolicy.NONE;

        public PatchPolicy getPy() {
            return py;
        }

        public void setPy(PatchPolicy py) {
            this.py = py;
        }

        public List<CodeChange> getCodeChanges() {
            return codeChanges;
        }

        public void setCodeChanges(List<CodeChange> codeChanges) {
            this.codeChanges = codeChanges;
        }

        private List<CodeChange> codeChanges = new ArrayList<>();

        @Override
        public String toString() {
            return "CodeChange{" +
                    "code='" + code + '\'' +
                    ", annotation=" + annotation +
                    ", py=" + py +
                    ", codeChanges=" + codeChanges +
                    '}';
        }
    }
}
