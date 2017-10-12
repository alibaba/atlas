package com.taobao.android;


import com.taobao.android.dex.Dex;
import com.taobao.android.dx.merge.CollisionPolicy;
import com.taobao.android.transform.DexTransform;

import java.io.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * @author lilong
 * @create 2017-07-10 上午11:03
 */

public class ClassDefSorter {

    private Dex dex;
    private List<String> classes = new ArrayList<>();
    private File outDexFile;

    public ClassDefSorter(File dexFile, List<String> classes, File outDexFile) throws IOException {
        this.dex = new Dex(dexFile);
        this.classes = classes;
        this.outDexFile = outDexFile;
    }

    public File sort() throws IOException {
//        List<ClassDef> classDefList = new ArrayList<>();
//        Iterable<ClassDef> classDefs = dex.classDefs();
//        for (ClassDef classDef : classDefs) {
//            classDefList.add(classDef);
//        }
//        Collections.sort(classDefList, new Comparator<ClassDef>() {
//            @Override
//            public int compare(ClassDef o1, ClassDef o2) {
//                if (classes.contains(dex.typeNames().get(o1.getTypeIndex()))&& classes.contains(dex.typeNames().get(o2.getTypeIndex()))) {
//
//                    return classes.indexOf(dex.typeNames().get(o1.getTypeIndex())) - classes.indexOf(dex.typeNames().get(o2.getTypeIndex()));
//
//                } else if (classes.contains(dex.typeNames().get(o1.getTypeIndex())) && !classes.contains(dex.typeNames().get(o2.getTypeIndex()))) {
//                    return -1;
//                } else if (!classes.contains(dex.typeNames().get(o1.getTypeIndex())) && classes.contains(dex.typeNames().get(o2.getTypeIndex()))) {
//                    return 1;
//                } else return o1.getTypeIndex()-(o2.getTypeIndex());
//            }
//        });

//        dex.classDefs()
        DexTransform dexTransform = new DexTransform(new Dex[]{new Dex(0),dex}, CollisionPolicy.FAIL);
        dexTransform.setClassList(classes);
        Dex dex = dexTransform.transform();
        dex.writeTo(outDexFile);
//        return outDexFile;
        return outDexFile;


    }

    public static void main(String[] args) throws IOException {
//        File classFile = new File("/Users/lilong/Documents/main_builder/classDefList");
//        List<String> classDefs = new ArrayList<>();
//        BufferedReader fileReader = new BufferedReader(new FileReader(classFile));
//        String line = null;
//        while ((line = fileReader.readLine()) != null) {
//            if (line.indexOf("L") != -1) {
//                classDefs.add(line.substring(line.indexOf("L")));
//            }
//        }
//            ClassDefSorter classDefSorter = new ClassDefSorter(new File("/Users/lilong/Downloads/taobao-android-release1/classes.dex"), classDefs, new File("/Users/lilong/Downloads/c.dex"));
//            classDefSorter.sort();
//        }
        File inFile = new File("/Users/lilong/Documents/mtl-gradle-plugin/1.txt");
        LinkedHashSet<String> classes = new LinkedHashSet<>();
        BufferedReader fileReader = new BufferedReader(new FileReader(inFile));
        String line = null;
        while ((line =fileReader.readLine())!= null){
            if (line.indexOf("L")!= -1) {
                classes.add(line.substring(line.indexOf("L")));
            }else {
                System.out.println(line);
            }
        }
        File outFile = new File("/Users/lilong/Documents/main_builder/classDefList");
        BufferedOutputStream fileOutputStream = new BufferedOutputStream(new FileOutputStream(outFile));
        FileWriter fileWriter = new FileWriter(outFile);
        for (String clazz :classes){
            fileWriter.write(clazz);
            fileWriter.write("\n");
        }
        fileWriter.flush();
    }

}
