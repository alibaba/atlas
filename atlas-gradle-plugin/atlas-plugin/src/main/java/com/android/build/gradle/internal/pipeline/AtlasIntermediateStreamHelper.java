package com.android.build.gradle.internal.pipeline;

import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.taobao.android.builder.tools.ReflectUtils;
import org.gradle.api.file.FileCollection;

import java.lang.reflect.Field;
import java.util.Set;

/**
 * @author lilong
 * @create 2017-12-20 下午1:08
 */
public class AtlasIntermediateStreamHelper{


    private IntermediateStream intermediateStream;

    public AtlasIntermediateStreamHelper(TransformTask transformTask) {

        this.intermediateStream = (IntermediateStream) ReflectUtils.getField(transformTask,"outputStream");
    }

    public void replaceProvider(TransformInvocation transformInvocation){
        try {
            Field field = intermediateStream.getClass().getDeclaredField("folderUtils");
            field.setAccessible(true);
            IntermediateFolderUtils intermediateFolderUtils = (IntermediateFolderUtils) field.get(intermediateStream);
            Set<QualifiedContent.ContentType> types = (Set<QualifiedContent.ContentType>) ReflectUtils.getField(intermediateFolderUtils,"types");
            Set<? super QualifiedContent.Scope> scopes = (Set<? super QualifiedContent.Scope>) ReflectUtils.getField(intermediateFolderUtils,"scopes");
            AtlasIntermediateFolderUtils atlasIntermediateFolderUtils = new AtlasIntermediateFolderUtils(intermediateFolderUtils.getRootFolder(),types,scopes);
            field.set(intermediateStream,atlasIntermediateFolderUtils);
            TransformOutputProvider transformOutputProvider = transformInvocation.getOutputProvider();
            ReflectUtils.updateField(transformOutputProvider,"folderUtils",atlasIntermediateFolderUtils);

        }catch (Exception e){

        }


    }


}
