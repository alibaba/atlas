package com.taobao.android.builder.dependency.ap;

import com.alibaba.fastjson.JSON;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.taobao.android.builder.dependency.output.DependencyJson;
import com.taobao.android.builder.dependency.parser.ResolvedDependencyInfo;
import com.taobao.android.builder.extension.TBuildType;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Nullable;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.dsl.ParsedModuleStringNotation;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.android.build.gradle.internal.api.ApContext.DEPENDENCIES_FILENAME;
import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * @author lilong
 * @create 2017-12-03 下午1:49
 */

public class ApDependency {

    private final DependencyHandler dependencies;


    private final Map<ModuleIdentifier, String> mFlatDependenciesMap = Maps.newHashMap();

    private final Map<ModuleIdentifier, String> mMainDependenciesMap = Maps.newHashMap();

    private final Map<ModuleIdentifier, Map<ModuleIdentifier, String>> mAwbDependenciesMap = Maps.newHashMap();

    private final DependencyJson apDependencyJson;

    public ApDependency(Project project, TBuildType tBuildType) {
        this.dependencies = project.getDependencies();
        File apBaseFile;
        apBaseFile = getBaseApFile(project, tBuildType);

        try (ZipFile zip = new ZipFile(apBaseFile)) {
            ZipEntry entry = zip.getEntry(DEPENDENCIES_FILENAME);
            try (InputStream in = zip.getInputStream(entry)) {
                apDependencyJson = JSON.parseObject(IOUtils.toString(in, StandardCharsets.UTF_8), DependencyJson.class);
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to read dependencies.txt from " + apBaseFile.getAbsolutePath(), e);
        }

        for (String mainDex : apDependencyJson.getMainDex()) {
            addDependency(mainDex, mMainDependenciesMap);
        }
        for (Map.Entry<String, ArrayList<String>> entry : apDependencyJson.getAwbs().entrySet()) {
            String awb = entry.getKey();
            Map<ModuleIdentifier, String> awbDependencies = getAwbDependencies(awb);
            addDependency(awb, awbDependencies);
            ArrayList<String> dependenciesString = entry.getValue();
            for (String dependencyString : dependenciesString) {
                addDependency(dependencyString, awbDependencies);
            }
        }
    }

    private Map<ModuleIdentifier, String> getAwbDependencies(String awb) {
        ParsedModuleStringNotation parsedNotation = new ParsedModuleStringNotation(awb,"awb");
        String group = parsedNotation.getGroup();
        String name = parsedNotation.getName();
        ModuleIdentifier moduleIdentifier = DefaultModuleIdentifier.newId(group, name);
        Map<ModuleIdentifier, String> awbDependencies = mAwbDependenciesMap.get(moduleIdentifier);
        if (awbDependencies == null) {
            awbDependencies = Maps.newHashMap();
            mAwbDependenciesMap.put(moduleIdentifier, awbDependencies);
        }
        return awbDependencies;
    }

    public Map<ModuleIdentifier, String> getAwbDependencies(String group, String name) {
        ModuleIdentifier moduleIdentifier = DefaultModuleIdentifier.newId(group, name);
        Map<ModuleIdentifier, String> awbDependencies = mAwbDependenciesMap.get(moduleIdentifier);
        return awbDependencies;
    }

    private File getBaseApFile(Project project, TBuildType tBuildType) {
        File apBaseFile;
        File buildTypeBaseApFile = tBuildType.getBaseApFile();
        if (null != buildTypeBaseApFile && buildTypeBaseApFile.exists()) {
            apBaseFile = buildTypeBaseApFile;
        } else if (!isNullOrEmpty(tBuildType.getBaseApDependency())) {
            String apDependency = tBuildType.getBaseApDependency();
            // Preconditions.checkNotNull(apDependency,
            //                            "You have to specify the baseApFile property or the baseApDependency
            // dependency");
            Dependency dependency = project.getDependencies().create(apDependency);
            Configuration configuration = project.getConfigurations().detachedConfiguration(dependency);
            configuration.setTransitive(false);
            apBaseFile = Iterables.getOnlyElement(Collections2.filter(configuration.getFiles(), new Predicate<File>() {
                @Override
                public boolean apply(@Nullable File file) {
                    return file.getName().endsWith(".ap");
                }
            }));
        } else {
            throw new IllegalStateException("AP is missing");
        }
        return apBaseFile;
    }

    public boolean isMainLibrary(ResolvedDependencyInfo dependencyInfo) {
        return mMainDependenciesMap.containsKey(
                DefaultModuleIdentifier.newId(dependencyInfo.getGroup(), dependencyInfo.getName()));
    }

    // ----- PRIVATE TASK API -----

    private void addDependency(String dependencyString, Map<ModuleIdentifier, String> awb) {
        ParsedModuleStringNotation parsedNotation = new ParsedModuleStringNotation(dependencyString,dependencyString.split("@")[1]);
        ModuleIdentifier moduleIdentifier = DefaultModuleIdentifier.newId(parsedNotation.getGroup(),
                parsedNotation.getName());
        String version = parsedNotation.getVersion();
        awb.put(moduleIdentifier, version);
        mFlatDependenciesMap.put(moduleIdentifier, version);
    }
}
