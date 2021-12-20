package router

import (
	"encoding/json"
	"fmt"
	"io/ioutil"
	"net/http"
	"os"
	"time"

	"github.com/gin-contrib/sessions"
	"github.com/gin-contrib/sessions/cookie"
	"github.com/gin-gonic/gin"
	"github.com/zmb3/spotify"

	"github.com/gin-contrib/static"
	"github.com/sardap/TuneNeutral/backend/pkg/api"
	"github.com/sardap/TuneNeutral/backend/pkg/config"
	"github.com/sardap/TuneNeutral/backend/pkg/db"
	"github.com/sardap/TuneNeutral/backend/pkg/models"
)

func DumbGetCookie(c *gin.Context, key string) string {
	result, err := c.Cookie(key)
	if err != nil {
		return ""
	}
	return result
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

type generateMoodPlaylistRequest struct {
	Mood float32 `json:"mood"`
	Date string  `json:"date"`
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

	_, err = api.GenerateMoodPlaylist(sessions.Default(c), models.Mood(request.Mood), date)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"result": generateMoodPlaylistResponse{},
	})
}

func addToQueueEndpoint(c *gin.Context) {
	err := api.AddToQueue(sessions.Default(c), c.Param("playlist_id"))
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"result": "success",
	})
}

var (
	errNotAuth = fmt.Errorf("not auth")
)

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

type basicPlaylist struct {
	Date      string  `json:"date"`
	Id        string  `json:"id"`
	StartMood float32 `json:"start_mood"`
}

type getPlaylistsResponse struct {
	Playlists []basicPlaylist `json:"playlists"`
}

func getMoodPlaylistsEndpoint(c *gin.Context) {
	userId, _, _ := getUser(c)

	playlists, err := api.GetPlaylists(userId)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{})
		return
	}

	response := getPlaylistsResponse{}
	for _, playlist := range playlists {
		basicPlaylist := basicPlaylist{
			Date:      playlist.Date.Format(time.RFC3339),
			Id:        playlist.Id,
			StartMood: playlist.StartMood,
		}

		response.Playlists = append(response.Playlists, basicPlaylist)
	}

	c.JSON(http.StatusOK, gin.H{
		"result": response,
	})
}

type getPlaylistResponse struct {
	Tracks    []basicTrack `json:"tracks"`
	StartMood float32      `json:"start_mood"`
}

func getMoodPlaylistEndpoint(c *gin.Context) {
	userId, _, _ := getUser(c)

	playlist, err := api.GetPlaylist(userId, c.Param("date"))
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{})
		return
	}

	response := getPlaylistResponse{
		StartMood: playlist.StartMood,
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

func authEndpoint(c *gin.Context) {
	api.AuthStart(c)
}

func logoutEndpoint(c *gin.Context) {
	session := sessions.Default(c)
	api.Logout(session)
	c.Redirect(http.StatusTemporaryRedirect, "/")
}

func authMiddleware(c *gin.Context) {
	if isAuthenticated(c) {
		c.Next()
	} else {
		c.JSON(http.StatusUnauthorized, gin.H{})
	}
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
		v1Authenticated.POST("/generate_mood_playlist", generateMoodPlaylistEndpoint)
		v1Authenticated.POST("/add_to_queue/:playlist_id", addToQueueEndpoint)
	}

	_, err := os.Stat(cfg.WebsiteFilesPath)
	if err != nil {
		panic(err)
	}

	r.Use(static.Serve("/", static.LocalFile(cfg.WebsiteFilesPath, false)))

	return r
}
