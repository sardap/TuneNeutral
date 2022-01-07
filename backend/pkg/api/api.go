package api

import (
	"fmt"
	"math/rand"
	"sort"
	"time"

	"github.com/gin-contrib/sessions"

	"github.com/dgraph-io/badger/v3"
	"github.com/sardap/TuneNeutral/backend/pkg/db"
	"github.com/sardap/TuneNeutral/backend/pkg/models"
	"github.com/zmb3/spotify"
)

var (
	ErrServerError = fmt.Errorf("internal server error")
	ErrNotFound    = fmt.Errorf("not found")
)

const (
	Userkey  = "user"
	TokenKey = "token"
	StateKey = "state"
)

func Logout(session sessions.Session) error {
	session.Delete(Userkey)
	session.Delete(TokenKey)
	session.Save()

	return nil
}

func GetPlaylists(db *db.Database, userId string) ([]*models.MoodPlaylist, error) {
	playlists, err := db.GetMoodPlaylists(userId)
	if err != nil {
		return nil, ErrServerError
	}

	return playlists, nil
}

func GetPlaylist(db *db.Database, userId string, date string) (*models.MoodPlaylist, error) {
	playlist, err := db.GetMoodPlaylist(userId, date)
	if err != nil {
		if err == badger.ErrKeyNotFound {
			return nil, ErrNotFound
		}
		return nil, ErrServerError
	}

	return playlist, nil
}

func transformValence(valence float32) float32 {
	return valence - 0.5
}

type SpotifyClient interface {
	CurrentUsersTracksOpt(*spotify.Options) (*spotify.SavedTrackPage, error)
	GetAudioFeatures(ids ...spotify.ID) ([]*spotify.AudioFeatures, error)
	CreatePlaylistForUser(userID, playlistName, description string, public bool) (*spotify.FullPlaylist, error)
	GetPlaylistTracks(playlistID spotify.ID) (*spotify.PlaylistTrackPage, error)
	RemoveTracksFromPlaylist(playlistID spotify.ID, trackIDs ...spotify.ID) (newSnapshotID string, err error)
	AddTracksToPlaylist(playlistID spotify.ID, trackIDs ...spotify.ID) (snapshotID string, err error)
}

func fetchNextUserTracks(db *db.Database, userId string, client SpotifyClient) error {
	if db.UserFetchLocked(userId) {
		return nil
	}

	userTracks, err := db.GetUserTracks(userId)
	if err == badger.ErrKeyNotFound {
		userTracks = &models.UserTracks{
			UserId:   userId,
			TrackIds: make(map[string]models.MinTrack),
		}
	}

	limit := 20
	opts := &spotify.Options{
		Offset: &userTracks.LastOffset,
		Limit:  &limit,
	}
	userTracksResponse, err := client.CurrentUsersTracksOpt(opts)
	if err != nil {
		return err
	}
	userTracks.LastOffset += limit

	featuresMap := make(map[string]*spotify.AudioFeatures)
	featuresToFetch := make([]spotify.ID, 0)
	for _, track := range userTracksResponse.Tracks {
		featuresToFetch = append(featuresToFetch, track.ID)
	}

	features, err := client.GetAudioFeatures(featuresToFetch...)
	if err != nil {
		return err
	}

	for _, feature := range features {
		featuresMap[feature.ID.String()] = feature
	}

	for _, track := range userTracksResponse.Tracks {
		if userTracks.LastTrackScanned != nil && string(track.ID) == *userTracks.LastTrackScanned {
			userTracks.LastOffset = userTracksResponse.Total + 1
			break
		}

		marketsMap := make(map[string]error)
		for _, market := range track.AvailableMarkets {
			marketsMap[market] = nil
		}

		feature, ok := featuresMap[track.ID.String()]
		if !ok {
			continue
		}

		modelTrack := models.Track{
			Id:               string(track.ID),
			Name:             track.Name,
			Valence:          feature.Valence,
			Energy:           feature.Energy,
			AvailableMarkets: marketsMap,
			AlbumId:          track.Album.ID.String(),
			Artists:          track.Artists,
		}

		if len(track.Album.Images) > 0 {
			modelTrack.AlbumArtUrl = track.Album.Images[0].URL
		}

		db.PutTrack(&modelTrack)

		userTracks.TrackIds[modelTrack.Id] = models.MinTrack{Valence: modelTrack.Valence, Energy: modelTrack.Energy}
	}

	if userTracks.LastOffset >= userTracksResponse.Total {
		userTracks.CompletedScan = true
		userTracks.LastOffset = 0
		lastTrack := string(userTracksResponse.Tracks[len(userTracksResponse.Tracks)-1].ID)
		userTracks.LastTrackScanned = &lastTrack
	}

	db.SetUserTracks(userId, userTracks)

	if !userTracks.CompletedScan && len(userTracks.TrackIds) < 1000 {
		return fetchNextUserTracks(db, userId, client)
	}

	db.SetUserFetchLock(userId)

	return nil
}

