package api

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"math/rand"
	"net/http"
	"sort"
	"time"

	"github.com/gin-contrib/sessions"
	"github.com/gin-gonic/gin"

	"github.com/dgraph-io/badger/v3"
	"github.com/sardap/TuneNeutral/backend/pkg/config"
	"github.com/sardap/TuneNeutral/backend/pkg/db"
	"github.com/sardap/TuneNeutral/backend/pkg/models"
	"github.com/zmb3/spotify"
	"golang.org/x/oauth2"
)

var (
	ErrServerError = fmt.Errorf("internal server error")
	ErrNotFound    = fmt.Errorf("not found")
)

var (
	auth spotify.Authenticator
)

func InitApi(cfg *config.Config) {
	auth = spotify.NewAuthenticator(
		fmt.Sprintf("%s://%s/callback", cfg.Scheme, cfg.Domain),
		spotify.ScopePlaylistReadPrivate,
		spotify.ScopePlaylistReadCollaborative,
		spotify.ScopeUserLibraryRead,
		spotify.ScopeUserReadPrivate,
		spotify.ScopeUserReadPlaybackState,
		spotify.ScopeUserModifyPlaybackState,
		spotify.ScopeUserTopRead,
		spotify.ScopeStreaming,
		spotify.ScopePlaylistModifyPublic,
	)

	auth.SetAuthInfo(
		cfg.ClientId,
		cfg.ClientSecret,
	)
}

func decodeToken(encodedToken []byte) *oauth2.Token {
	token, _ := base64.StdEncoding.DecodeString(string(encodedToken))

	var result oauth2.Token
	json.Unmarshal(token, &result)

	return &result
}

func GetClientFromToken(token []byte) (spotify.Client, error) {
	return auth.NewClient(decodeToken(token)), nil
}

func encodeToken(a *oauth2.Token) []byte {
	jsonfied, _ := json.Marshal(*a)

	return []byte(base64.StdEncoding.EncodeToString(jsonfied))
}

// the user will eventually be redirected back to your redirect URL
// typically you'll have a handler set up like the following:
func RedirectEndpoint(c *gin.Context) {
	defer time.Sleep(time.Until(time.Now().Add(1 * time.Second)))

	// use the same userId string here that you used to generate the URL
	key, err := db.Db.GetAuthState(c.Request.URL.Query().Get("state"))
	if err != nil {
		c.JSON(400, gin.H{
			"message": "invlaid",
		})
		return
	}

	session := sessions.Default(c)

	if session.Get(StateKey).(string) != key {
		c.JSON(400, gin.H{
			"message": "invlaid",
		})
		return
	}

	code := c.Request.URL.Query().Get("code")
	token, err := auth.Exchange(code)
	if err != nil {
		http.Error(c.Writer, "Couldn't get token", http.StatusNotFound)
		return
	}

	session.Set(TokenKey, encodeToken(token))
	if err := session.Save(); err != nil {
		return
	}

	c.Redirect(http.StatusTemporaryRedirect, "/")
}

func AuthStart(c *gin.Context) error {

	state, key, err := db.Db.GenerateAuthState()
	if err != nil {
		return ErrServerError
	}

	session := sessions.Default(c)
	session.Set(StateKey, key)
	if err := session.Save(); err != nil {
		return err
	}

	c.Redirect(http.StatusTemporaryRedirect, auth.AuthURL(state))

	return nil
}

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

func GetPlaylists(userId string) ([]*models.MoodPlaylist, error) {
	playlists, err := db.Db.GetMoodPlaylists(userId)
	if err != nil {
		return nil, ErrServerError
	}

	return playlists, nil
}

func GetPlaylist(userId string, date string) (*models.MoodPlaylist, error) {
	playlist, err := db.Db.GetMoodPlaylist(userId, date)
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

	if !userTracks.CompletedScan && len(userTracks.TrackIds) < 1000 {
		return fetchNextUserTracks(userId, client)
	}

	db.Db.SetUserFetchLock(userId)

	return nil
}

func feelNothingYet(mood models.Mood) bool {
	return mood > models.MoodNothing-models.Mood(0.05) && mood < models.MoodNothing+models.Mood(0.05)
}

func GenerateMoodPlaylist(userId string, client *spotify.Client, startMood models.Mood, date time.Time) (*models.MoodPlaylist, error) {
	if err := fetchNextUserTracks(userId, client); err != nil {
		return nil, err
	}

	userTracks, _ := db.Db.GetUserTracks(userId)

	ignoreTracks := make(map[string]interface{})
	{
		playlists, _ := db.Db.GetMoodPlaylitsBetweenDates(userId, date.Add(-(24 * 7 * time.Hour)), date)

		for _, playlist := range playlists {
			for _, track := range playlist.Tracks {
				ignoreTracks[track] = nil
			}
		}

	}

	type entry struct {
		id      string
		valence float32
	}
	entries := make([]*entry, 0)
	for id, valence := range userTracks.TrackIds {
		if _, ok := ignoreTracks[id]; ok {
			continue
		}

		entries = append(entries, &entry{
			id:      id,
			valence: valence,
		})
	}

	result := &models.MoodPlaylist{
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

	result.StartMood = float32(startMood)

	mood := startMood

	for len(selectedTracks) < 10 {
		if feelNothingYet(mood) {
			feelNothing = true
		}

		moodCategory := models.ValenceMoodCategory(float32(mood)).Opposite()

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

		nextMood := mood + (models.Mood(transformValence(entry.valence)) / 4)
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

func UpdatePlaylist(userId string, client *spotify.Client, date string) error {
	playlist, err := db.Db.GetMoodPlaylist(userId, date)
	if err != nil {
		return ErrNotFound
	}

	playlistId, err := db.Db.GetSpotifyPlaylist(userId)
	if err != nil {
		if err == badger.ErrKeyNotFound {
			resp, err := client.CreatePlaylistForUser(userId, "tune neutral", "Playlist for tune neutral", true)
			if err != nil {
				return ErrServerError
			}
			playlistId = string(resp.ID)
			db.Db.SetSpotifyPlaylist(userId, playlistId)
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

func RemoveTrackFromUser(userId string, trackId string) error {
	userTracks, err := db.Db.GetUserTracks(userId)
	if err != nil {
		return ErrNotFound
	}

	if userTracks.IgnoredTracks == nil {
		userTracks.IgnoredTracks = map[string]interface{}{}
	}
	userTracks.IgnoredTracks[trackId] = nil
	delete(userTracks.TrackIds, trackId)

	db.Db.SetUserTracks(userId, userTracks)

	return nil
}

func UnremoveTrackFromUser(userId string, trackId string) error {
	userTracks, err := db.Db.GetUserTracks(userId)
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

	track, err := db.Db.GetTrack(trackId)
	if err != nil {
		return ErrNotFound
	}
	userTracks.TrackIds[trackId] = track.Valence

	db.Db.SetUserTracks(userId, userTracks)

	return nil
}

func GetRemovedTracksForUser(userId string) ([]string, error) {
	userTracks, err := db.Db.GetUserTracks(userId)
	if err != nil {
		return nil, ErrNotFound
	}

	var trackIds []string

	for trackId := range userTracks.IgnoredTracks {
		trackIds = append(trackIds, trackId)
	}

	return trackIds, nil
}

func ClearUserData(userId string) error {
	db.Db.ClearUserTracks(userId)
	db.Db.ClearMoodPlaylists(userId)
	db.Db.ClearUserFetchLock(userId)

	return nil
}
