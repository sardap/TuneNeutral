package router

import (
	"encoding/json"
	"errors"
	"fmt"
	"io/ioutil"
	"log"
	"net/http"
	"os"
	"time"

	"github.com/gin-contrib/sessions"
	"github.com/gin-contrib/sessions/cookie"
	"github.com/gin-gonic/gin"
	uuid "github.com/nu7hatch/gouuid"
	"github.com/zmb3/spotify"

	"github.com/gin-contrib/static"
	"github.com/sardap/TuneNeutral/backend/pkg/api"
	"github.com/sardap/TuneNeutral/backend/pkg/config"
	"github.com/sardap/TuneNeutral/backend/pkg/db"
	"github.com/sardap/TuneNeutral/backend/pkg/models"
)

var (
	errNotAuth = fmt.Errorf("not auth")
)

func processApiError(c *gin.Context, err error) {
	if errors.Is(err, api.ErrNotFound) {
		c.JSON(http.StatusNotFound, gin.H{})
	} else {
		id, _ := uuid.NewV4()
		log.Printf("Internal server error(%s): %v", id.String(), errors.Unwrap(err))
		c.JSON(http.StatusInternalServerError, gin.H{
			"reference_code": id.String(),
		})
	}
}

func authEndpoint(c *gin.Context) {
	if c.Query("terms_and_conditions") != "true" {
		c.JSON(http.StatusUnauthorized, gin.H{})
		return
	}

	api.AuthStart(c)
}

func logoutEndpoint(c *gin.Context) {
	session := sessions.Default(c)
	api.Logout(session)
	c.Redirect(http.StatusTemporaryRedirect, "/")
}

func getToken(session sessions.Session) ([]byte, error) {
	result, ok := session.Get(api.TokenKey).([]byte)
	if !ok || len(result) <= 0 {
		return nil, errNotAuth
	}
	return result, nil
}

func getUser(c *gin.Context) (string, *spotify.Client, error) {
	session := sessions.Default(c)

	token, err := getToken(session)
	if err != nil {
		return "", nil, err
	}

	client, err := api.GetClientFromToken([]byte(token))
	if err != nil {
		return "", nil, err
	}

	userId, ok := session.Get(api.Userkey).(string)
	if !ok || len(userId) <= 0 {
		usr, err := client.CurrentUser()
		if err != nil {
			return "", nil, err
		}

		session.Set(api.Userkey, usr.ID)
		session.Save()

		return usr.ID, &client, nil
	}

	return userId, &client, nil

}

func isAuthenticated(c *gin.Context) bool {
	session := sessions.Default(c)

	_, err := getToken(session)

	return err == nil
}

func authenticatedEndpoint(c *gin.Context) {
	if isAuthenticated(c) {
		c.JSON(http.StatusOK, gin.H{})
	} else {
		c.JSON(http.StatusUnauthorized, gin.H{})
	}
}

func authMiddleware(c *gin.Context) {
	if isAuthenticated(c) {
		c.Next()
	} else {
		c.JSON(http.StatusUnauthorized, gin.H{})
	}
}

type basicPlaylist struct {
	Date      string  `json:"date"`
	StartMood float32 `json:"start_mood"`
	Note      *string `json:"note"`
}

type getPlaylistsResponse struct {
	Playlists []basicPlaylist `json:"playlists"`
}

func getMoodPlaylistsEndpoint(c *gin.Context) {
	userId, _, _ := getUser(c)

	playlists, err := api.GetPlaylists(userId)
	if err != nil {
		processApiError(c, err)
		return
	}

	response := getPlaylistsResponse{}
	for _, playlist := range playlists {
		basicPlaylist := basicPlaylist{
			Date:      playlist.Date.Format(time.RFC3339),
			StartMood: playlist.StartMood,
			Note:      playlist.Note,
		}

		response.Playlists = append(response.Playlists, basicPlaylist)
	}

	c.JSON(http.StatusOK, gin.H{
		"result": response,
	})
}

