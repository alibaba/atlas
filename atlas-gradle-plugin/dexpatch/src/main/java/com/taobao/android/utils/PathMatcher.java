/*
 * Copyright 2014 Alibaba.com All right reserved. This software is the
 * confidential and proprietary information of Alibaba.com ("Confidential
 * Information"). You shall not disclose such Confidential Information and shall
 * use it only in accordance with the terms of the license agreement you entered
 * into with Alibaba.com.
 */

package com.taobao.android.utils;

import org.apache.commons.lang3.StringUtils;

/**
 * <p>
 * PathMatcher implementation for Ant-style path patterns. Examples are provided below.
 * </p>
 * <p>
 * Part of this mapping code has been kindly borrowed from <a href="http://ant.apache.org">Apache Ant</a>.
 * <p>
 * The mapping matches URLs using the following rules:<br>
 * <ul>
 * <li>? matches one character</li>
 * <li>* matches zero or more characters</li>
 * <li>** matches zero or more 'directories' in a path</li>
 * </ul>
 * <p>
 * Some examples:<br>
 * <ul>
 * <li><code>com/t?st.jsp</code> - matches <code>com/test.jsp</code> but also <code>com/tast.jsp</code> or
 * <code>com/txst.jsp</code></li>
 * <li><code>com/*.jsp</code> - matches all <code>.jsp</code> files in the <code>com</code> directory</li>
 * <li><code>com/&#42;&#42;/test.jsp</code> - matches all <code>test.jsp</code> files underneath the <code>com</code>
 * path</li>
 * <li><code>org/apache/shiro/&#42;&#42;/*.jsp</code> - matches all <code>.jsp</code> files underneath the
 * <code>org/apache/shiro</code> path</li>
 * <li><code>org/&#42;&#42;/servlet/bla.jsp</code> - matches <code>org/apache/shiro/servlet/bla.jsp</code> but also
 * <code>org/apache/shiro/testing/servlet/bla.jsp</code> and <code>org/servlet/bla.jsp</code></li>
 * </ul>
 * <p>
 * <b>N.B.</b>: This class was borrowed (with much appreciation) from the <a
 * href="http://www.springframework.org">Spring Framework</a> with modifications. We didn't want to reinvent the wheel
 * of great work they've done, but also didn't want to force every Shiro user to depend on Spring
 * </p>
 * <p>
 * As per the Apache 2.0 license, the original copyright notice and all author and copyright information have remained
 * in tact.
 * </p>
 *
 * @since 16.07.2003
 */
public class PathMatcher {
    /**
     * Default path separator: "/"
     */
    public static final String DEFAULT_PATH_SEPARATOR = "/";

    private String pathSeparator = DEFAULT_PATH_SEPARATOR;

    /**
     * Set the path separator to use for pattern parsing. Default is "/", as in
     * Ant.
     */
    public void setPathSeparator(String pathSeparator) {
        this.pathSeparator = (pathSeparator != null ? pathSeparator : DEFAULT_PATH_SEPARATOR);
    }

    public boolean isPattern(String str) {
        return (str.indexOf('*') != -1 || str.indexOf('?') != -1);
    }


    public boolean match(String[] patterns, String str) {
        if (null == patterns) {
            return false;
        }
        for (String pattern : patterns) {
            if (match(pattern, str)) {
                return true;
            }
        }
        return false;
    }

