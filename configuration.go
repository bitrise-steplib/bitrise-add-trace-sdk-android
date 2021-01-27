package main

// These configurations should be constant (final and static), code should access them accordingly. The reason for this
// is that this file should hold them, to make it easy to have a clear overview and make it the single source of truth.
const configFileName = "bitrise-addons-configuration.json"
const configFileVersion = "1.0.0"
const apmTokenEnvName = "APM_COLLECTOR_TOKEN"
const srcDirEnvName = "BITRISE_SOURCE_DIR"
