import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Set;

public class InjectTraceTask extends DefaultTask {

    private static final String TRACE_SDK_GRADLE_FILE_NAME = "traceSdk.gradle";

    private static final String BITRISE_STEP_SRC_ENV = "BITRISE_STEP_SOURCE_DIR";

    @TaskAction
    public void taskAction() {
        final Project applicationModule = getApplicationModule(getProject().getSubprojects());
        injectTraceSdkDependency(applicationModule);
    }

    private Project getApplicationModule(final Set<Project> projectSet) {
        for (final Project project : projectSet) {
            if (project.getPlugins().hasPlugin("com.android.application")) {
                return project;
            }
        }
        throw new IllegalStateException("No module with com.android.application plugin found. You must have at least " +
                "one Android application module to install Trace SDK!");
    }

    private void injectTraceSdkDependency(final Project applicationModule) {
        copyTraceSdkGradleFile(applicationModule.getProjectDir().getPath());
        appendTraceSdkDependency(applicationModule.getBuildFile().getPath());
    }

    private void copyTraceSdkGradleFile(final String projectDir) {
        final Path traceSdkGradleFilePath = Paths.get(getEnv(BITRISE_STEP_SRC_ENV) + "/" + TRACE_SDK_GRADLE_FILE_NAME);
        final Path destinationPath = Paths.get(projectDir + "/" + TRACE_SDK_GRADLE_FILE_NAME);
        try {
            Files.copy(traceSdkGradleFilePath, destinationPath);
        } catch (final IOException e) {
            throw new IllegalStateException(String.format("Could not copy %1$s to %2$s. Reason: %3$s",
                    TRACE_SDK_GRADLE_FILE_NAME, destinationPath, e.getLocalizedMessage()));
        }
    }

    private void appendTraceSdkDependency(final String applicationBuildGradlePath) {
        try {
            Files.write(Paths.get(applicationBuildGradlePath),
                    getContentToAppend(applicationBuildGradlePath).getBytes(),
                    StandardOpenOption.APPEND);
        } catch (final IOException e) {
            throw new IllegalStateException(
                    String.format("Failed to append to %s. Reason: %s", applicationBuildGradlePath,
                            e.getLocalizedMessage()));
        }
    }

    private String getContentToAppend(final String applicationBuildGradlePath) {
        if (applicationBuildGradlePath.endsWith(".kts")) {
            return String.format("\napply(\"%s\")", TRACE_SDK_GRADLE_FILE_NAME);
        } else if (applicationBuildGradlePath.endsWith(".gradle")) {
            return String.format("\napply from: \"%s\"", TRACE_SDK_GRADLE_FILE_NAME);
        } else {
            throw new IllegalStateException(String.format("Could not determine language for %s",
                    applicationBuildGradlePath));
        }
    }

    private String getEnv(final String envName) {
        final String env = System.getenv(envName);
        if (env == null) {
            throw new IllegalStateException(String.format("%s is not set as env variable, aborting build. Please set it " +
                    "as env variable before running this step", envName));
        }
        return env;
    }
}
