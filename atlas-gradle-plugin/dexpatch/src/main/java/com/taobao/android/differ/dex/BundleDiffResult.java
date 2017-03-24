package com.taobao.android.differ.dex;

import com.taobao.android.object.DiffType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author lilong
 * @create 2017-03-22 下午7:02
 */

public class BundleDiffResult implements Serializable {

    private String bundleName;

    public String getBundleName() {
        return bundleName;
    }

    public void setBundleName(String bundleName) {
        this.bundleName = bundleName;
    }

    public void setClassDiffs(List<ClassDiff> classDiffs) {
        this.classDiffs = classDiffs;
    }

    public List<ClassDiff> getClassDiffs() {
        return classDiffs;
    }

    private List<ClassDiff>classDiffs = new ArrayList<>();

    public static class ClassDiff implements Serializable{
        private String className;

        private DiffType diffType;

        private List<MethodDiff>methodDiffs = new ArrayList<>();

        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public DiffType getDiffType() {
            return diffType;
        }

        public void setDiffType(DiffType diffType) {
            this.diffType = diffType;
        }

        public List<MethodDiff> getMethodDiffs() {
            return methodDiffs;
        }

        public void setMethodDiffs(List<MethodDiff> methodDiffs) {
            this.methodDiffs = methodDiffs;
        }

        public List<FieldDiff> getFieldDiffs() {
            return fieldDiffs;
        }

        public void setFieldDiffs(List<FieldDiff> fieldDiffs) {
            this.fieldDiffs = fieldDiffs;
        }

        private List<FieldDiff>fieldDiffs = new ArrayList<>();

    }

    public static class MethodDiff implements Serializable{
        private String methodDesc;

        public String getMethodDesc() {
            return methodDesc;
        }

        public void setMethodDesc(String methodDesc) {
            this.methodDesc = methodDesc;
        }

        public DiffType getDiffType() {
            return diffType;
        }

        public void setDiffType(DiffType diffType) {
            this.diffType = diffType;
        }

        private DiffType diffType;
    }

    public static class FieldDiff implements Serializable{
        private String fieldDesc;

        public String getFieldDesc() {
            return fieldDesc;
        }

        public void setFieldDesc(String fieldDesc) {
            this.fieldDesc = fieldDesc;
        }

        public DiffType getDiffType() {
            return diffType;
        }

        public void setDiffType(DiffType diffType) {
            this.diffType = diffType;
        }

        private DiffType diffType;
    }
}
