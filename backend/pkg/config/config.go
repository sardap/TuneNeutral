package config

import "fmt"

type Config struct {
	ClientId         string
	ClientSecret     string
	Domain           string
	Scheme           string
	DatabasePath     string
	WebsiteFilesPath string
	CookieAuthSecert string
	CookieEyncSecert string
}

func (c *Config) Valid() error {
	if c.ClientId == "" {
		return fmt.Errorf("spotify-client-id must be set")
	}

	if c.ClientSecret == "" {
		return fmt.Errorf("spotify-client-secret must be set")
	}

	if c.WebsiteFilesPath == "" {
		return fmt.Errorf("website-files-path must be set")
	}

	if c.CookieAuthSecert == "" {
		return fmt.Errorf("cookie secert must be set")
	}

	if c.CookieEyncSecert == "" {
		return fmt.Errorf("cookie eync must be set")

	}

	return nil
}