    public boolean match(String pattern, String str) {
        if (str == null) {
            return false;
        }
        if (pattern == null) {
            return false;
        }
        if (str.startsWith(this.pathSeparator) != pattern.startsWith(this.pathSeparator)) {
            return false;
        }

        String[] patDirs = StringUtils.split(pattern, this.pathSeparator);
        String[] strDirs = StringUtils.split(str, this.pathSeparator);

        int patIdxStart = 0;
        int patIdxEnd = patDirs.length - 1;
        int strIdxStart = 0;
        int strIdxEnd = strDirs.length - 1;

        // Match all elements up to the first **
        while (patIdxStart <= patIdxEnd && strIdxStart <= strIdxEnd) {
            String patDir = (String) patDirs[patIdxStart];
            if (patDir.equals("**")) {
                break;
            }
            if (!matchStrings(patDir, (String) strDirs[strIdxStart])) {
                return false;
            }
            patIdxStart++;
            strIdxStart++;
        }

        if (strIdxStart > strIdxEnd) {
            // String is exhausted, only match if rest of pattern is * or **'s
            if (patIdxStart == patIdxEnd && patDirs[patIdxStart].equals("*") && str.endsWith(this.pathSeparator)) {
                return true;
            }
            for (int i = patIdxStart; i <= patIdxEnd; i++) {
                if (!patDirs[i].equals("**")) {
                    return false;
                }
            }
            return true;
        } else {
            if (patIdxStart > patIdxEnd) {
                // String not exhausted, but pattern is. Failure.
                return false;
            }
        }

        // up to last '**'
        while (patIdxStart <= patIdxEnd && strIdxStart <= strIdxEnd) {
            String patDir = (String) patDirs[patIdxEnd];
            if (patDir.equals("**")) {
                break;
            }
            if (!matchStrings(patDir, (String) strDirs[strIdxEnd])) {
                return false;
            }
            patIdxEnd--;
            strIdxEnd--;
        }
        if (strIdxStart > strIdxEnd) {
            // String is exhausted
            for (int i = patIdxStart; i <= patIdxEnd; i++) {
                if (!patDirs[i].equals("**")) {
                    return false;
                }
            }
            return true;
        }

        while (patIdxStart != patIdxEnd && strIdxStart <= strIdxEnd) {
            int patIdxTmp = -1;
            for (int i = patIdxStart + 1; i <= patIdxEnd; i++) {
                if (patDirs[i].equals("**")) {
                    patIdxTmp = i;
                    break;
                }
            }
            if (patIdxTmp == patIdxStart + 1) {
                // '**/**' situation, so skip one
                patIdxStart++;
                continue;
            }
            // Find the pattern between padIdxStart & padIdxTmp in str between
            // strIdxStart & strIdxEnd
            int patLength = (patIdxTmp - patIdxStart - 1);
            int strLength = (strIdxEnd - strIdxStart + 1);
            int foundIdx = -1;
            strLoop:
            for (int i = 0; i <= strLength - patLength; i++) {
                for (int j = 0; j < patLength; j++) {
                    String subPat = (String) patDirs[patIdxStart + j + 1];
                    String subStr = (String) strDirs[strIdxStart + i + j];
                    if (!matchStrings(subPat, subStr)) {
                        continue strLoop;
                    }
                }

                foundIdx = strIdxStart + i;
                break;
            }

            if (foundIdx == -1) {
                return false;
            }

            patIdxStart = patIdxTmp;
            strIdxStart = foundIdx + patLength;
        }

        for (int i = patIdxStart; i <= patIdxEnd; i++) {
            if (!patDirs[i].equals("**")) {
                return false;
            }
        }

        return true;
    }

