package api_test

import (
	"path"
	"testing"
	"time"

	"github.com/dgraph-io/badger/v3"
	"github.com/sardap/TuneNeutral/backend/pkg/api"
	"github.com/sardap/TuneNeutral/backend/pkg/config"
	"github.com/sardap/TuneNeutral/backend/pkg/db"
	"github.com/sardap/TuneNeutral/backend/pkg/models"
	"github.com/stretchr/testify/assert"
)

func setupDB(t *testing.T) *db.Database {
	cfg := &config.Config{
		DatabasePath: path.Join(t.TempDir(), "database"),
	}

	return db.ConnectDb(cfg)
}

func TestClearUserData(t *testing.T) {
	t.Parallel()

	const userId = "paul"

	// setup
	dbConn := setupDB(t)
	defer dbConn.Close()
	dbConn.SetUserTracks(userId, &models.UserTracks{
		UserId:     userId,
		LastOffset: 1,
	})
	date, _ := time.Parse("2022-01-06", models.DateFormat)
	dbConn.SetMoodPlaylist(userId, &models.MoodPlaylist{
		Date: date,
	})
	dbConn.SetUserFetchLock(userId)

	// Run
	assert.NoError(t, api.ClearUserData(dbConn, userId))

	assert.ErrorIs(t, func() error { _, err := dbConn.GetUserTracks(userId); return err }(), badger.ErrKeyNotFound)
	assert.ErrorIs(t, func() error { _, err := dbConn.GetMoodPlaylist(userId, "2022-01-06"); return err }(), badger.ErrKeyNotFound)
	assert.False(t, dbConn.UserFetchLocked(userId))
}

func TestGetRemovedTracksForUser(t *testing.T) {
	t.Parallel()

	const userId = "paul"

	// setup
	dbConn := setupDB(t)
	defer dbConn.Close()

	type scenario struct {
		ignoredTracks []string
	}
	scenarios := []scenario{
		{
			ignoredTracks: []string{"please", "hire", "me"},
		},
		{
			ignoredTracks: []string{},
		},
	}
	for _, scenario := range scenarios {
		ignoreTracksMap := make(map[string]interface{})
		for _, track := range scenario.ignoredTracks {
			ignoreTracksMap[track] = nil
		}
		dbConn.SetUserTracks(userId, &models.UserTracks{
			UserId:        userId,
			LastOffset:    1,
			IgnoredTracks: ignoreTracksMap,
		})

		removedTracks, err := api.GetRemovedTracksForUser(dbConn, userId)

		assert.NoError(t, err)
		assert.Len(t, removedTracks, len(scenario.ignoredTracks))
	}

	dbConn.ClearUserTracks(userId)
	_, err := api.GetRemovedTracksForUser(dbConn, userId)
	assert.ErrorIs(t, err, api.ErrNotFound)
}

func TestUnremoveTrackFromUser(t *testing.T) {
	t.Parallel()

	const userId = "paul"

	// setup
	dbConn := setupDB(t)
	defer dbConn.Close()
	type scenario struct {
		ignoredTracks   []string
		trackToUnremove string
		expectedErr     error
	}
	scenarios := []scenario{
		{
			ignoredTracks:   []string{"please", "hire", "me"},
			trackToUnremove: "please",
		},
		{
			ignoredTracks:   []string{},
			trackToUnremove: "please",
			expectedErr:     api.ErrNotFound,
		},
	}
	for _, scenario := range scenarios {
		ignoreTracksMap := make(map[string]interface{})
		for _, track := range scenario.ignoredTracks {
			ignoreTracksMap[track] = nil
			dbConn.PutTrack(&models.Track{
				Id:   track,
				Name: track,
			})
		}
		dbConn.SetUserTracks(userId, &models.UserTracks{
			UserId:        userId,
			LastOffset:    1,
			IgnoredTracks: ignoreTracksMap,
			TrackIds:      make(map[string]models.MinTrack),
		})

		err := api.UnremoveTrackFromUser(dbConn, userId, scenario.trackToUnremove)

		assert.ErrorIs(t, err, scenario.expectedErr)
	}

	dbConn.ClearUserTracks(userId)
	err := api.UnremoveTrackFromUser(dbConn, userId, "")
	assert.ErrorIs(t, err, api.ErrNotFound)
}

func TestRemoveTrackFromUser(t *testing.T) {
	t.Parallel()

	const userId = "paul"

	// setup
	dbConn := setupDB(t)
	defer dbConn.Close()
	type scenario struct {
		tracks        []string
		trackToRemove string
		expectedErr   error
	}
	scenarios := []scenario{
		{
			tracks:        []string{"please", "hire", "me"},
			trackToRemove: "please",
		},
		{
			tracks:        []string{"hire", "me"},
			trackToRemove: "please",
			expectedErr:   api.ErrNotFound,
		},
	}
	for _, scenario := range scenarios {
		tracksMap := make(map[string]models.MinTrack)
		for _, track := range scenario.tracks {
			tracksMap[track] = models.MinTrack{
				Valence: 0,
				Energy:  0,
			}
			dbConn.PutTrack(&models.Track{
				Id:   track,
				Name: track,
			})
		}
		dbConn.SetUserTracks(userId, &models.UserTracks{
			UserId:   userId,
			TrackIds: tracksMap,
		})

		err := api.RemoveTrackFromUser(dbConn, userId, scenario.trackToRemove)

		assert.ErrorIs(t, err, scenario.expectedErr)
	}

	dbConn.ClearUserTracks(userId)
	err := api.RemoveTrackFromUser(dbConn, userId, "")
	assert.ErrorIs(t, err, api.ErrNotFound)
}
