package main

import (
	"bytes"
	"fmt"
	"io"
	"log"
	"os"
	"os/exec"
	"path"
	"path/filepath"
	"strings"
)

// Appends the TraceInjectorTask to the end of the root build.gradle.
func appendTraceInjectorTaskToProject(path string) error {
	f, err := os.OpenFile(path, os.O_APPEND|os.O_WRONLY, 0644)
	if err != nil {
		return fmt.Errorf("failed to open file on path \"%s\". %s", path, err)
	}

	defer func() {
		err = f.Close()
	}()
	if err != nil {
		return err
	}

	c, err := getTraceInjectorTaskContent(path)
	if err != nil {
		return err
	}

	if _, err := f.WriteString(c); err != nil {
		log.Println(err)
	}
	return nil
}

// Gets the string value for creating the TraceInjectorTask.
func getTraceInjectorTaskContent(path string) (string, error) {
	if strings.HasSuffix(path, kotlinBuildGradleSuffix) {
		// tasks.register<io.bitrise.trace.step.InjectTraceTask>("injectTraceTask")
		return fmt.Sprint("\n\ntasks.register<", injectTraceTaskClassName, ">(\"", injectTraceTaskName, "\")"), nil
	} else if strings.HasSuffix(path, groovyBuildGradleSuffix) {
		// task injectTraceTask(type: io.bitrise.trace.step.InjectTraceTask)
		return fmt.Sprint("\n\ntask ", injectTraceTaskName, "(type: ", injectTraceTaskClassName, ")"), nil
	}
	return "", fmt.Errorf("could not determine the language for gradle file at %s", path)
}

// Copies the TraceInjectorTask.java file from the steps source to the given projects buildSrc directory.
func addTaskFile(stepDir, projDir string) error {
	in, err := os.Open(path.Join(stepDir, injectTraceTaskFileSrcPath))
	if err != nil {
		return err
	}
	defer func() {
		err = in.Close()
	}()
	if err != nil {
		return err
	}

	dst := path.Join(projDir, injectTraceTaskFileDstPath)
	e := os.MkdirAll(filepath.Dir(dst), os.ModePerm)
	if e != nil {
		return e
	}
	out, err := os.Create(dst)
	if err != nil {
		return err
	}
	defer func() {
		err = out.Close()
	}()
	if err != nil {
		return err
	}

	_, err = io.Copy(out, in)
	if err != nil {
		return err
	}
	return out.Close()
}

// Runs the TraceInjectorTask.
func runTraceInjector() error {
	projDir, err := projectDir()
	if err != nil {
		return fmt.Errorf("cannot start injector task. Reason: %s", err)
	}

	var stdOut bytes.Buffer
	var stdErr bytes.Buffer
	cmd := exec.Command(path.Join(projDir, "./gradlew"), injectTraceTaskName, "-p", projDir)
	printCommand(cmd)

	cmd.Stdout = &stdOut
	cmd.Stderr = &stdErr
	e := cmd.Run()
	if e != nil {
		return fmt.Errorf("io.bitrise.trace.step.InjectTraceTask failed. Error: %s\nConsole output: %s\nError output: %s", e, stdOut.String(), stdErr.String())
	}

	return nil
}

func printCommand(cmd *exec.Cmd) {
	fmt.Printf("==> Executing: %s\n", strings.Join(cmd.Args, " "))
}