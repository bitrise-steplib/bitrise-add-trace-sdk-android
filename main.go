package main

import (
	"fmt"
	"github.com/bitrise-io/go-utils/log"
	"os"
	"path"
)

// Called when the main function should be terminated with failure.
func failf(format string, v ...interface{}) {
	log.Errorf(format, v...)
	os.Exit(1)
}

func main() {
	log.Infof("Creating the configuration file")
	if err := createConfigurationFile(); err != nil {
		failf("Could not create the config file, aborting build. Reason: %s\n", err)
	}
	log.Infof("Configuration file successfully created")

	log.Infof("Adding Trace injector to project")
	if err := addTraceInjectorTask(); err != nil {
		failf("Could not add Trace injector to project, aborting build. Reason: %s\n", err)
	}
	log.Infof("Added Trace injector to project")

	log.Infof("Running Trace injector on project")
	if err := runTraceInjector(); err != nil {
		failf("Error when injecting Trace to project, aborting build. Reason: %s\n", err)
	}
	log.Infof("Trace injector successfully injected the SDK")

	os.Exit(0)
}

// Creates the configuration file for the given Android project. The configuration file has the required properties for
// building the Android application.
func createConfigurationFile() error {
	c, err := getConfigFileContent()
	if err != nil {
		return err
	}

	fc, err := formatConfigFileContent(c)
	if err != nil {
		return err
	}

	src, err := projectDir()
	if err != nil {
		return err
	}
	p := path.Join(src, configFileName)
	return createConfigFile(fc, p)
}

func addTraceInjectorTask() error {
	projSrc, err := projectDir()
	if err != nil {
		return err
	}
	if err := appendTraceInjectorTaskToProject(path.Join(projSrc, "build.gradle")); err != nil {
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