type IdNamePair struct {
	Name string `json:"name"`
	Id   string `json:"id"`
}

type basicAlbum struct {
	IdNamePair
	Url string `json:"url"`
}

type basicTrack struct {
	IdNamePair
	Mood    float32      `json:"mood"`
	Album   basicAlbum   `json:"album"`
	Artists []IdNamePair `json:"artists"`
}

type getPlaylistResponse struct {
	Tracks    []basicTrack `json:"tracks"`
	StartMood float32      `json:"start_mood"`
	Note      *string      `json:"note"`
}

func getMoodPlaylistEndpoint(c *gin.Context) {
	userId, _, _ := getUser(c)

	playlist, err := api.GetPlaylist(userId, c.Param("date"))
	if err != nil {
		processApiError(c, err)
		return
	}

	response := getPlaylistResponse{
		StartMood: playlist.StartMood,
		Note:      playlist.Note,
	}
	for _, track := range playlist.Tracks {
		track, err := db.Db.GetTrack(track)
		if err != nil {
			continue
		}
		basicTrack := basicTrack{
			IdNamePair: IdNamePair{
				Name: track.Name,
				Id:   track.Id,
			},
			Mood: float32(models.ValenceMoodCategory(track.Valence - 0.5)),
			Album: basicAlbum{
				IdNamePair: IdNamePair{
					Name: track.AlbumId,
					Id:   track.AlbumId,
				},
				Url: track.AlbumArtUrl,
			},
		}

		for _, artist := range track.Artists {
			basicTrack.Artists = append(basicTrack.Artists, IdNamePair{
				Name: artist.Name,
				Id:   string(artist.ID),
			})
		}

		response.Tracks = append(response.Tracks, basicTrack)
	}

	c.JSON(http.StatusOK, gin.H{
		"result": response,
	})
}

type getRemovedTracksResponse struct {
	Tracks []basicTrack `json:"tracks"`
}

func getRemovedTracksEndpoint(c *gin.Context) {
	userId, _, _ := getUser(c)

	tracks, err := api.GetRemovedTracksForUser(userId)
	if err != nil {
		processApiError(c, err)
		return
	}

	response := getRemovedTracksResponse{}
	for _, track := range tracks {
		track, err := db.Db.GetTrack(track)
		if err != nil {
			continue
		}
		basicTrack := basicTrack{
			IdNamePair: IdNamePair{
				Name: track.Name,
				Id:   track.Id,
			},
			Mood: float32(models.ValenceMoodCategory(track.Valence - 0.5)),
			Album: basicAlbum{
				IdNamePair: IdNamePair{
					Name: track.AlbumId,
					Id:   track.AlbumId,
				},
				Url: track.AlbumArtUrl,
			},
		}

		for _, artist := range track.Artists {
			basicTrack.Artists = append(basicTrack.Artists, IdNamePair{
				Name: artist.Name,
				Id:   string(artist.ID),
			})
		}

		response.Tracks = append(response.Tracks, basicTrack)
	}

	c.JSON(http.StatusOK, gin.H{
		"result": response,
	})
}

type getSpotifyPlaylist struct {
	Id string `json:"id"`
}

func getSpotifyPlaylistEndpoint(c *gin.Context) {
	userId, _, _ := getUser(c)

	playlistId, _ := db.Db.GetSpotifyPlaylist(userId)

	response := getSpotifyPlaylist{
		Id: playlistId,
	}

	c.JSON(http.StatusOK, gin.H{
		"result": response,
	})
}

type getAllDataResponse struct {
	UserTracks    *models.UserTracks
	MoodPlaylists []*models.MoodPlaylist
	FetchLocked   bool
}

func getAllData(c *gin.Context) {
	userId, _, _ := getUser(c)

	userTracks, _ := db.Db.GetUserTracks(userId)
	moodPlaylist, _ := db.Db.GetMoodPlaylists(userId)

	response := getAllDataResponse{
		UserTracks:    userTracks,
		MoodPlaylists: moodPlaylist,
		FetchLocked:   db.Db.UserFetchLocked(userId),
	}

	c.JSON(http.StatusOK, gin.H{
		"result": response,
	})
}

