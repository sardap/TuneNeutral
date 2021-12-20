package tune

import (
	"fmt"
	"math/rand"
	"sort"
	"time"

	"github.com/dgraph-io/badger/v3"
	uuid "github.com/nu7hatch/gouuid"
	"github.com/sardap/TuneNeutral/backend/pkg/db"
	"github.com/sardap/TuneNeutral/backend/pkg/models"
	"github.com/zmb3/spotify"
)

var (
	ErrNotFound error = fmt.Errorf("not found")
)

func transformValence(valence float32) float32 {
	return valence - 0.5
}

func fetchNextUserTracks(userId string, client *spotify.Client) error {
	if db.Db.UserFetchLocked(userId) {
		return nil
	}

	userTracks, err := db.Db.GetUserTracks(userId)
	if err == badger.ErrKeyNotFound {
		userTracks = &models.UserTracks{
			UserId:   userId,
			TrackIds: make(map[string]float32),
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
			AvailableMarkets: marketsMap,
			AlbumId:          track.Album.ID.String(),
			Artists:          track.Artists,
		}

		if len(track.Album.Images) > 0 {
			modelTrack.AlbumArtUrl = track.Album.Images[0].URL
		}

		db.Db.PutTrack(&modelTrack)

		userTracks.TrackIds[modelTrack.Id] = modelTrack.Valence
	}

	if userTracks.LastOffset >= userTracksResponse.Total {
		userTracks.CompletedScan = true
		userTracks.LastOffset = 0
		lastTrack := string(userTracksResponse.Tracks[len(userTracksResponse.Tracks)-1].ID)
		userTracks.LastTrackScanned = &lastTrack
	}

	db.Db.SetUserTracks(userId, userTracks)

	if !userTracks.CompletedScan && len(userTracks.TrackIds) < 100 {
		return fetchNextUserTracks(userId, client)
	}

	db.Db.SetUserFetchLock(userId)

	return nil
}

func feelNothingYet(mood models.Mood) bool {
	return mood > models.MoodNothing-models.Mood(0.05) && mood < models.MoodNothing+models.Mood(0.05)
}

func GenerateMoodPlaylist(userId string, startMood models.Mood, date time.Time, client *spotify.Client) (*models.MoodPlaylist, error) {
	if err := fetchNextUserTracks(userId, client); err != nil {
		return nil, err
	}

	userTracks, _ := db.Db.GetUserTracks(userId)
	type entry struct {
		id      string
		valence float32
	}
	entries := make([]*entry, 0)
	for id, valence := range userTracks.TrackIds {
		// Ignore tracks which aren't suitable
		entries = append(entries, &entry{
			id:      id,
			valence: valence,
		})
	}

	result := &models.MoodPlaylist{
		Id: func() string {
			u, _ := uuid.NewV4()
			return u.String()
		}(),
		Date: date,
	}

	feelNothing := false

	steps := make(map[models.Mood][]*entry)
	for _, entry := range entries {
		steps[models.ValenceMoodCategory(transformValence(entry.valence))] = append(steps[models.ValenceMoodCategory(transformValence(entry.valence))], entry)
	}

	for mood := range steps {
		rand.Shuffle(len(steps[mood]), func(i, j int) {
			steps[mood][i], steps[mood][j] = steps[mood][j], steps[mood][i]
		})
	}

	var selectedTracks []*entry

	result.StartMood = float32(models.ValenceMoodCategory(float32(startMood)))

	mood := startMood

	for len(selectedTracks) < 10 {
		if feelNothingYet(mood) {
			feelNothing = true
		}

		moodCategory := models.ValenceMoodCategory(float32(mood)).Opposite()

		// Loops here forever

		for !feelNothing && len(steps[moodCategory]) <= 0 && !feelNothingYet(moodCategory) {
			if mood >= models.MoodNothing {
				moodCategory += 0.125
			} else {
				moodCategory -= 0.125
			}
		}

		if moodCategory == models.MoodNothing && len(steps[moodCategory]) <= 0 {
			break
		}

		entry := steps[moodCategory][0]

		nextMood := mood + (models.Mood(transformValence(entry.valence)) / 3.5)
		steps[moodCategory] = append(steps[moodCategory][:0], steps[moodCategory][0+1:]...)

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

	db.Db.SetMoodPlaylist(userId, result)

	return result, nil
}
