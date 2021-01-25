// TODO move to proper package io.bitrise.trace.step

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.tasks.TaskAction;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Set;

/**
 * Task will inject the required gradle file changes to add Trace to the given Android application.
 */
public class InjectTraceTask extends DefaultTask {

    private final Logger logger = getProject().getLogger();

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
     * Environment variable name for the source code of the step.
     */
    private static final String BITRISE_STEP_SRC_ENV = "BITRISE_STEP_SOURCE_DIR";
    //endregion

    //region Task action

    /**
     * The action that will be performed when this task is run. Does the following:
     * <ul>
     *     <li>ensures {@link #TRACE_SDK_DEPENDENCY_NAME} is a dependency to the app module</li>
     *     <li>ensures {@link #TRACE_GRADLE_PLUGIN_DEPENDENCY_NAME} is a buildscript dependency for the root
     *     project</li>
     *     <li>ensures that {@link #TRACE_GRADLE_PLUGIN_DEPENDENCY_NAME} is applied as a plugin on the app</li>
     * </ul>
     *
     * @throws IOException
     */

    // TODO javadoc
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
            if (project.getPlugins().hasPlugin("com.android.application")) {
                return project;
            }
        }
        throw new IllegalStateException("No module with \"com.android.application\" plugin found. You must have at " +
                "least one Android application module to install Trace SDK!");
    }
    //endregion

    //region Ensure dependency for 'trace-sdk'

    /**
     * Ensures that the given module has dependency on {@link #TRACE_SDK_DEPENDENCY_NAME}.
     *
     * @param appModule the {@link Project} of the app.
     */
    private void ensureTraceSdkDependency(final Project appModule) {
        if (hasTraceSdkDependency(appModule)) {
            logger.info("Project \"{}\" already has dependency on \"{}\", skipping injecting the dependency. Please " +
                            "make sure that in your build.gradle files the dependency is defined for all the required" +
                            "configurations! For more information please check the README.md of \"trace-android-sdk\"",
                    appModule.getName(), TRACE_SDK_DEPENDENCY_NAME);
        } else {
            injectTraceSdkDependency(appModule);
        }
    }

    /**
     * Checks if the given {@link Project} has dependency on {@link #TRACE_SDK_DEPENDENCY_NAME} or not. If any
     * {@link org.gradle.api.artifacts.Configuration} that should have it as a dependency do not have it, {@code
     * false} is returned.
     *
     * @param appModule the given Project.
     * @return {@code true} if it has, {@code false} otherwise.
     */
    public boolean hasTraceSdkDependency(final Project appModule) {
        for (final Configuration configuration : appModule.getConfigurations()) {
            final String configurationNameLc = configuration.getName().toLowerCase();
            if (configurationNameLc.contains("compileclasspath") || configurationNameLc.contains("runtimeclasspath")) {
                final boolean hasSdk = hasDependency(configuration, TRACE_SDK_DEPENDENCY_NAME,
                        TRACE_SDK_DEPENDENCY_GROUP_NAME);
                if (hasSdk) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Injects the code for adding {@link #TRACE_SDK_DEPENDENCY_NAME} as a dependency to the given Android application.
     *
     * @param appModule the {@link Project} of the Android app.
     */
    private void injectTraceSdkDependency(final Project appModule) {
        copyGradleFile(appModule.getProjectDir().getPath(), TRACE_SDK_GRADLE_FILE_NAME);
        appendTraceDependency(appModule.getBuildFile().getPath(), TRACE_SDK_GRADLE_FILE_NAME);
    }
    //endregion

    //region Ensure dependency for 'trace-gradle-plugin'

    /**
     * Ensures that the given module has dependency on {@link #TRACE_GRADLE_PLUGIN_DEPENDENCY_NAME}.
     *
     * @param appModule the {@link Project} of the app.
     * @throws IOException
     */
    //TODO javadoc
    private void ensureTraceGradlePluginDependency(final Project appModule) throws IOException {
        if (hasTraceGradlePluginDependency(appModule)) {
            logger.info("Project \"{}\" already has dependency on \"{}\", skipping injecting the dependency. Please " +
                            "make sure that in your build.gradle files the dependency is defined for all the required" +
                            "configurations! For more information please check the README.md of \"trace-android-sdk\"",
                    appModule.getName(), TRACE_GRADLE_PLUGIN_DEPENDENCY_NAME);
        } else {
            injectTraceGradlePluginDependency(appModule);
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
    public boolean hasTraceGradlePluginDependency(final Project appModule) {
        for (final Configuration configuration : appModule.getBuildscript().getConfigurations()) {
            final boolean hasPlugin = hasDependency(configuration, TRACE_GRADLE_PLUGIN_DEPENDENCY_NAME,
                    TRACE_GRADLE_PLUGIN_DEPENDENCY_GROUP_NAME);
            if (hasPlugin) {
                return true;
            }
        }
        return false;
    }

    /**
     * Injects the code for adding {@link #TRACE_GRADLE_PLUGIN_DEPENDENCY_NAME} as a plugin to the given Android
     * application.
     *
     * @param appModule the {@link Project} of the Android app.
     */
    private void injectTraceGradlePluginDependency(final Project appModule) throws IOException {
        final String buildGradlePath = appModule.getBuildFile().getPath();
        final long[] buildScriptClosurePosition = findBuildScriptPosition(buildGradlePath);
        if ((buildScriptClosurePosition)[0] >= 0) {
            insertDependencyToBuildScriptClosure(buildGradlePath, buildScriptClosurePosition[0],
                    buildScriptClosurePosition[1]);
        } else {
            insertDependencyWithBuildScriptClosure(appModule);
        }
    }

    /**
     * Updates the given build.gradle file, inserts the dependency for {@link #TRACE_GRADLE_PLUGIN_DEPENDENCY_NAME}
     * to the buildscript closure.
     *
     * @param path       the path of the build.gradle.
     * @param lineNumber the line number of the buildscript closure.
     * @param charIndex  the char index of the buildscript closure, just right after the open of the closure.
     * @throws IOException when the build.gradle file cannot be modified.
     */
    private void insertDependencyToBuildScriptClosure(final String path, final long lineNumber, final long charIndex)
            throws IOException {
        // todo make sure there is repo jcenter or mavencentral
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(path, "rw")) {
            String line = randomAccessFile.readLine();
            int actualLineNumber = 0;
            while (line != null) {
                if (actualLineNumber == lineNumber) {
                    line = insertToStringAt(line, getTraceGradlePluginDependency(), charIndex);
                }
                randomAccessFile.writeBytes(line);
                actualLineNumber++;
                line = randomAccessFile.readLine();
            }
        }
    }

    private String getTraceGradlePluginDependency() {
        // TODO dependencies.add("classpath", "io.bitrise.trace.plugin:trace-gradle-plugin:0.0.2")
        return "";
    }

    /**
     * Inserts a String to the given char position.
     *
     * @param original  the original String to be updated.
     * @param toInsert  the String to insert.
     * @param charIndex the index of the char where it will be inserted
     * @return the updated String.
     */
    private String insertToStringAt(final String original, final String toInsert, final long charIndex) {
        final StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < original.length(); i++) {
            stringBuilder.append(original.charAt(i));

            if (i == charIndex) {
                stringBuilder.append(toInsert);
            }
        }
        return stringBuilder.toString();
    }

    private void insertDependencyWithBuildScriptClosure(final Project appModule) {
        //TODO add it to the end of the given gradle file
    }

    private long[] findBuildScriptPosition(final String path) {
        // TODO
        try (final FileInputStream fileInputStream = new FileInputStream(path);) {

            final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fileInputStream));
            String line;
            int actualLineNumber = 0;
            while ((line = bufferedReader.readLine()) != null) {

                actualLineNumber++;

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new long[]{0, 0};
    }

    //endregion

    //region Ensure to apply 'trace-gradle-plugin'

    /**
     * Ensures that the given module has applied {@link #TRACE_GRADLE_PLUGIN_DEPENDENCY_NAME} as a plugin.
     *
     * @param appModule the {@link Project} of the app.
     */
    private void ensureTraceGradlePluginIsApplied(final Project appModule) {
        if (isTraceGradlePluginApplied(appModule)) {
            logger.info("Project \"{}\" has already applied \"{}\" as a plugin, skipping injecting the plugin apply. " +
                            "For more information please check the README.md of \"trace-android-sdk\"",
                    appModule.getName(), TRACE_GRADLE_PLUGIN_DEPENDENCY_NAME);
        } else {
            injectTraceGradlePluginApply(appModule);
        }
    }

    /**
     * Checks if the given {@link Project} has the  {@link #TRACE_GRADLE_PLUGIN_DEPENDENCY_NAME} applied as a plugin or
     * not.
     *
     * @param appModule the given Project.
     * @return {@code true} if it is, {@code false} otherwise.
     */
    public boolean isTraceGradlePluginApplied(final Project appModule) {
        return appModule.getPlugins().hasPlugin(TRACE_GRADLE_PLUGIN_DEPENDENCY_GROUP_NAME);
    }

    /**
     * Injects the code for adding {@link #TRACE_GRADLE_PLUGIN_DEPENDENCY_NAME} as a dependency to the given Android
     * application.
     *
     * @param appModule the {@link Project} of the Android app.
     */
    private void injectTraceGradlePluginApply(final Project appModule) {
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
    private String getEnv(final String envName) {
        final String env = System.getenv(envName);
        if (env == null) {
            throw new IllegalStateException(
                    String.format("%s is not set as env variable, aborting build. Please set it " +
                            "as env variable before running this step", envName));
        }
        return env;
    }

    /**
     * Copies the given Gradle file from the step source to the given Android application.
     *
     * @param appModuleDir the path of the {@link Project} of the Android application.
     */
    private void copyGradleFile(final String appModuleDir, final String buildFileName) {
        final Path traceSdkGradleFilePath = Paths.get(getEnv(BITRISE_STEP_SRC_ENV) + "/" + buildFileName);
        final Path destinationPath = Paths.get(appModuleDir + "/" + buildFileName);
        try {
            Files.copy(traceSdkGradleFilePath, destinationPath);
        } catch (final IOException e) {
            throw new IllegalStateException(String.format("Could not copy %1$s to %2$s. Reason: %3$s",
                    buildFileName, destinationPath, e.getLocalizedMessage()));
        }
    }

    /**
     * Appends the given Gradle build file the apply of the given Gradle build file.
     *
     * @param appBuildGradlePath the path of the Gradle build file, which should be extended.
     * @param buildFileName      the given Gradle build file path to apply.
     */
    private void appendTraceDependency(final String appBuildGradlePath, final String buildFileName) {
        try {
            Files.write(Paths.get(appBuildGradlePath),
                    getContentToAppend(appBuildGradlePath, buildFileName).getBytes(),
                    StandardOpenOption.APPEND);
        } catch (final IOException e) {
            throw new IllegalStateException(String.format("Failed to append to %s. Reason: %s", appBuildGradlePath,
                    e.getLocalizedMessage()));
        }
    }

    /**
     * Gets the content to append for the given Gradle build file to apply the given Gradle build file.
     *
     * @param appBuildGradlePath the path of the Gradle build file, which should be extended.
     * @param buildFileName      the given Gradle build file path to apply.
     * @return the content to append to the Gradle build file.
     */
    private String getContentToAppend(final String appBuildGradlePath, final String buildFileName) {
        if (appBuildGradlePath.endsWith(".kts")) {
            return String.format("\napply(\"%s\")", buildFileName);
        } else if (appBuildGradlePath.endsWith(".gradle")) {
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
    public boolean hasDependency(final Configuration configuration, final String dependencyName,
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
        return false;
    }
    //endregion
}