type generateMoodPlaylistRequest struct {
	Mood float32 `json:"mood"`
	Date string  `json:"date"`
	Note string  `json:"note"`
}

type generateMoodPlaylistResponse struct {
}

func generateMoodPlaylistEndpoint(c *gin.Context) {
	var request generateMoodPlaylistRequest
	jsonData, _ := ioutil.ReadAll(c.Request.Body)
	json.Unmarshal(jsonData, &request)

	if request.Date == "" {
		request.Date = time.Now().Format(time.RFC3339)
	}

	date, err := time.Parse("2006-01-02", request.Date)
	if err != nil {
		c.JSON(http.StatusOK, gin.H{
			"error": "invalid date",
		})
		return
	}

	userId, client, _ := getUser(c)

	_, err = api.GenerateMoodPlaylist(userId, client, models.Mood(request.Mood), date, request.Note)
	if err != nil {
		processApiError(c, err)
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"result": generateMoodPlaylistResponse{},
	})
}

func updateTunePlaylistEndpoint(c *gin.Context) {
	userId, client, _ := getUser(c)

	err := api.UpdatePlaylist(userId, client, c.Param("playlist_id"))
	if err != nil {
		processApiError(c, err)
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"result": "success",
	})
}

func removeTrackEndpoint(c *gin.Context) {
	userId, _, _ := getUser(c)

	err := api.RemoveTrackFromUser(userId, c.Param("track_id"))
	if err != nil {
		processApiError(c, err)
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"result": "success",
	})
}

func unremoveTrackEndpoint(c *gin.Context) {
	userId, _, _ := getUser(c)

	err := api.UnremoveTrackFromUser(userId, c.Param("track_id"))
	if err != nil {
		processApiError(c, err)
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"result": "success",
	})
}

func removeAllUserData(c *gin.Context) {
	userId, _, _ := getUser(c)

	api.ClearUserData(userId)
	api.Logout(sessions.Default(c))

	c.JSON(http.StatusOK, gin.H{
		"result": "success",
	})
}

func CreateRouter(cfg *config.Config) *gin.Engine {
	r := gin.Default()

	store := cookie.NewStore([]byte(cfg.CookieAuthSecert), []byte(cfg.CookieEyncSecert))
	r.Use(sessions.Sessions("tune", store))

	r.GET("/callback", api.RedirectEndpoint)
	r.GET("/auth", authEndpoint)
	r.GET("/logout", logoutEndpoint)

	v1 := r.Group("/v1/api")
	{
		v1.GET("/authenticated", authenticatedEndpoint)
	}

	v1Authenticated := r.Group("/v1/api")
	v1Authenticated.Use(authMiddleware)
	{
		v1Authenticated.GET("/mood_playlists", getMoodPlaylistsEndpoint)
		v1Authenticated.GET("/mood_playlist/:date", getMoodPlaylistEndpoint)
		v1Authenticated.GET("/removed_tracks", getRemovedTracksEndpoint)
		v1Authenticated.GET("/spotify_playlist", getSpotifyPlaylistEndpoint)
		v1Authenticated.GET("/all_data", getAllData)
		v1Authenticated.POST("/generate_mood_playlist", generateMoodPlaylistEndpoint)
		v1Authenticated.POST("/update_playlist/:playlist_id", updateTunePlaylistEndpoint)
		v1Authenticated.POST("/remove_track/:track_id", removeTrackEndpoint)
		v1Authenticated.POST("/unremove_track/:track_id", unremoveTrackEndpoint)
		v1Authenticated.DELETE("/remove_all_user_data", removeAllUserData)
	}

	_, err := os.Stat(cfg.WebsiteFilesPath)
	if err != nil {
		panic(err)
	}

	r.Use(static.Serve("/", static.LocalFile(cfg.WebsiteFilesPath, false)))

	return r
}
