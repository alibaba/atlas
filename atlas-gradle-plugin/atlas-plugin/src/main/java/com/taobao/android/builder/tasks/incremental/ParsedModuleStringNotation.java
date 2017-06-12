package com.taobao.android.builder.tasks.incremental;

import org.gradle.api.IllegalDependencyNotation;

public class ParsedModuleStringNotation {
    private String group;

    private String name;

    private String version;

    private String classifier;

    private String artifactType;

    public ParsedModuleStringNotation(String moduleNotation) {
        assignValuesFromModuleNotation(moduleNotation);
    }

    private void assignValuesFromModuleNotation(String moduleNotation) {
        int count = 0;
        int idx = 0;
        int cur = -1;
        while (++cur < moduleNotation.length()) {
            if (':' == moduleNotation.charAt(cur)) {
                String fragment = moduleNotation.substring(idx, cur);
                assignValue(count, fragment);
                idx = cur + 1;
                count++;
            }
        }
        assignValue(count, moduleNotation.substring(idx, cur));
        count++;
        if (count < 2 || count > 5) {
            throw new IllegalDependencyNotation("Supplied String module notation '" + moduleNotation
                                                + "' is invalid. Example notations: 'org.gradle:gradle-core:2.2', "
                                                + "'org.mockito:mockito-core:1.9.5:javadoc'.");
        }
    }

    private void assignValue(int count, String fragment) {
        switch (count) {
            case 0:
                group = "".equals(fragment) ? null : fragment;
                break;
            case 1:
                name = fragment;
                break;
            case 2:
                artifactType = "".equals(fragment) ? null : fragment;
                break;
            case 3:
                version = "".equals(fragment) ? null : fragment;
                break;
            case 4:
                classifier = version;
                version = fragment;
        }
    }

    public String getGroup() {
        return group;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getClassifier() {
        return classifier;
    }
}
