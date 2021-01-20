package main

import (
	"encoding/json"
	"fmt"
	"github.com/bitrise-io/go-utils/log"
	"io/ioutil"
	"os"
)

// ConfigFileContent is the struct for the content of a config file.
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
	log.Debugf("Getting the content for the config file:\nToken value is \"%s\"\nConfig file version is \"%s\"", t, configFileVersion)
	return ConfigFileContent{
		Version: configFileVersion,
		Token:   t,
	}, nil
}

// Formats the ConfigFileContent to JSON.
// Returns the value of the JSON in bytes.
func formatConfigFileContent(content ConfigFileContent) ([]byte, error) {
	c, err := json.Marshal(content)
	log.Debugf("Formatting the config file content to JSON")
	if err != nil {
		return c, fmt.Errorf("failed to format the received Configuration to JSON! Error: %s", err)
	}
	return c, nil
}

// Creates the configuration file on the given path with the given content.
func createConfigFile(content []byte, path string) error {
	log.Debugf("Writing the config file content to \"%s\"", path)
	err := ioutil.WriteFile(path, content, 0644)
	if err != nil {
		return fmt.Errorf("failed to write to the configuraution file! Error: %s", err)
	}
	return nil
}
