package db

import (
	"bytes"
	"encoding/gob"
	"fmt"
	"log"
	"strings"
	"time"

	"github.com/dgraph-io/badger/v3"
	uuid "github.com/nu7hatch/gouuid"
	"github.com/sardap/TuneNeutral/backend/pkg/config"
	"github.com/sardap/TuneNeutral/backend/pkg/models"
)

type Database struct {
	db *badger.DB
}

func (d *Database) Close() {
	d.db.Close()
}

func trackKey(trackId string) []byte {
	return []byte(fmt.Sprintf("tracks/%s", trackId))
}

func (d *Database) PutTrack(track *models.Track) error {
	return d.db.Update(func(txn *badger.Txn) error {
		buf := &bytes.Buffer{}
		enc := gob.NewEncoder(buf)
		enc.Encode(track)
		txn.Set(trackKey(track.Id), buf.Bytes())
		return nil
	})
}

func (d *Database) GetTrack(id string) (track *models.Track, err error) {
	err = d.db.View(func(txn *badger.Txn) error {
		itm, err := txn.Get(trackKey(id))
		if err != nil {
			return err
		}
		return itm.Value(func(val []byte) error {
			enc := gob.NewDecoder(bytes.NewBuffer(val))
			return enc.Decode(&track)
		})
	})
	return
}

func (d *Database) TrackExists(id string) (result bool) {
	result = false
	d.db.View(func(txn *badger.Txn) error {
		_, err := txn.Get(trackKey(id))
		if err == nil {
			result = true
		}
		return nil
	})
	return result
}

func (d *Database) GetTracks(ids ...string) (tracks []*models.Track) {
	d.db.View(func(txn *badger.Txn) error {
		for _, id := range ids {
			itm, err := txn.Get(trackKey(id))
			if err != nil {
				continue
			}
			itm.Value(func(val []byte) error {
				var track *models.Track
				enc := gob.NewDecoder(bytes.NewBuffer(val))
				enc.Decode(&track)
				tracks = append(tracks, track)
				return nil
			})
		}
		return nil
	})
	return
}

func userTracksKey(userId string) []byte {
	return []byte(fmt.Sprintf("user/tracks/%s", userId))
}

func (d *Database) SetUserTracks(userId string, userTracks *models.UserTracks) error {
	return d.db.Update(func(txn *badger.Txn) error {
		buf := &bytes.Buffer{}
		enc := gob.NewEncoder(buf)
		err := enc.Encode(userTracks)
		if err != nil {
			panic(err)
		}
		return txn.Set(userTracksKey(userId), buf.Bytes())
	})
}

func (d *Database) GetUserTracks(userId string) (tracks *models.UserTracks, err error) {
	err = d.db.View(func(txn *badger.Txn) error {
		itm, err := txn.Get(userTracksKey(userId))
		if err != nil {
			return err
		}
		return itm.Value(func(val []byte) error {
			enc := gob.NewDecoder(bytes.NewBuffer(val))
			return enc.Decode(&tracks)
		})
	})
	return
}

func (d *Database) ClearUserTracks(userId string) error {
	return d.db.Update(func(txn *badger.Txn) error {
		return txn.Delete(userTracksKey(userId))
	})
}

func userFetchLock(userId string) []byte {
	return []byte(fmt.Sprintf("user/fetch_lock/%s", userId))
}

func (d *Database) SetUserFetchLock(userId string) error {
	return d.db.Update(func(txn *badger.Txn) error {
		entry := badger.NewEntry(userFetchLock(userId), []byte{1})
		entry.ExpiresAt = uint64(time.Now().Add(45 * time.Minute).Unix())
		return txn.SetEntry(entry)
	})
}

func (d *Database) UserFetchLocked(userId string) (result bool) {
	d.db.View(func(txn *badger.Txn) error {
		_, err := txn.Get(userFetchLock(userId))
		if err != badger.ErrKeyNotFound {
			result = true
		}
		return nil
	})
	return
}

func (d *Database) ClearUserFetchLock(userId string) error {
	return d.db.Update(func(txn *badger.Txn) error {
		return txn.Delete(userFetchLock(userId))
	})
}

func keyMoodPlaylistPrefix(userId string) []byte {
	return []byte(fmt.Sprintf("user/playlist/%s", userId))

}

func keyMoodPlaylist(userId, date string) []byte {
	return []byte(fmt.Sprintf("%s/%s", keyMoodPlaylistPrefix(userId), date))
}

func (d *Database) SetMoodPlaylist(userId string, playlist *models.MoodPlaylist) error {
	return d.db.Update(func(txn *badger.Txn) error {
		buf := &bytes.Buffer{}
		enc := gob.NewEncoder(buf)
		err := enc.Encode(playlist)
		if err != nil {
			panic(err)
		}
		return txn.Set(keyMoodPlaylist(userId, playlist.Date.Format(models.DateFormat)), buf.Bytes())
	})
}

func (d *Database) GetMoodPlaylist(userId, date string) (playlist *models.MoodPlaylist, err error) {
	err = d.db.View(func(txn *badger.Txn) error {
		itm, err := txn.Get(keyMoodPlaylist(userId, date))
		if err != nil {
			return err
		}
		return itm.Value(func(val []byte) error {
			enc := gob.NewDecoder(bytes.NewBuffer(val))
			return enc.Decode(&playlist)
		})
	})
	return
}

