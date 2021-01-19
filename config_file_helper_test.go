package main

import (
	"reflect"
	"testing"
)

const expectedConfigFile = `{"version":"1.0.0","token":"sampleToken"}`

func Test_formatConfigFileContent(t *testing.T) {
	tests := []struct {
		name    string
		content ConfigFileContent
		want    []byte
		wantErr bool
	}{
		{"valid_test", ConfigFileContent{Version: "1.0.0", Token: "sampleToken"}, []byte(expectedConfigFile), false},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := formatConfigFileContent(tt.content)
			if (err != nil) != tt.wantErr {
				t.Errorf("formatConfigFileContent() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			if !reflect.DeepEqual(got, tt.want) {
				t.Errorf("formatConfigFileContent() = %v, want %v", string(got), string(tt.want))
			}
		})
	}
}
