package com.taobao.zip;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.android.build.gradle.internal.api.ApContext;
import com.android.tools.r8.CompilationFailedException;
import com.taobao.android.builder.tools.zip.BetterZip;
import com.taobao.android.builder.tools.zip.SevenZip;

import org.junit.Test;

import java.io.File;
import java.io.IOException;

/**
 * com.taobao.zip
 *
 * @author lilong
 * @time 9:00 AM
 * @date 2020/3/19
 */
public class ZipTest {

    private static final String JSON_DATA= "{\n" +
            "    \"version\": 1,\n" +
            "    \"Around\": [\n" +
            "        {\n" +
            "            \"process\": \":channel\",\n" +
            "            \"aroundType\": \"Field_Set\",\n" +
            "            \"rollback\": \"true\",\n" +
            "            \"aroundValue\": {\n" +
            "                \"joinPoint\": {\n" +
            "                    \"className\": \"com.taobao.recovery.testdata.PatchClazz\",\n" +
            "                    \"fullMethodDesc\": \"returnIntMethod.(Lcom/taobao/recovery/testdata/Test;)I\"\n" +
            "                },\n" +
            "                \"joinValue\": {\n" +
            "                    \"className\": \"com.taobao.recovery.testdata.Test\",\n" +
            "                    \"fieldName\": \"observered\",\n" +
            "                    \"fieldType\": \"boolean\",\n" +
            "                    \"fieldValue\": \"true\",\n" +
            "                    \"times\": 1\n" +
            "                }\n" +
            "            }\n" +
            "        },\n" +
            "        {\n" +
            "            \"aroundType\": \"Method_Invoke\",\n" +
            "            \"aroundValue\": {\n" +
            "                \"joinPoint\": {\n" +
            "                    \"className\": \"com.taobao.recovery.testdata.PatchClazz\",\n" +
            "                    \"fullMethodDesc\": \"returnBooleanMethod.(Lcom/taobao/recovery/testdata/Test;)Z\"\n" +
            "                },\n" +
            "                \"joinValue\": {\n" +
            "                    \"className\": \"com.taobao.recovery.testdata.Test\",\n" +
            "                    \"fullMethodDesc\": \"add.(II)I\",\n" +
            "                    \"paramValues\": \"1,2\",\n" +
            "                    \"times\": 1\n" +
            "                }\n" +
            "            }\n" +
            "        },\n" +
            "        {\n" +
            "            \"aroundType\": \"Empty_Method_Body\",\n" +
            "            \"aroundValue\": {\n" +
            "                \"joinPoint\": {\n" +
            "                    \"className\": \"com.taobao.recovery.testdata.PatchClazz\",\n" +
            "                    \"fullMethodDesc\": \"emptyMethod.()V\"\n" +
            "                }\n" +
            "            }\n" +
            "        },\n" +
            "        {\n" +
            "            \"aroundType\": \"Method_Return_Value\",\n" +
            "            \"aroundValue\": {\n" +
            "                \"joinPoint\": {\n" +
            "                    \"className\": \"com.taobao.recovery.testdata.PatchClazz\",\n" +
            "                    \"fullMethodDesc\": \"returnBooleanMethod.()Z\"\n" +
            "                },\n" +
            "                \"joinValue\": {\n" +
            "                    \"returnType\": \"boolean\",\n" +
            "                    \"returnValue\": \"false\",\n" +
            "                    \n" +
            "                }\n" +
            "            }\n" +
            "        },\n" +
            "        {\n" +
            "            \"aroundType\": \"Try_Catch\",\n" +
            "            \"aroundValue\": {\n" +
            "                \"joinPoint\": {\n" +
            "                    \"className\": \"com.taobao.recovery.testdata.PatchClazz\",\n" +
            "                    \"fullMethodDesc\": \"exceptionMethod.()V\"\n" +
            "                }\n" +
            "            }\n" +
            "        }\n" +
            "    ]\n" +
            "}";

    @Test
    public void test() throws IOException, CompilationFailedException {
//        File zipFile = new File("/Users/lilong/Downloads/A");
//        ApContext apContext = new ApContext();
//        apContext.setApExploredFolder(zipFile);
//        File out = apContext.getCompileDir();
//        System.err.println("xx");
        JSONObject json = JSON.parseObject(JSON_DATA);

//        BetterZip.addFileAndDir(zipFile, "lib", folder);
    }
}
