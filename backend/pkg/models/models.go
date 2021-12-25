package models

import (
	"math"
	"time"

	"github.com/zmb3/spotify"
)

const (
	DateFormat = "2006-01-02"
)

type Mood float32

const (
	MoodSuicidal Mood = -0.25
	MoodSad      Mood = -0.125
	MoodNothing  Mood = 0.00
	MoodGood     Mood = 0.125
	MoodHappy    Mood = 0.25
)

func (m Mood) Opposite() Mood {
	switch m {
	case MoodSuicidal:
		return MoodHappy
	case MoodSad:
		return MoodGood
	case MoodNothing:
		return MoodNothing
	case MoodGood:
		return MoodSad
	case MoodHappy:
		return MoodSuicidal
	}

	panic("fuck")
}

func ValenceMoodCategory(valence float32) Mood {
	moods := []Mood{
		MoodSuicidal, MoodSad,
		MoodNothing,
		MoodGood, MoodHappy,
	}

	bestDist := math.MaxFloat32
	closestMood := MoodSuicidal

	for _, mood := range moods {
		dist := math.Abs(float64(valence - float32(mood)))
		if dist < bestDist {
			bestDist = dist
			closestMood = mood
		}
	}

	return closestMood
}

type Track struct {
	Id               string
	Name             string
	Valence          float32
	AvailableMarkets map[string]error
	AlbumId          string
	AlbumArtUrl      string
	Artists          []spotify.SimpleArtist
}

type UserTracks struct {
	UserId           string
	LastOffset       int
	TrackIds         map[string]float32
	IgnoredTracks    map[string]interface{}
	CompletedScan    bool
	LastTrackScanned *string
}

type MoodPlaylist struct {
	Date      time.Time
	Tracks    []string
	StartMood float32
	EndMood   float32
	Note      *string
}

type SpotifyRedirect struct {
	Token          string
	RedirectTarget string
}
