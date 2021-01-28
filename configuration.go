package main

import (
	"fmt"
	"os"
	"path"
)

// Environment variables
const apmTokenEnvName = "APM_COLLECTOR_TOKEN"
const projectDirEnvName = "BITRISEIO_GIT_REPOSITORY_SLUG"
const stepSrcDirEnvName = "BITRISE_STEP_SOURCE_DIR"

// Config file values
// These configurations should be constant (final and static), code should access them accordingly. The reason for this
// is that this file should hold them, to make it easy to have a clear overview and make it the single source of truth.
const configFileName = "bitrise-addons-configuration.json"
const configFileVersion = "1.0.0"

// Injector Gradle task values
const injectTraceTaskName = "injectTraceTask"
const injectTraceTaskClassName = "io.bitrise.trace.step.InjectTraceTask"
const injectTraceTaskFileSrcPath = "src/main/java/io/bitrise/trace/step/InjectTraceTask.java"
const injectTraceTaskFileDstPath = "buildSrc/src/main/java/io/bitrise/trace/step/InjectTraceTask.java"

// Language dependent values
const kotlinBuildGradleSuffix = ".gradle.kts"
const groovyBuildGradleSuffix = ".gradle"

// Configs stores the step's inputs
type Configs struct {
	RootProjectPath string `env:"project_path"`
	GradleOptions   string `env:"gradle_options"`
}

// Returns the directory for the Android application.
func projectDir(src string) (string, error) {
	pDir, err := env(projectDirEnvName)
	if err != nil {
		return "", err
	}
	return path.Join(src, pDir), nil
}

// Gets an environment variable, throws error when it is not present.
func env(envName string) (string, error) {
	env := os.Getenv(envName)
	if env == "" {
		return "", fmt.Errorf("%s is not set as env variable, aborting build. Please set it as env variable before running this step", envName)

	}
	return env, nil
}