    /**
     * Tests whether or not a string matches against a pattern. The pattern may
     * contain two special characters:<br>
     * '*' means zero or more characters<br>
     * '?' means one and only one character
     *
     * @param pattern pattern to match against. Must not be <code>null</code>.
     * @param str     string which must be matched against the pattern. Must not be
     *                <code>null</code>.
     * @return <code>true</code> if the string matches against the pattern, or
     * <code>false</code> otherwise.
     */
    private boolean matchStrings(String pattern, String str) {
        char[] patArr = pattern.toCharArray();
        char[] strArr = str.toCharArray();
        int patIdxStart = 0;
        int patIdxEnd = patArr.length - 1;
        int strIdxStart = 0;
        int strIdxEnd = strArr.length - 1;
        char ch;

        boolean containsStar = false;
        for (int i = 0; i < patArr.length; i++) {
            if (patArr[i] == '*') {
                containsStar = true;
                break;
            }
        }

        if (!containsStar) {
            // No '*'s, so we make a shortcut
            if (patIdxEnd != strIdxEnd) {
                return false; // Pattern and string do not have the same size
            }
            for (int i = 0; i <= patIdxEnd; i++) {
                ch = patArr[i];
                if (ch != '?') {
                    if (ch != strArr[i]) {
                        return false;// Character mismatch
                    }
                }
            }
            return true; // String matches against pattern
        }

        if (patIdxEnd == 0) {
            return true; // Pattern contains only '*', which matches anything
        }

        // Process characters before first star
        while ((ch = patArr[patIdxStart]) != '*' && strIdxStart <= strIdxEnd) {
            if (ch != '?') {
                if (ch != strArr[strIdxStart]) {
                    return false;// Character mismatch
                }
            }
            patIdxStart++;
            strIdxStart++;
        }
        if (strIdxStart > strIdxEnd) {
            // All characters in the string are used. Check if only '*'s are
            // left in the pattern. If so, we succeeded. Otherwise failure.
            for (int i = patIdxStart; i <= patIdxEnd; i++) {
                if (patArr[i] != '*') {
                    return false;
                }
            }
            return true;
        }

        // Process characters after last star
        while ((ch = patArr[patIdxEnd]) != '*' && strIdxStart <= strIdxEnd) {
            if (ch != '?') {
                if (ch != strArr[strIdxEnd]) {
                    return false;// Character mismatch
                }
            }
            patIdxEnd--;
            strIdxEnd--;
        }
        if (strIdxStart > strIdxEnd) {
            // All characters in the string are used. Check if only '*'s are
            // left in the pattern. If so, we succeeded. Otherwise failure.
            for (int i = patIdxStart; i <= patIdxEnd; i++) {
                if (patArr[i] != '*') {
                    return false;
                }
            }
            return true;
        }

        // process pattern between stars. padIdxStart and patIdxEnd point
        // always to a '*'.
        while (patIdxStart != patIdxEnd && strIdxStart <= strIdxEnd) {
            int patIdxTmp = -1;
            for (int i = patIdxStart + 1; i <= patIdxEnd; i++) {
                if (patArr[i] == '*') {
                    patIdxTmp = i;
                    break;
                }
            }
            if (patIdxTmp == patIdxStart + 1) {
                // Two stars next to each other, skip the first one.
                patIdxStart++;
                continue;
            }
            // Find the pattern between padIdxStart & padIdxTmp in str between
            // strIdxStart & strIdxEnd
            int patLength = (patIdxTmp - patIdxStart - 1);
            int strLength = (strIdxEnd - strIdxStart + 1);
            int foundIdx = -1;
            strLoop:
            for (int i = 0; i <= strLength - patLength; i++) {
                for (int j = 0; j < patLength; j++) {
                    ch = patArr[patIdxStart + j + 1];
                    if (ch != '?') {
                        if (ch != strArr[strIdxStart + i + j]) {
                            continue strLoop;
                        }
                    }
                }

                foundIdx = strIdxStart + i;
                break;
            }

            if (foundIdx == -1) {
                return false;
            }

            patIdxStart = patIdxTmp;
            strIdxStart = foundIdx + patLength;
        }

        // All characters in the string are used. Check if only '*'s are left
        // in the pattern. If so, we succeeded. Otherwise failure.
        for (int i = patIdxStart; i <= patIdxEnd; i++) {
            if (patArr[i] != '*') {
                return false;
            }
        }

        return true;
    }

    /**
     * Given a pattern and a full path, returns the non-pattern mapped part.
     * E.g.:
     * <ul>
     * <li>'<code>/docs/*</code>' and '<code>/docs/cvs/commit</code> -> '
     * <code>cvs/commit</code>'</li>
     * <li>'<code>/docs/cvs/*.html</code>' and '
     * <code>/docs/cvs/commit.html</code> -> '<code>commit</code>'</li>
     * <li>'<code>/docs/**</code>' and '<code>/docs/cvs/commit</code> -> '
     * <code>cvs/commit</code>'</li>
     * <li>'<code>/docs/**\/*.html</code>' and '<code>/docs/cvs/commit</code> ->
     * '<code>cvs/commit</code>'</li>
     * </ul>
     * <p/>
     * Assumes that {@link #match} returns <code>true</code> for '
     * <code>pattern</code>' and '<code>path</code>', but does
     * <strong>not</strong> enforce this.
     */
    public String extractPathWithinPattern(String pattern, String path) {
        String[] patternParts = StringUtils.split(pattern, this.pathSeparator);
        String[] pathParts = StringUtils.split(path, this.pathSeparator);

        StringBuffer buffer = new StringBuffer();

        // Add any path parts that have a wildcarded pattern part.
        int puts = 0;
        for (int i = 0; i < patternParts.length; i++) {
            String patternPart = patternParts[i];
            if (patternPart.indexOf('*') > -1 || patternPart.indexOf('?') > -1) {
                if (puts != 0) {
                    buffer.append(this.pathSeparator);
                }
                if (pathParts.length >= i + 1) {
                    buffer.append(pathParts[i]);
                    puts++;
                }
            }
        }

        // Append any trailing path parts.
        for (int i = patternParts.length; i < pathParts.length; i++) {
            if (puts > 0 || i > 0) {
                buffer.append(this.pathSeparator);
            }
            buffer.append(pathParts[i]);
        }

        return buffer.toString();
    }
}
