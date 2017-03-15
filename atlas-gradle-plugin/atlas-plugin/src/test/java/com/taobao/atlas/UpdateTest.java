//package com.taobao.atlas;
//
//import com.alibaba.fastjson.JSON;
//import com.taobao.android.object.BuildPatchInfos;
//import com.taobao.android.object.PatchBundleInfo;
//import com.taobao.android.object.PatchInfo;
//
//import org.apache.commons.io.FileUtils;
//import org.junit.Test;
//
//import java.io.File;
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.List;
//
///**
// * Created by wuzhong on 2016/12/20.
// */
//public class UpdateTest {
//
//    @Test
//    public void test() throws IOException {
//
//        //        System.out.println(123);
//
//        File file = new File(getClass().getClassLoader().getResource("patchs.json").getFile());
//
//        String json = FileUtils.readFileToString(file);
//
//        BuildPatchInfos patchInfos = JSON.parseObject(json, BuildPatchInfos.class);
//
//        UpdateInfo updateInfo = new UpdateInfo();
//        updateInfo.baseVersion = patchInfos.getBaseVersion();
//        updateInfo.updateVersion = patchInfos.getPatches().get(0).getPatchVersion();
//
//        PatchInfo patchInfo = patchInfos.getPatches().get(0);
//
//        List<UpdateInfo.Item> items = new ArrayList<UpdateInfo.Item>();
//        for (PatchBundleInfo patchBundleInfo : patchInfo.getBundles()) {
//            UpdateInfo.Item item = new UpdateInfo.Item();
//            items.add(item);
//            item.dependency = patchBundleInfo.getDependency();
//            item.isMainDex = patchBundleInfo.getMainBundle();
//            item.name = patchBundleInfo.getPkgName();
//            //            item.srcVersion = patchBundleInfo.getVersion();
//            item.version = updateInfo.baseVersion + "@" + patchBundleInfo.getVersion();
//        }
//        updateInfo.updateBundles = items;
//
//        System.out.println(updateInfo);
//    }
//}
