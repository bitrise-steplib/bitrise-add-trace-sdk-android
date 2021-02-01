package main

import (
	"bytes"
	"fmt"
	"github.com/kballard/go-shellquote"
	"os"
	"os/exec"
	"path"
	"strings"

	"github.com/bitrise-io/go-steputils/stepconf"
	"github.com/bitrise-io/go-utils/log"
)

// Called when the main function should be terminated with failure.
func failf(format string, v ...interface{}) {
	log.Errorf(format, v...)
	os.Exit(1)
}

func main() {
	var configs Configs
	if err := stepconf.Parse(&configs); err != nil {
		failf("Issue with input: %s", err)
	}
	log.Infof("Creating the configuration file")

	if err := createConfigurationFile(configs.RootProjectPath); err != nil {
		failf("Could not create the config file, aborting build. Reason: %s\n", err)
	}
	log.Infof("Configuration file successfully created")

	log.Infof("Adding Trace injector to project")
	if err := addTraceInjectorTask(configs.RootProjectPath); err != nil {
		failf("Could not add Trace injector to project, aborting build. Reason: %s\n", err)
	}
	log.Infof("Added Trace injector to project")

	log.Infof("Running Trace injector on project")
	if err := runTraceInjector(configs.RootProjectPath, configs.GradleOptions); err != nil {
		failf("Error when injecting Trace to project, aborting build. Reason: %s\n", err)
	}
	log.Infof("Trace injector successfully injected the SDK")

	log.Infof("Verifying Trace on project")
	if err := runVerifyTraceTask(configs.RootProjectPath, configs.GradleOptions); err != nil {
		failf("Error when verifying Trace in project, aborting build. Please check the logs for details. Reason: %s\n", err)
	}
	log.Infof("Verification was successful")

	os.Exit(0)
}

// Creates the configuration file for the given Android project. The configuration file has the required properties for
// building the Android application. Requires the root directory of the project as an input.
func createConfigurationFile(rootDir string) error {
	c, err := getConfigFileContent()
	if err != nil {
		return err
	}

	fc, err := formatConfigFileContent(c)
	if err != nil {
		return err
	}

	src, err := projectDir(rootDir)
	if err != nil {
		return err
	}
	p := path.Join(src, configFileName)
	return createConfigFile(fc, p)
}

// Adds the InjectTraceTask to the given project. Requires the root directory of the project as an input.
func addTraceInjectorTask(rootDir string) error {
	projSrc, err := projectDir(rootDir)
	if err != nil {
		return err
	}

	gradlePath, err := findRootGradle(projSrc)
	if err != nil {
		return err
	}

	if err := appendTraceInjectorTaskToProject(gradlePath); err != nil {
		return fmt.Errorf("failed to append Trace task to root build.gradle. Reason: %s", err)
	}

	stepSrc, err := env(stepSrcDirEnvName)
	if err != nil {
		return err
	}
	if err := addTaskFile(stepSrc, projSrc); err != nil {
		return fmt.Errorf("failed to add Trace injector task file to project. Reason: %s", err)
	}
	return nil
}

// Finds and returns the path for the root build.gradle or build.gradle.kts
func findRootGradle(projectDir string) (string, error) {
	groovyGradle := path.Join(projectDir, "build.gradle")
	if _, err := os.Stat(groovyGradle); err == nil {
		return groovyGradle, nil
	}

	kotlinGradle := path.Join(projectDir, "build.gradle.kts")
	if _, err := os.Stat(kotlinGradle); err == nil {
		return kotlinGradle, nil
	}

	return "", fmt.Errorf("could not find any suitable build gradle files in %s. Please make sure the input for "+
		"project path is correctly set and there is a build.gradle or build.gradle.kts file", projectDir)

}

// Runs the VerifyTraceTask. This will verify the required dependencies and plugins are present for Trace.
func runVerifyTraceTask(rootDir, options string) error {
	optionSlice, err := shellquote.Split(options)
	if err != nil {
		return fmt.Errorf("cannot parse Gradle Task Options, please make sure it is set correctly. Value: \"%s\". Error: %s ", options, err)
	}

	projDir, err := projectDir(rootDir)
	if err != nil {
		return fmt.Errorf("cannot start verify task. Reason: %s", err)
	}

	var stdOut bytes.Buffer
	var stdErr bytes.Buffer
	cmdSlice := []string{path.Join(projDir, "./gradlew"), verifyTraceTaskName, "-p", projDir}
	cmdSlice = append(cmdSlice, optionSlice...)

	cmd := exec.Command(cmdSlice[0], cmdSlice[1:]...)
	printCommand(cmd)

	cmd.Stdout = &stdOut
	cmd.Stderr = &stdErr
	e := cmd.Run()
	if e != nil {
		return fmt.Errorf("VerifyTraceTask failed. Error: %s\nConsole output: %s\nError output: %s", e, stdOut.String(), stdErr.String())
	}
	fmt.Printf("Console output from VerifyTrace task:\n%s", stdOut.String())

	return nil
}

func printCommand(cmd *exec.Cmd) {
	fmt.Printf("==> Executing: %s\n", strings.Join(cmd.Args, " "))
}
