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
	MoodDepressed Mood = -0.25
	MoodSad       Mood = -0.125
	MoodNothing   Mood = 0.00
	MoodGood      Mood = 0.125
	MoodHappy     Mood = 0.25
)

func (m Mood) Opposite() Mood {
	switch m {
	case MoodDepressed:
		return MoodHappy
	case MoodSad:
		return MoodGood
	case MoodNothing:
		return MoodNothing
	case MoodGood:
		return MoodSad
	case MoodHappy:
		return MoodDepressed
	}

	panic("Not Implemented")
}

func ValenceMoodCategory(valence float32) Mood {
	moods := []Mood{
		MoodDepressed, MoodSad,
		MoodNothing,
		MoodGood, MoodHappy,
	}

	return GetCategory(valence, moods)
}

type Energy float32

const (
	EnergyDepressed Energy = -0.25
	EnergySad       Energy = -0.125
	EnergyNothing   Energy = 0.00
	EnergyGood      Energy = 0.125
	EnergyHappy     Energy = 0.25
)

func (m Energy) Opposite() Energy {
	switch m {
	case EnergyDepressed:
		return EnergyHappy
	case EnergySad:
		return EnergyGood
	case EnergyNothing:
		return EnergyNothing
	case EnergyGood:
		return EnergySad
	case EnergyHappy:
		return EnergyDepressed
	}

	panic("Not Implemented")
}

func EnergyMoodCategory(energy float32) Energy {
	energies := []Energy{
		EnergyDepressed, EnergySad,
		EnergyNothing,
		EnergyGood, EnergyHappy,
	}

	return GetCategory(energy, energies)
}

type Category interface {
	Energy | Mood
}

func GetCategory[T Category](val float32, values []T) T {
	bestDist := math.MaxFloat32
	closestVal := values[0]

	for _, other := range values {
		dist := math.Abs(float64(val - float32(other)))
		if dist < bestDist {
			bestDist = dist
			closestVal = other
		}
	}

	return closestVal
}

type Track struct {
	Id               string
	Name             string
	Valence          float32
	Energy           float32
	AvailableMarkets map[string]error
	AlbumId          string
	AlbumArtUrl      string
	Artists          []spotify.SimpleArtist
}

type MinTrack struct {
	Valence float32
	Energy  float32
}

type UserTracks struct {
	UserId           string
	LastOffset       int
	TrackIds         map[string]MinTrack
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