func (d *Database) GetMoodPlaylitsBetweenDates(userId string, start, end time.Time) (playlists []*models.MoodPlaylist, err error) {
	err = d.db.View(func(txn *badger.Txn) error {
		it := txn.NewIterator(badger.DefaultIteratorOptions)
		defer it.Close()

		prefix := keyMoodPlaylistPrefix(userId)

		getDate := func(v []byte) time.Time {
			result, _ := time.Parse(models.DateFormat, strings.Split(string(v), "/")[3])
			return result
		}

		for it.Seek(prefix); it.ValidForPrefix(prefix); it.Next() {
			date := getDate(it.Item().Key())
			if (date.After(start) && date.Before(end)) || date == start || date == end {
				it.Item().Value(func(val []byte) error {
					enc := gob.NewDecoder(bytes.NewBuffer(val))
					var playlist models.MoodPlaylist
					enc.Decode(&playlist)
					playlists = append(playlists, &playlist)
					return nil
				})
			}
		}

		return nil
	})
	return
}

func (d *Database) GetMoodPlaylists(userId string) (playlists []*models.MoodPlaylist, err error) {
	err = d.db.View(func(txn *badger.Txn) error {
		it := txn.NewIterator(badger.DefaultIteratorOptions)
		defer it.Close()

		prefix := keyMoodPlaylistPrefix(userId)

		for it.Seek(prefix); it.ValidForPrefix(prefix); it.Next() {
			it.Item().Value(func(val []byte) error {
				enc := gob.NewDecoder(bytes.NewBuffer(val))
				var playlist *models.MoodPlaylist
				enc.Decode(&playlist)
				playlists = append(playlists, playlist)
				return nil
			})
		}
		return nil
	})
	return
}

func (d *Database) ClearMoodPlaylists(userId string) error {
	return d.db.Update(func(txn *badger.Txn) error {
		it := txn.NewIterator(badger.DefaultIteratorOptions)
		defer it.Close()

		prefix := keyMoodPlaylistPrefix(userId)

		for it.Seek(prefix); it.ValidForPrefix(prefix); it.Next() {
			txn.Delete(it.Item().Key())
		}
		return nil
	})
}

func keySpotifyPlaylist(userId string) []byte {
	return []byte(fmt.Sprintf("user/spotify_playlist/%s", userId))
}

func (d *Database) GetSpotifyPlaylist(userId string) (playlistId string, err error) {
	err = d.db.View(func(txn *badger.Txn) error {
		itm, err := txn.Get(keySpotifyPlaylist(userId))
		if err != nil {
			return err
		}

		data, _ := itm.ValueCopy(nil)
		playlistId = string(data)
		return nil
	})
	return
}

func (d *Database) SetSpotifyPlaylist(userId, playlistId string) error {
	return d.db.Update(func(txn *badger.Txn) error {
		return txn.Set(keySpotifyPlaylist(userId), []byte(playlistId))
	})
}

func authStateKey(id string) []byte {
	return []byte(fmt.Sprintf("auth/states/%s", id))
}

func (d *Database) GenerateAuthState() (state, key string, err error) {
	d.db.Update(func(txn *badger.Txn) error {
		{
			id, _ := uuid.NewV4()
			state = id.String()
		}
		{
			id, _ := uuid.NewV4()
			key = id.String()
		}

		entry := badger.NewEntry(authStateKey(state), []byte(key))
		entry.WithTTL(3 * time.Minute)
		err = txn.SetEntry(entry)
		return nil
	})
	return
}

func (d *Database) GetAuthState(state string) (result string, err error) {
	err = d.db.View(func(txn *badger.Txn) error {
		itm, err := txn.Get(authStateKey(state))
		if err != nil {
			return err
		}

		data, _ := itm.ValueCopy(nil)
		result = string(data)
		return nil
	})
	if err != nil {
		go d.db.Update(func(txn *badger.Txn) error {
			return txn.Delete(authStateKey(state))
		})
	}
	return
}

func ipKey(ip string) []byte {
	return []byte(fmt.Sprintf("bad/ips/%s", ip))
}

func (d *Database) SetBadIp(ip string, expire time.Duration) error {
	return d.db.Update(func(txn *badger.Txn) error {
		entry := badger.NewEntry(ipKey(ip), []byte{})
		entry.ExpiresAt = uint64(time.Now().Add(expire).Unix())
		return txn.SetEntry(entry)
	})
}

func (d *Database) IsIpGood(ip string) (result bool) {
	d.db.View(func(txn *badger.Txn) error {
		_, err := txn.Get(ipKey(ip))
		result = err == badger.ErrKeyNotFound
		return nil
	})
	return
}

func ConnectDb(cfg *config.Config) *Database {
	// Open the Badger database located in the /tmp/badger directory.
	// It will be created if it doesn't exist.
	db, err := badger.Open(badger.DefaultOptions(cfg.DatabasePath))
	if err != nil {
		log.Fatal(err)
	}
	return &Database{
		db: db,
	}
}
