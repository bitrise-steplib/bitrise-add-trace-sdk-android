package io.bitrise.trace.step;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.tasks.TaskAction;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

/**
 * Task will inject the required gradle file changes to add Trace to the given Android application. For the versions
 * see 'traceSdk.gradle' and {@link #TRACE_GRADLE_PLUGIN_VERSION}.
 */
public class InjectTraceTask extends DefaultTask {

    static Logger logger;

    @Inject
    public InjectTraceTask() {
        super();
        logger = getProject().getLogger();
    }

    //region Constants
    /**
     * The name of the  dependency for the 'trace-sdk'.
     */
    private static final String TRACE_SDK_DEPENDENCY_NAME = "trace-sdk";

    /**
     * The group of the  dependency for {@link #TRACE_SDK_DEPENDENCY_NAME}.
     */
    private static final String TRACE_SDK_DEPENDENCY_GROUP_NAME = "io.bitrise.trace";

    /**
     * The name of the Gradle file that contains the dependency for the {@link #TRACE_SDK_DEPENDENCY_NAME} project.
     */
    private static final String TRACE_SDK_GRADLE_FILE_NAME = "traceSdk.gradle";

    /**
     * The name of the  dependency for the 'trace-gradle-plugin'.
     */
    private static final String TRACE_GRADLE_PLUGIN_DEPENDENCY_NAME = "trace-gradle-plugin";

    /**
     * The group of the  dependency for {@link #TRACE_GRADLE_PLUGIN_DEPENDENCY_NAME}.
     */
    private static final String TRACE_GRADLE_PLUGIN_DEPENDENCY_GROUP_NAME = "io.bitrise.trace.plugin";

    /**
     * The name of the Gradle file that contains the dependency for the {@link #TRACE_GRADLE_PLUGIN_DEPENDENCY_NAME}
     * project.
     */
    private static final String TRACE_GRADLE_PLUGIN_GRADLE_FILE_NAME = "tracePlugin.gradle";

    /**
     * The version of {@link #TRACE_GRADLE_PLUGIN_DEPENDENCY_NAME}, which will be injected project. Note: for the
     * version of {@link #TRACE_SDK_DEPENDENCY_NAME} check traceSdk.gradle.
     */
    private static final String TRACE_GRADLE_PLUGIN_VERSION = "0.0.3";

    /**
     * Environment variable name for the source code of the step.
     */
    private static final String BITRISE_STEP_SRC_ENV = "BITRISE_STEP_SOURCE_DIR";
    //endregion

    //region Task action

    /**
     * The action that will be performed when this task is run. Does the following:
     * <ul>
     *     <li>ensures {@link #TRACE_SDK_DEPENDENCY_NAME} is a dependency to the app module</li>
     *     <li>ensures {@link #TRACE_GRADLE_PLUGIN_DEPENDENCY_NAME} is a buildscript dependency for the app module</li>
     *     <li>ensures that {@link #TRACE_GRADLE_PLUGIN_DEPENDENCY_NAME} is applied as a plugin on the app</li>
     * </ul>
     *
     * @throws IOException when any I/O error occurs with the file on the path.
     */
    @TaskAction
    public void taskAction() throws IOException {
        final Project rootProject = getProject();
        final Project applicationModule = getApplicationModule(rootProject.getSubprojects());

        ensureTraceSdkDependency(applicationModule);
        ensureTraceGradlePluginDependency(applicationModule);
        ensureTraceGradlePluginIsApplied(applicationModule);
    }

    /**
     * Gets the application module. Throws IllegalStateException when there is no such.
     *
     * @param projectSet the Set of the {@link Project}s which should contain the application.
     * @return the Project that is the application.
     */
    private Project getApplicationModule(final Set<Project> projectSet) {
        for (final Project project : projectSet) {
            final String projectName = project.getName();
            logger.debug("Checking project \"{}\" if it is an Android application", projectName);
            if (project.getPlugins().hasPlugin("com.android.application")) {
                logger.info("Project \"{}\" is an Android application! Task will ensure it has all the required Trace" +
                        " dependencies", projectName);
                return project;
            }
            logger.debug("Project \"{}\" is not an Android application!", projectName);
        }
        throw new IllegalStateException("No module with \"com.android.application\" plugin found. You must have at " +
                "least one Android application module in your project to install Trace SDK!");
    }
    //endregion

    //region Ensure dependency for 'trace-sdk'

