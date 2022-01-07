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
	"github.com/zmb3/spotify"
)

const userId = "paul"

type (
	CurrentUsersTracksOptFunc    func(*spotify.Options) (*spotify.SavedTrackPage, error)
	GetAudioFeaturesFunc         func(...spotify.ID) ([]*spotify.AudioFeatures, error)
	CreatePlaylistForUserFunc    func(userID, playlistName, description string, public bool) (*spotify.FullPlaylist, error)
	GetPlaylistTracksFunc        func(playlistID spotify.ID) (*spotify.PlaylistTrackPage, error)
	RemoveTracksFromPlaylistFunc func(playlistID spotify.ID, trackIDs ...spotify.ID) (newSnapshotID string, err error)
	AddTracksToPlaylistFunc      func(playlistID spotify.ID, trackIDs ...spotify.ID) (snapshotID string, err error)
)

type mockSpotifyClient struct {
	currentUsersTracksOpt    CurrentUsersTracksOptFunc
	getAudioFeatures         GetAudioFeaturesFunc
	createPlaylistForUser    CreatePlaylistForUserFunc
	getPlaylistTracks        GetPlaylistTracksFunc
	removeTracksFromPlaylist RemoveTracksFromPlaylistFunc
	addTracksToPlaylist      AddTracksToPlaylistFunc
}

func (m *mockSpotifyClient) CurrentUsersTracksOpt(options *spotify.Options) (*spotify.SavedTrackPage, error) {
	return m.currentUsersTracksOpt(options)
}

func (m *mockSpotifyClient) GetAudioFeatures(ids ...spotify.ID) ([]*spotify.AudioFeatures, error) {
	return m.getAudioFeatures(ids...)
}

func (m *mockSpotifyClient) CreatePlaylistForUser(userID, playlistName, description string, public bool) (*spotify.FullPlaylist, error) {
	return m.createPlaylistForUser(userID, playlistName, description, public)
}

func (m *mockSpotifyClient) GetPlaylistTracks(playlistID spotify.ID) (*spotify.PlaylistTrackPage, error) {
	return m.getPlaylistTracks(playlistID)
}

func (m *mockSpotifyClient) RemoveTracksFromPlaylist(playlistID spotify.ID, trackIDs ...spotify.ID) (newSnapshotID string, err error) {
	return m.removeTracksFromPlaylist(playlistID, trackIDs...)
}

func (m *mockSpotifyClient) AddTracksToPlaylist(playlistID spotify.ID, trackIDs ...spotify.ID) (snapshotID string, err error) {
	return m.addTracksToPlaylist(playlistID, trackIDs...)
}

func newMockSpotifyClient() *mockSpotifyClient {
	return &mockSpotifyClient{
		currentUsersTracksOpt: func(o *spotify.Options) (*spotify.SavedTrackPage, error) {
			return nil, nil
		},
		getAudioFeatures: func(i ...spotify.ID) ([]*spotify.AudioFeatures, error) {
			return nil, nil
		},
		createPlaylistForUser: func(userID, playlistName, description string, public bool) (*spotify.FullPlaylist, error) {
			return nil, nil
		},
		getPlaylistTracks: func(playlistID spotify.ID) (*spotify.PlaylistTrackPage, error) {
			return nil, nil
		},
		removeTracksFromPlaylist: func(playlistID spotify.ID, trackIDs ...spotify.ID) (newSnapshotID string, err error) {
			return "", nil
		},
		addTracksToPlaylist: func(playlistID spotify.ID, trackIDs ...spotify.ID) (snapshotID string, err error) {
			return "", nil
		},
	}
}

func newDatabase(t *testing.T) *db.Database {
	cfg := &config.Config{
		DatabasePath: path.Join(t.TempDir(), "database"),
	}

	return db.ConnectDb(cfg)
}

func easyParseDate(dateStr string) time.Time {
	date, _ := time.Parse(models.DateFormat, dateStr)
	return date
}

