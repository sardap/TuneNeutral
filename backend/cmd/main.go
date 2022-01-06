package main

import (
	"fmt"
	"math/rand"
	"time"

	"github.com/namsral/flag"

	"github.com/sardap/TuneNeutral/backend/pkg/config"
	"github.com/sardap/TuneNeutral/backend/pkg/db"
	"github.com/sardap/TuneNeutral/backend/pkg/router"
)

func main() {
	rand.Seed(time.Now().UnixMicro())

	cfg := &config.Config{}

	flag.StringVar(&cfg.ClientId, "spotify-client-id", "", "spotify client id")
	flag.StringVar(&cfg.ClientSecret, "spotify-client-secret", "", "spotify client secert")
	flag.StringVar(&cfg.Scheme, "scheme", "http", "http scheme")
	flag.StringVar(&cfg.Domain, "domain", "localhost:8080", "domain")
	flag.StringVar(&cfg.DatabasePath, "database-path", "database", "database path")
	flag.StringVar(&cfg.WebsiteFilesPath, "website-file-path", "", "static website file path")
	flag.StringVar(&cfg.CookieAuthSecert, "cookie_auth_secret", "", "")
	flag.StringVar(&cfg.CookieEyncSecert, "cookie-enyc-secret", "", "")
	flag.Parse()

	if err := cfg.Valid(); err != nil {
		fmt.Printf("Error starting %v", err)
		return
	}

	dbConn := db.ConnectDb(cfg)
	defer dbConn.Close()
	router.CreateRouter(cfg, dbConn).Run(":8080")
}