    /**
     * Ensures that the given module has dependency on {@link #TRACE_SDK_DEPENDENCY_NAME}.
     *
     * @param appModule the {@link Project} of the app.
     * @throws IOException when any I/O error occurs with the file on the path.
     */
    private void ensureTraceSdkDependency(final Project appModule) throws IOException {
        if (hasTraceSdkDependency(appModule)) {
            logger.info("Skipping injecting the dependency. Please make sure that in your build.gradle files the " +
                    "dependency is defined for all the required configurations! For more information please " +
                    "check the README.md of \"trace-android-sdk\" " +
                    "(https://github.com/bitrise-io/trace-android-sdk/blob/main/README.md)");
        } else {
            logger.info("Adding dependency on  \"{}\" for project \"{}\".", TRACE_SDK_DEPENDENCY_NAME,
                    appModule.getName());
            addTraceSdkDependency(appModule);
        }
    }

    /**
     * Checks if the given {@link Project} has dependency on {@link #TRACE_SDK_DEPENDENCY_NAME} or not. If any
     * {@link org.gradle.api.artifacts.Configuration} that should have it as a dependency do have it, {@code true} is
     * returned.
     *
     * @param appModule the given Project.
     * @return {@code true} if it has, {@code false} otherwise.
     */
    private boolean hasTraceSdkDependency(final Project appModule) {
        for (final Configuration configuration : appModule.getConfigurations()) {
            logger.debug("Checking configuration \"{}\" for dependency on \"{}\".", configuration.getName(),
                    TRACE_SDK_DEPENDENCY_NAME);
            final String configurationNameLc = configuration.getName().toLowerCase();
            if (configurationNameLc.contains("compileclasspath") || configurationNameLc.contains("runtimeclasspath")) {
                if (hasDependency(configuration, TRACE_SDK_DEPENDENCY_NAME, TRACE_SDK_DEPENDENCY_GROUP_NAME)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Injects the code for adding {@link #TRACE_SDK_DEPENDENCY_NAME} as a dependency to the given Android
     * application and copies {@link #TRACE_SDK_GRADLE_FILE_NAME} to the project.
     *
     * @param appModule the {@link Project} of the Android app.
     * @throws IOException when any I/O error occurs with the file on the path.
     */
    private void addTraceSdkDependency(final Project appModule) throws IOException {
        copyGradleFile(appModule.getProjectDir().getPath(), TRACE_SDK_GRADLE_FILE_NAME);
        appendTraceDependency(appModule.getBuildFile().getPath(), TRACE_SDK_GRADLE_FILE_NAME);
    }
    //endregion

    //region Ensure dependency for 'trace-gradle-plugin'

    /**
     * Ensures that the given module has dependency on {@link #TRACE_GRADLE_PLUGIN_DEPENDENCY_NAME}.
     *
     * @param appModule the {@link Project} of the app.
     * @throws IOException when any I/O error occurs with the file on the path.
     */
    private void ensureTraceGradlePluginDependency(final Project appModule) throws IOException {
        if (hasTraceGradlePluginDependency(appModule)) {
            logger.info("Skipping injecting the dependency. Please make sure that in your build.gradle files the " +
                    "dependency is defined for all the required configurations! For more information please " +
                    "check the README.md of \"trace-android-sdk\"");
        } else {
            logger.info("Adding dependency on  \"{}\" for project \"{}\".", TRACE_GRADLE_PLUGIN_DEPENDENCY_NAME,
                    appModule.getName());
            addTraceGradlePluginDependency(appModule.getBuildFile().getPath());
        }
    }

    /**
     * Checks if the given {@link Project} has dependency on {@link #TRACE_GRADLE_PLUGIN_DEPENDENCY_NAME} or not. If any
     * {@link org.gradle.api.artifacts.Configuration} that should have it as a dependency do not have it, {@code
     * false} is returned.
     *
     * @param appModule the given Project.
     * @return {@code true} if it has, {@code false} otherwise.
     */
    private boolean hasTraceGradlePluginDependency(final Project appModule) {
        for (final Configuration configuration : appModule.getBuildscript().getConfigurations()) {
            if (hasDependency(configuration, TRACE_GRADLE_PLUGIN_DEPENDENCY_NAME,
                    TRACE_GRADLE_PLUGIN_DEPENDENCY_GROUP_NAME)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Injects the code for adding {@link #TRACE_GRADLE_PLUGIN_DEPENDENCY_NAME} as a plugin to the given Android
     * application.
     *
     * @param buildGradlePath the path of the build.gradle file in the app module.
     * @throws IOException when any I/O error occurs with the file on the path.
     */
    private void addTraceGradlePluginDependency(final String buildGradlePath) throws IOException {
        if (updateBuildScriptContent(buildGradlePath)) {
            logger.info("Updated buildscript block of \"{}\".", buildGradlePath);
        } else {
            logger.debug(" \"{}\" does not have buldscript block, adding it.", buildGradlePath);
            insertDependencyWithBuildScriptClosure(buildGradlePath);
        }
    }

    /**
     * When adding {@link #TRACE_GRADLE_PLUGIN_DEPENDENCY_NAME} to the given project and it does have a buildscript
     * block in it's build.gradle, this method updates the buildscript closure. Injects the dependency on the
     * {@link #TRACE_GRADLE_PLUGIN_DEPENDENCY_NAME} and adds JCenter as repository. If the buildscript closure is not
     * present does nothing and returns {@code false}.
     *
     * @param path the path of the file.
     * @return {@code true} if the buildscript block has been updated, {@code false otherwise}.
     * @throws IOException when any I/O error occurs with the file on the path.
     */
    private boolean updateBuildScriptContent(final String path) throws IOException {
        final String codeContent = getCodeContent(path);
        final String regex = "buildscript[ \\t\\n\\r]*\\{";
        final Pattern pattern = Pattern.compile(regex);
        final Matcher matcher = pattern.matcher(codeContent);
        if (matcher.find()) {
            final String updatedContent = matcher.replaceFirst(getUpdatedBuildScriptContent());
            try (final FileWriter fileWriter = new FileWriter(path, false)) {
                logger.debug("Updating \"{}\" with new content: \n\"{}\"", path, updatedContent);
                fileWriter.append(updatedContent);
            }
            return true;
        }
        return false;
    }

    /**
     * When adding {@link #TRACE_GRADLE_PLUGIN_DEPENDENCY_NAME} to the given project and it does not have a
     * buildscript block in it's build.gradle, this method updates the given build.gradle file, inserts the dependency
     * for {@link #TRACE_GRADLE_PLUGIN_DEPENDENCY_NAME} with a new buildscript closure.
     *
     * @param buildGradlePath the path of the build.gradle.
     * @throws IOException when any I/O error occurs with the file on the buildGradlePath.
     */
    private void insertDependencyWithBuildScriptClosure(final String buildGradlePath) throws IOException {
        final String buildscriptClosure = "\nbuildscript {\n" +
                "    %s\n" +
                "    repositories {\n" +
                "        jcenter()\n" +
                "        google()\n" +
                "    }\n" +
                "}";
        appendContentToFile(buildGradlePath, String.format(buildscriptClosure, getTraceGradlePluginDependency()));
    }

    /**
     * Gets the content for adding a buildscript dependency on {@link #TRACE_GRADLE_PLUGIN_DEPENDENCY_NAME}.
     *
     * @return the content that should be in the build.gradle.
     */
    private String getTraceGradlePluginDependency() {
        return String.format("\ndependencies.add(\"classpath\", \"io.bitrise.trace.plugin:trace-gradle-plugin:%s\")",
                TRACE_GRADLE_PLUGIN_VERSION);
    }

    /**
     * Gets the content for updating the buildscript with a new dependency on
     * {@link #TRACE_GRADLE_PLUGIN_DEPENDENCY_NAME}.
     */
    private String getUpdatedBuildScriptContent() {
        return String.format("buildscript {" +
                        "   %s" +
                        "   repositories {\n" +
                        "      jcenter()\n" +
                        "    }"
                , getTraceGradlePluginDependency());
    }

    //endregion

    //region Ensure to apply 'trace-gradle-plugin'

    /**
     * Ensures that the given module has applied {@link #TRACE_GRADLE_PLUGIN_DEPENDENCY_NAME} as a plugin.
     *
     * @param appModule the {@link Project} of the app.
     * @throws IOException when any I/O error occurs with the file on the path.
     */
    private void ensureTraceGradlePluginIsApplied(final Project appModule) throws IOException {
        if (isTraceGradlePluginApplied(appModule)) {
            logger.info("Project \"{}\" has already applied \"{}\" as a plugin, skipping injecting the plugin apply. " +
                            "For more information please check the README.md of \"trace-android-sdk\"",
                    appModule.getName(), TRACE_GRADLE_PLUGIN_DEPENDENCY_NAME);
        } else {
            injectTraceGradlePluginApply(appModule);
        }
    }

    /**
     * Checks if the given {@link Project} has the {@link #TRACE_GRADLE_PLUGIN_DEPENDENCY_NAME} applied as a plugin or
     * not.
     *
     * @param appModule the given Project.
     * @return {@code true} if it is, {@code false} otherwise.
     */
    private boolean isTraceGradlePluginApplied(final Project appModule) {
        return appModule.getPlugins().hasPlugin(TRACE_GRADLE_PLUGIN_DEPENDENCY_GROUP_NAME);
    }

    /**
     * Injects the code for adding {@link #TRACE_GRADLE_PLUGIN_DEPENDENCY_NAME} as a dependency to the given Android
     * application.
     *
     * @param appModule the {@link Project} of the Android app.
     * @throws IOException when any I/O error occurs with the files on the path.
     */
    private void injectTraceGradlePluginApply(final Project appModule) throws IOException {
        copyGradleFile(appModule.getProjectDir().getPath(), TRACE_GRADLE_PLUGIN_GRADLE_FILE_NAME);
        appendTraceDependency(appModule.getBuildFile().getPath(), TRACE_GRADLE_PLUGIN_GRADLE_FILE_NAME);
    }
    //endregion

    //region Common helper

    /**
     * Gets an environment variable. Throws IllegalStateException if the environment variable is not present.
     *
     * @param envName the name of the environment variable.
     * @return the value of the environment variable.
     */
    private static String getEnv(final String envName) {
        final String env = System.getenv(envName);
        if (env == null) {
            throw new IllegalStateException(
                    String.format("%s is not set as env variable, aborting build. Please set it " +
                            "as env variable before running this step", envName));
        }
        logger.debug("Environment variable \"{}\" is present with value \"{}\".", envName, env);
        return env;
    }

    /**
     * Copies the given Gradle file from the Bitrise step source directory to the given Android application.
     *
     * @param appModuleDir the path of the {@link Project} of the Android application.
     * @throws IOException when any I/O error occurs with the files on the path.
     */
    private static void copyGradleFile(final String appModuleDir, final String buildFileName) throws IOException {
        final Path traceSdkGradleFilePath = Paths.get(getEnv(BITRISE_STEP_SRC_ENV) + "/" + buildFileName);
        final Path destinationPath = Paths.get(appModuleDir + "/" + buildFileName);
        Files.copy(traceSdkGradleFilePath, destinationPath);
        logger.debug("Copied \"{}\" to \"{}\".", traceSdkGradleFilePath, destinationPath);
    }

    /**
     * Appends the given Gradle build file the apply of the given Gradle build file.
     *
     * @param appBuildGradlePath the path of the Gradle build file, which should be extended.
     * @param buildFileName      the given Gradle build file path to apply.
     * @throws IOException when any I/O error occurs with the file on the path.
     */
    private static void appendTraceDependency(final String appBuildGradlePath, final String buildFileName)
            throws IOException {
        appendContentToFile(appBuildGradlePath, getContentToAppend(appBuildGradlePath, buildFileName));
    }

    /**
     * Appends the given content to a given file.
     *
     * @param path    the path of the file.
     * @param content the content to append.
     * @throws IOException when any I/O error occurs with the file on the path.
     */
    private static void appendContentToFile(final String path, final String content) throws IOException {
        logger.debug("Appending to \"{}\" content:\n\"{}\"", path, content);
        Files.write(Paths.get(path), content.getBytes(), StandardOpenOption.APPEND);
    }

    /**
     * Gets the content to append for the given Gradle build file based on the extension (language) of the file. The
     * content is to apply the given Gradle build file, the name of this file is an argument.
     *
     * @param appBuildGradlePath the path of the Gradle build file, which should be extended.
     * @param buildFileName      the given Gradle build file path to apply.
     * @return the content to append to the Gradle build file.
     */
    static String getContentToAppend(final String appBuildGradlePath, final String buildFileName) {
        if (appBuildGradlePath.endsWith(".kts")) {
            logger.debug("\"{}\" is a Kotlin file.", appBuildGradlePath);
            return String.format("\napply(\"%s\")", buildFileName);
        } else if (appBuildGradlePath.endsWith(".gradle")) {
            logger.debug("\"{}\" is a Groovy file.", appBuildGradlePath);
            return String.format("\napply from: \"%s\"", buildFileName);
        } else {
            throw new IllegalStateException(String.format("Could not determine language for %s",
                    appBuildGradlePath));
        }
    }

    /**
     * Checks if the given {@link Configuration} has dependency on the given dependency or not.
     *
     * @param configuration       the given Configuration.
     * @param dependencyName      the name of the dependency to check for.
     * @param dependencyGroupName the group of the dependency to check for.
     * @return {@code true} if it has, {@code false} otherwise.
     */
    static boolean hasDependency(final Configuration configuration, final String dependencyName,
                                 final String dependencyGroupName) {
        for (final Dependency dependency : configuration.getAllDependencies()) {
            if (dependency.getName().equals(dependencyName) &&
                    dependency.getGroup() != null &&
                    dependency.getGroup().equals(dependencyGroupName)) {
                logger.info("Configuration \"{}\" already contains \"{}\" as dependency with version {}.",
                        configuration.getName(), dependencyName, dependency.getVersion());
                return true;
            }
        }
        logger.info("Configuration \"{}\" does not have a dependency on \"{}:{}\".", configuration.getName(),
                dependencyGroupName, dependencyName);
        return false;
    }

    /**
     * Removes all the commented code from a file and returns it as a String.
     *
     * @param path the path of the file to get the code from.
     * @return the String value of the code.
     * @throws IOException when any I/O error occurs with the file on the path.
     */
    private static String getCodeContent(final String path) throws IOException {
        final List<String> lines = Files.readAllLines(new File(path).toPath());
        return removeCommentedCode(lines);
    }

    /**
     * Removes all the commented code from a List of Strings.
     *
     * @param lines the List of lines
     * @return the String value of the code.
     */
    static String removeCommentedCode(final List<String> lines) {
        final String currentLineComment = "//";
        final String greedyCommentStart = "/*";
        final String greedyCommentEnd = "*/";
        final Pattern pattern = getGreedyCommentBlockPattern();

        final StringBuilder stringBuilder = new StringBuilder();
        boolean isGreedyCommented = false;
        for (final String line : lines) {
            logger.debug("Removing comments from line \"{}\"", line);
            String reducedLine = removeGreedyCommentBlocksFromLine(line, pattern);
            logger.debug("Removed complete greedy comments: \"{}\"", reducedLine);
            final int gceIndex = reducedLine.indexOf(greedyCommentEnd);
            if (gceIndex >= 0) {
                reducedLine = reducedLine.substring(reducedLine.indexOf(greedyCommentEnd) + greedyCommentEnd.length());
                isGreedyCommented = false;
                logger.debug("Removed greedy comment ends: \"{}\"", reducedLine);
            } else {
                if (isGreedyCommented) {
                    logger.debug("Skipping full line as it is greedy commented.");
                    continue;
                }
            }

            final int clcIndex = reducedLine.indexOf(currentLineComment);
            final int gcsIndex = reducedLine.indexOf(greedyCommentStart);
            final int csIndex = getSmallestNonNegativeNumber(clcIndex, gcsIndex);
            if (csIndex >= 0) {
                if (csIndex == gcsIndex) {
                    isGreedyCommented = true;
                }
                reducedLine = reducedLine.substring(0, csIndex);
                logger.debug("Removed greedy comment starts and line comments: \"{}\"", reducedLine);
            }

            stringBuilder.append(reducedLine).append("\n");
        }
        return stringBuilder.toString();
    }

    /**
     * Removes complete greedy comment blocks from a line.
     *
     * @param line    the given line.
     * @param pattern the Pattern to use for removing.
     * @return the line without greedy comment blocks.
     */
    static String removeGreedyCommentBlocksFromLine(final String line, final Pattern pattern) {
        final Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
            return matcher.replaceAll("");
        }
        return line;
    }

    /**
     * Gets the Pattern for getting greedy comment blocks.
     *
     * @return the compiled pattern.
     */
    static Pattern getGreedyCommentBlockPattern() {
        final String regex = "/\\*.*?\\*/";
        return Pattern.compile(regex);
    }

    /**
     * Gets the smallest non-negative number from the given numbers.
     *
     * @param numbers the numbers.
     * @return the smallest non-negative number, or -1 if there is no positive.
     */
    static int getSmallestNonNegativeNumber(final int... numbers) {
        return Arrays.stream(numbers).filter(i -> i >= 0).min().orElse(-1);
    }
    //endregion
}