func TestClearUserData(t *testing.T) {
	t.Parallel()

	// setup
	dbConn := newDatabase(t)
	defer dbConn.Close()
	dbConn.SetUserTracks(userId, &models.UserTracks{
		UserId:     userId,
		LastOffset: 1,
	})
	dbConn.SetMoodPlaylist(userId, &models.MoodPlaylist{
		Date: easyParseDate("2022-01-06"),
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

	// setup
	dbConn := newDatabase(t)
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

	// setup
	dbConn := newDatabase(t)
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

	// setup
	dbConn := newDatabase(t)
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

func TestUpdateSpotifyPlaylist(t *testing.T) {
	t.Parallel()

	client := newMockSpotifyClient()

	// setup
	dbConn := newDatabase(t)
	defer dbConn.Close()
	type scenario struct {
		playlists               []*models.MoodPlaylist
		spotifyPlaylist         *spotify.FullPlaylist
		spotifyPlaylistTracks   *spotify.PlaylistTrackPage
		expectedSpotifyPlaylist []string
		dbSpotifyPlaylistId     string
		targetPlaylistDate      string
		expectedErr             error
	}
	scenarios := []scenario{
		{
			playlists: []*models.MoodPlaylist{
				{
					Date:   easyParseDate("2000-01-20"),
					Tracks: []string{"please", "hire", "me"},
				},
			},
			spotifyPlaylistTracks: &spotify.PlaylistTrackPage{
				Tracks: []spotify.PlaylistTrack{
					{
						Track: spotify.FullTrack{
							SimpleTrack: spotify.SimpleTrack{
								ID: "duck",
							},
						},
					},
				},
			},
			expectedSpotifyPlaylist: []string{"please", "hire", "me"},
			dbSpotifyPlaylistId:     "spotify_id",
			targetPlaylistDate:      "2000-01-20",
			expectedErr:             nil,
		},
		{
			playlists: []*models.MoodPlaylist{
				{
					Date:   easyParseDate("2000-01-20"),
					Tracks: []string{},
				},
				{
					Date:   easyParseDate("2000-01-21"),
					Tracks: []string{"please", "hire", "me"},
				},
			},
			expectedSpotifyPlaylist: []string{},
			targetPlaylistDate:      "2000-01-20",
			expectedErr:             nil,
		},
		{
			playlists: []*models.MoodPlaylist{
				{
					Date:   easyParseDate("2000-01-20"),
					Tracks: []string{},
				},
			},
			expectedSpotifyPlaylist: []string{},
			targetPlaylistDate:      "2000-01-21",
			expectedErr:             api.ErrNotFound,
		},
	}
	for _, scenario := range scenarios {
		client.createPlaylistForUser = func(userID, playlistName, description string, public bool) (*spotify.FullPlaylist, error) {
			scenario.spotifyPlaylistTracks = &spotify.PlaylistTrackPage{
				Tracks: []spotify.PlaylistTrack{},
			}
			return &spotify.FullPlaylist{SimplePlaylist: spotify.SimplePlaylist{
				ID: "id",
			}}, nil
		}
		client.getPlaylistTracks = func(playlistID spotify.ID) (*spotify.PlaylistTrackPage, error) {
			return scenario.spotifyPlaylistTracks, nil
		}
		client.addTracksToPlaylist = func(playlistID spotify.ID, trackIDs ...spotify.ID) (snapshotID string, err error) {
			for _, id := range trackIDs {
				scenario.spotifyPlaylistTracks.Tracks = append(scenario.spotifyPlaylistTracks.Tracks, spotify.PlaylistTrack{
					Track: spotify.FullTrack{
						SimpleTrack: spotify.SimpleTrack{
							ID: id,
						},
					},
				})
			}
			return "", nil
		}
		client.removeTracksFromPlaylist = func(playlistID spotify.ID, trackIDs ...spotify.ID) (newSnapshotID string, err error) {
			tracks := &scenario.spotifyPlaylistTracks.Tracks
			for _, id := range trackIDs {
				idx := -1
				for i, track := range *tracks {
					if track.Track.ID == id {
						idx = i
					}
				}
				(*tracks)[idx] = (*tracks)[len(*tracks)-1]
				*tracks = (*tracks)[:len(*tracks)-1]
			}
			return "", nil
		}
		if scenario.dbSpotifyPlaylistId != "" {
			dbConn.SetSpotifyPlaylist(userId, scenario.dbSpotifyPlaylistId)
		}
		for _, playlist := range scenario.playlists {
			dbConn.SetMoodPlaylist(userId, playlist)
		}

		err := api.UpdateSpotifyPlaylist(dbConn, client, userId, scenario.targetPlaylistDate)

		assert.ErrorIs(t, err, scenario.expectedErr)

		if scenario.spotifyPlaylistTracks != nil {
			for i, track := range scenario.spotifyPlaylistTracks.Tracks {
				assert.Equal(t, string(track.Track.ID), scenario.expectedSpotifyPlaylist[i])
			}
		}

		api.ClearUserData(dbConn, userId)
	}
}

func TestGenerateMoodPlaylist(t *testing.T) {
	t.Parallel()

	client := newMockSpotifyClient()

	// setup
	dbConn := newDatabase(t)
	defer dbConn.Close()
	type scenario struct {
		startMood            models.Mood
		date                 time.Time
		note                 string
		spotifySavedTracks   []spotify.SavedTrack
		spotifyAudioFeatures []*spotify.AudioFeatures
		expectedTracks       []string
		expectedErr          error
		expectedEndMood      float32
	}
	scenarios := []scenario{
		{
			startMood: models.Mood(0.45),
			date:      easyParseDate("2000-01-20"),
			note:      "Feeling pretty good",
			spotifySavedTracks: []spotify.SavedTrack{
				{
					FullTrack: spotify.FullTrack{
						SimpleTrack: spotify.SimpleTrack{
							ID:   "please",
							Name: "please",
							Artists: []spotify.SimpleArtist{
								{
									Name: "paul",
									ID:   "paul",
								},
							},
						},
						Album: spotify.SimpleAlbum{
							ID: "tune",
						},
					},
				},
				{
					FullTrack: spotify.FullTrack{
						SimpleTrack: spotify.SimpleTrack{
							ID:   "hire",
							Name: "hire",
							Artists: []spotify.SimpleArtist{
								{
									Name: "paul",
									ID:   "paul",
								},
							},
						},
						Album: spotify.SimpleAlbum{
							ID: "tune",
						},
					},
				},
				{
					FullTrack: spotify.FullTrack{
						SimpleTrack: spotify.SimpleTrack{
							ID:   "me",
							Name: "me",
							Artists: []spotify.SimpleArtist{
								{
									Name: "paul",
									ID:   "paul",
								},
							},
						},
						Album: spotify.SimpleAlbum{
							ID: "tune",
						},
					},
				},
			},
			spotifyAudioFeatures: []*spotify.AudioFeatures{
				{
					ID:      "please",
					Valence: 0.2,
					Energy:  0.5,
				},
				{
					ID:      "hire",
					Valence: 0.3,
					Energy:  0.8,
				},
				{
					ID:      "me",
					Valence: 0.5,
					Energy:  0.9,
				},
			},
			expectedTracks:  []string{"please", "hire", "me"},
			expectedEndMood: 0.25,
			expectedErr:     nil,
		},
	}
	for _, scenario := range scenarios {
		client.currentUsersTracksOpt = func(o *spotify.Options) (*spotify.SavedTrackPage, error) {
			result := &spotify.SavedTrackPage{}
			result.Limit = *o.Limit
			result.Offset = *o.Offset
			result.Total = len(scenario.spotifySavedTracks)

			if *o.Offset+*o.Limit > len(scenario.spotifySavedTracks) {
				result.Tracks = scenario.spotifySavedTracks[*o.Offset:len(scenario.spotifySavedTracks)]
			} else {
				result.Tracks = scenario.spotifySavedTracks[*o.Offset : *o.Offset+*o.Limit]
			}

			return result, nil
		}

		client.getAudioFeatures = func(ids ...spotify.ID) ([]*spotify.AudioFeatures, error) {
			result := make([]*spotify.AudioFeatures, 0)

			for _, id := range ids {
				for _, track := range scenario.spotifyAudioFeatures {
					if id == track.ID {
						result = append(result, track)
						break
					}
				}
			}

			if len(result) != len(ids) {
				return nil, spotify.Error{
					Status: 404,
				}
			}

			return result, nil
		}

		moodPlaylist, err := api.GenerateMoodPlaylist(dbConn, userId, client, scenario.startMood, scenario.date, scenario.note)

		for i, track := range scenario.expectedTracks {
			assert.Equal(t, track, moodPlaylist.Tracks[i])
		}
		assert.Equal(t, scenario.expectedEndMood, moodPlaylist.EndMood)
		assert.ErrorIs(t, err, scenario.expectedErr)
	}
}