func feelNothingYet(mood models.Mood) bool {
	return mood > models.MoodNothing-models.Mood(0.05) && mood < models.MoodNothing+models.Mood(0.05)
}

func GenerateMoodPlaylist(
	dbConn *db.Database, userId string, client SpotifyClient,
	startMood models.Mood, date time.Time, note string,
) (*models.MoodPlaylist, error) {
	if err := fetchNextUserTracks(dbConn, userId, client); err != nil {
		return nil, err
	}

	userTracks, _ := dbConn.GetUserTracks(userId)

	ignoreTracks := make(map[string]interface{})
	{
		playlists, _ := dbConn.GetMoodPlaylitsBetweenDates(userId, date.Add(-(24 * 7 * time.Hour)), date)

		for _, playlist := range playlists {
			for _, track := range playlist.Tracks {
				ignoreTracks[track] = nil
			}
		}

	}

	type entry struct {
		id      string
		valence float32
		energy  float32
	}
	entries := make([]*entry, 0)
	for id, minTrack := range userTracks.TrackIds {
		if _, ok := ignoreTracks[id]; ok {
			continue
		}

		entries = append(entries, &entry{
			id:      id,
			valence: minTrack.Valence,
			energy:  minTrack.Energy,
		})
	}

	result := &models.MoodPlaylist{
		Date: date,
		Note: &note,
	}

	feelNothing := false

	valSteps := make(map[models.Mood][]*entry)
	for _, entry := range entries {
		valSteps[models.ValenceMoodCategory(transformValence(entry.valence))] =
			append(valSteps[models.ValenceMoodCategory(transformValence(entry.valence))], entry)
	}

	for mood := range valSteps {
		rand.Shuffle(len(valSteps[mood]), func(i, j int) {
			valSteps[mood][i], valSteps[mood][j] = valSteps[mood][j], valSteps[mood][i]
		})
	}

	var selectedTracks []*entry

	result.StartMood = float32(startMood)

	mood := startMood

	for len(selectedTracks) < 10 {
		if feelNothingYet(mood) {
			feelNothing = true
		}

		moodCategory := models.ValenceMoodCategory(float32(mood)).Opposite()

		for !feelNothing && len(valSteps[moodCategory]) <= 0 && !feelNothingYet(moodCategory) {
			if mood >= models.MoodNothing {
				moodCategory += 0.125
			} else {
				moodCategory -= 0.125
			}
		}

		if moodCategory == models.MoodNothing && len(valSteps[moodCategory]) <= 0 {
			break
		}

		entry := valSteps[moodCategory][0]

		nextMood := mood + (models.Mood(transformValence(entry.valence)) / 4)
		valSteps[moodCategory] = append(valSteps[moodCategory][:0], valSteps[moodCategory][0+1:]...)

		if feelNothing && !feelNothingYet(nextMood) {
			continue
		}

		selectedTracks = append(selectedTracks, entry)
		mood = nextMood
	}

	sort.Slice(selectedTracks, func(i, j int) bool {
		if startMood > models.MoodNothing {
			return selectedTracks[i].valence < selectedTracks[j].valence
		}

		return selectedTracks[i].valence > selectedTracks[j].valence
	})

	for _, track := range selectedTracks {
		result.Tracks = append(result.Tracks, track.id)
	}

	result.EndMood = float32(models.ValenceMoodCategory(float32(mood)))

	dbConn.SetMoodPlaylist(userId, result)

	return result, nil
}

