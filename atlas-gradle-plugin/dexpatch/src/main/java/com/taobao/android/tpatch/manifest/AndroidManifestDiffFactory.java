package com.taobao.android.tpatch.manifest;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author lilong
 * @create 2017-03-13 上午9:55
 */

public class AndroidManifestDiffFactory {

    public Set<DiffItem>diffResuit = new HashSet<DiffItem>();

    public void diff(File baseAndroidManifest,File newAndroidManifest) throws Exception {
        Manifest baseManifest = AXMLPrint.paresManfiest(baseAndroidManifest);
        Manifest newManifest = AXMLPrint.paresManfiest(newAndroidManifest);
        diff(baseManifest,newManifest);


    }

    private void diff(Manifest baseManifest, Manifest newManifest) {

       diff(baseManifest.getApplication().activitys,newManifest.getApplication().activitys);
       diff(baseManifest.getApplication().providers,newManifest.getApplication().providers);
       diff(baseManifest.getApplication().services,newManifest.getApplication().services);
       diff(baseManifest.getApplication().receivers,newManifest.getApplication().receivers);
    }

    private void diff(Collection<? extends Manifest.AndroidBase> baseCollection, Collection<? extends Manifest.AndroidBase>newCollection){
        if (newCollection == null||baseCollection == null){
            return;
        }
        for (Manifest.AndroidBase newAndroidBase:newCollection){
            String name = newAndroidBase.name;
            boolean find = false;
            for (Manifest.AndroidBase baseAndroidBase:baseCollection){
                String baseName = baseAndroidBase.name;
                if (baseName.equals(name)){
                    find = true;
                    if (baseAndroidBase.toString().equals(newAndroidBase.toString())){
                        //do nothind
//                        diffResuit.add(new DiffItem(DiffType.NONE,newAndroidBase));
                    }else {
                        diffResuit.add(new DiffItem(DiffType.MODIFY,newAndroidBase));
                    }

                    break;
                }
            }
            if (!find){
                diffResuit.add(new DiffItem(DiffType.ADD,newAndroidBase));
            }
        }



    }


    public class DiffItem {

        public DiffType diffType;
        public Manifest.AndroidBase Component;

        public DiffItem(DiffType diffType, Manifest.AndroidBase name) {
            this.diffType = diffType;
            this.Component = name;
        }
    }

    enum DiffType{
        ADD,REMOVE,MODIFY,NONE
    }

    public static void main(String[]args) throws Exception {
        AndroidManifestDiffFactory androidManifestDiffFactory = new AndroidManifestDiffFactory();
        androidManifestDiffFactory.diff(new File("/Users/lilong/Downloads/10004583@taobao_android_6.5.0/AndroidManifest.xml"),new File("/Users/lilong/Downloads/tpatch-diff/AndroidManifest.xml"));
        Set<DiffItem>sets = androidManifestDiffFactory.diffResuit;
        System.out.println("xxx");
    }


}
