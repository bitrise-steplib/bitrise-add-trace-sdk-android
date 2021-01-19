package main

import (
	"encoding/json"
	"fmt"
	. "io/ioutil"
	"os"
)

// This is the struct for the content of a config file.
type ConfigFileContent struct {
	Version string `json:"version"`
	Token   string `json:"token"`
}

// Gets the content for the configuration file as a ConfigContent.
func getConfigFileContent() (ConfigFileContent, error) {
	t := os.Getenv(apmTokenEnvName)
	if t == "" {
		return ConfigFileContent{}, fmt.Errorf("token is not set in env variables")
	}
	return ConfigFileContent{
		Version: configFileVersion,
		Token:   t,
	}, nil
}

// Formats the ConfigFileContent to JSON.
// Returns the value of the JSON in bytes.
func formatConfigFileContent(content ConfigFileContent) ([]byte, error) {
	c, err := json.Marshal(content)
	if err != nil {
		return c, fmt.Errorf("failed to format the received Configuration to JSON! Error: %s", err)
	}
	return c, nil
}

// Creates the configuration file on the given path with the given content.
func createConfigFile(content []byte, path string) error {
	err := WriteFile(path, content, 0644)
	if err != nil {
		return fmt.Errorf("failed to write to the configuraution file! Error: %s", err)
	}
	return nil
}
