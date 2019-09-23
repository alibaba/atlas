package com.android.build.gradle.internal.dependency

import com.android.builder.model.Version
import com.android.tools.build.jetifier.core.config.ConfigParser
import com.android.tools.build.jetifier.processor.FileMapping
import com.android.tools.build.jetifier.processor.Processor
import com.google.common.base.Preconditions
import com.google.common.base.Splitter
import com.google.common.base.Verify
import org.gradle.api.artifacts.transform.ArtifactTransform
import java.io.File
import javax.inject.Inject

/**
 * @ClassName FeatureJetifyTransform
 * @Description TODO
 * @Author zhayu.ll
 * @Date 2019-09-18 09:33
 * @Version 1.0
 */

class FeatureJetifyTransform @Inject constructor(blackListOption: String) : ArtifactTransform() {

    companion object {

        /**
         * The Jetifier processor.
         */
        private val jetifierProcessor: Processor by lazy {
            Processor.createProcessor3(
                    config = ConfigParser.loadDefaultConfig()!!,
                    dataBindingVersion = Version.ANDROID_GRADLE_PLUGIN_VERSION,
                    allowAmbiguousPackages = false,
                    stripSignatures = true
            )
        }
    }

    /**
     * List of regular expressions for libraries that should not be jetified.
     *
     * If a library's absolute path contains a substring that matches one of the regular
     * expressions, the library won't be jetified.
     *
     * For example, if the regular expression is  "doNot.*\.jar", then "/path/to/doNotJetify.jar"
     * won't be jetified.
     */
    private val jetifierBlackList: List<Regex> = getJetifierBlackList(blackListOption)

    /**
     * Computes the Jetifier blacklist of type [Regex] from a string containing a comma-separated
     * list of regular expressions. The string may be empty.
     */
    private fun getJetifierBlackList(blackListOption: String): List<Regex> {
        val blackList = mutableListOf<String>()
        if (!blackListOption.isEmpty()) {
            blackList.addAll(Splitter.on(",").trimResults().splitToList(blackListOption))
        }

        // Jetifier should not jetify itself (http://issuetracker.google.com/119135578)
        blackList.add("jetifier-.*\\.jar")

        return blackList.map { Regex(it) }
    }

    override fun transform(aarOrJarFile: File): List<File> {
        Preconditions.checkArgument(
                aarOrJarFile.name.toLowerCase().endsWith(".aar")
                        || aarOrJarFile.name.toLowerCase().endsWith(".jar")
                        || aarOrJarFile.name.toLowerCase().endsWith(".awb")
        )

        /*
         * The aars or jars can be categorized into 4 types:
         *  - AndroidX libraries
         *  - Old support libraries
         *  - Other libraries that are blacklisted
         *  - Other libraries that are not blacklisted
         * In the following, we handle these cases accordingly.
         */
        // Case 1: If this is an AndroidX library, no need to jetify it
        if (jetifierProcessor.isNewDependencyFile(aarOrJarFile)) {
            return listOf(aarOrJarFile)
        }

        // Case 2: If this is an old support library, it means that it was not replaced during
        // dependency substitution earlier, either because it does not yet have an AndroidX version,
        // or because its AndroidX version is not yet available on remote repositories. Again, no
        // need to jetify it.
        if (jetifierProcessor.isOldDependencyFile(aarOrJarFile)) {
            return listOf(aarOrJarFile)
        }

        // Case 3: If the library is blacklisted, do not jetify it
        if (jetifierBlackList.any { it.containsMatchIn(aarOrJarFile.absolutePath) }) {
            return listOf(aarOrJarFile)
        }

        // Case 4: For the remaining libraries, let's jetify them
        val outputFile = File(outputDirectory, "jetified-" + aarOrJarFile.name)
        val maybeTransformedFile = try {
            jetifierProcessor.transform(
                    setOf(FileMapping(aarOrJarFile, outputFile)), false
            )
                    .single()
        } catch (exception: Exception) {
            throw RuntimeException(
                    "Failed to transform '$aarOrJarFile' using Jetifier." +
                            " Reason: ${exception.message}. (Run with --stacktrace for more details.)",
                    exception
            )
        }

        // If the aar/jar was transformed, the returned file would be the output file. Otherwise, it
        // would be the original file.
        Preconditions.checkState(
                maybeTransformedFile == aarOrJarFile || maybeTransformedFile == outputFile
        )

        // If the file wasn't transformed, returning the original file here also tells Gradle that
        // the file wasn't transformed. In either case (whether the file was transformed or not), we
        // can just return to Gradle the file that was returned from Jetifier.
        Verify.verify(maybeTransformedFile.exists(), "$outputFile does not exist")
        return listOf(maybeTransformedFile)
    }
}