func UpdateSpotifyPlaylist(dbConn *db.Database, client SpotifyClient, userId string, date string) error {
	playlist, err := dbConn.GetMoodPlaylist(userId, date)
	if err != nil {
		return ErrNotFound
	}

	playlistId, err := dbConn.GetSpotifyPlaylist(userId)
	if err != nil {
		if err == badger.ErrKeyNotFound {
			resp, err := client.CreatePlaylistForUser(userId, "tune neutral", "Playlist for tune neutral", true)
			if err != nil {
				return ErrServerError
			}
			playlistId = string(resp.ID)
			dbConn.SetSpotifyPlaylist(userId, playlistId)
		} else {
			return ErrNotFound
		}
	}

	{
		tracksResp, err := client.GetPlaylistTracks(spotify.ID(playlistId))
		if err != nil {
			return ErrServerError
		}

		var ids []spotify.ID

		for _, track := range tracksResp.Tracks {
			ids = append(ids, track.Track.ID)
		}

		client.RemoveTracksFromPlaylist(spotify.ID(playlistId), ids...)
	}

	{
		var ids []spotify.ID

		for _, trackId := range playlist.Tracks {
			ids = append(ids, spotify.ID(trackId))
		}

		client.AddTracksToPlaylist(spotify.ID(playlistId), ids...)
	}

	return nil
}

func RemoveTrackFromUser(dbConn *db.Database, userId string, trackId string) error {
	userTracks, err := dbConn.GetUserTracks(userId)
	if err != nil {
		return ErrNotFound
	}

	if userTracks.IgnoredTracks == nil {
		userTracks.IgnoredTracks = map[string]interface{}{}
	}
	userTracks.IgnoredTracks[trackId] = nil

	_, ok := userTracks.TrackIds[trackId]
	if !ok {
		return ErrNotFound
	}
	delete(userTracks.TrackIds, trackId)

	dbConn.SetUserTracks(userId, userTracks)

	return nil
}

func UnremoveTrackFromUser(dbConn *db.Database, userId string, trackId string) error {
	userTracks, err := dbConn.GetUserTracks(userId)
	if err != nil {
		return ErrNotFound
	}

	if userTracks.IgnoredTracks == nil {
		userTracks.IgnoredTracks = map[string]interface{}{}
	}
	_, ok := userTracks.IgnoredTracks[trackId]
	if !ok {
		return ErrNotFound
	}
	delete(userTracks.IgnoredTracks, trackId)

	track, err := dbConn.GetTrack(trackId)
	if err != nil {
		return ErrNotFound
	}
	userTracks.TrackIds[trackId] = models.MinTrack{Valence: track.Valence, Energy: track.Energy}

	dbConn.SetUserTracks(userId, userTracks)

	return nil
}

func GetRemovedTracksForUser(dbConn *db.Database, userId string) ([]string, error) {
	userTracks, err := dbConn.GetUserTracks(userId)
	if err != nil {
		return nil, ErrNotFound
	}

	var trackIds []string

	for trackId := range userTracks.IgnoredTracks {
		trackIds = append(trackIds, trackId)
	}

	return trackIds, nil
}

func ClearUserData(dbConn *db.Database, userId string) error {
	dbConn.ClearUserTracks(userId)
	dbConn.ClearMoodPlaylists(userId)
	dbConn.ClearUserFetchLock(userId)
	dbConn.ClearSpotifyPlaylist(userId)

	return nil
}
