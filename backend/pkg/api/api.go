package api

import (
	"crypto"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"math/rand"
	"net/http"
	"sort"
	"strings"
	"time"

	"github.com/gin-contrib/sessions"
	"github.com/gin-gonic/gin"

	"github.com/dgraph-io/badger/v3"
	uuid "github.com/nu7hatch/gouuid"
	"github.com/sardap/TuneNeutral/backend/pkg/config"
	"github.com/sardap/TuneNeutral/backend/pkg/db"
	"github.com/sardap/TuneNeutral/backend/pkg/models"
	"github.com/zmb3/spotify"
	"golang.org/x/oauth2"
)

var (
	ErrInvalidLogin = fmt.Errorf("invalid login")
	ErrNotLoggedIn  = fmt.Errorf("not logged in")
	ErrServerError  = fmt.Errorf("internal server error")
	ErrNotFound     = fmt.Errorf("not found")
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
		spotify.ScopeUserReadCurrentlyPlaying,
		spotify.ScopeUserReadPlaybackState,
		spotify.ScopeUserModifyPlaybackState,
		spotify.ScopeUserTopRead,
		spotify.ScopeStreaming,
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

func getToken(session sessions.Session) ([]byte, error) {
	result, ok := session.Get(TokenKey).([]byte)
	if !ok || len(result) <= 0 {
		return nil, ErrInvalidLogin
	}
	return result, nil
}

func getClient(session sessions.Session) (string, *spotify.Client, error) {
	token, err := getToken(session)
	if err != nil {
		return "", nil, err
	}

	client, err := GetClientFromToken([]byte(token))
	if err != nil {
		return "", nil, err
	}

	userId, ok := session.Get(Userkey).(string)
	if !ok || len(userId) <= 0 {
		usr, err := client.CurrentUser()
		if err != nil {
			return "", nil, err
		}

		session.Set(Userkey, usr.ID)
		session.Save()

		return usr.ID, &client, nil
	}

	return userId, &client, nil
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

func hashPassword(plainPassword string) string {
	hash := crypto.SHA3_512.New()
	return string(hash.Sum([]byte(plainPassword)))
}

func checkPasswordValid(plainPassword string) error {
	if strings.Trim(plainPassword, " ") == "" {
		return fmt.Errorf("bad password")
	}

	if len(plainPassword) < 8 {
		return fmt.Errorf("password must be at least 8 long")
	}

	return nil
}

func checkUsernameAndPassword(username, plainPassword string) error {
	if err := checkPasswordValid(plainPassword); err != nil {
		return err
	}

	if strings.Trim(username, " ") == "" || strings.Trim(plainPassword, " ") == "" {
		return ErrInvalidLogin
	}

	return nil
}

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

	if !userTracks.CompletedScan && len(userTracks.TrackIds) < 100 {
		return fetchNextUserTracks(userId, client)
	}

	db.Db.SetUserFetchLock(userId)

	return nil
}

func feelNothingYet(mood models.Mood) bool {
	return mood > models.MoodNothing-models.Mood(0.05) && mood < models.MoodNothing+models.Mood(0.05)
}

func GenerateMoodPlaylist(session sessions.Session, startMood models.Mood, date time.Time) (*models.MoodPlaylist, error) {
	userId, client, err := getClient(session)
	if err != nil {
		return nil, err
	}

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

func AddToQueue(session sessions.Session, playlistId string) error {
	userId, client, err := getClient(session)
	if err != nil {
		return err
	}

	playlist, err := db.Db.GetMoodPlaylist(userId, playlistId)
	if err != nil {
		return ErrNotFound
	}

	for _, trackId := range playlist.Tracks {
		if err := client.QueueSong(spotify.ID(trackId)); err != nil {
			return err
		}
	}

	client.Play()

	return nil
}
