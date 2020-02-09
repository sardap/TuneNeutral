package com.example.tuneneutral

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.tuneneutral.database.DatabaseManager
import com.example.tuneneutral.database.TrackInfo
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.lang.RuntimeException
import java.text.DateFormat.getDateInstance
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.round


class GenrateNeutralisedPlaylist(private val mSpotifyAccessToken: String, private val mCurrentValence: Float, private val mContext: Context) : Runnable {

    private var mCall: Call? = null
    private val mOkHttpClient = OkHttpClient()

    override fun run() {
        val tracks = getNTracksWithValence(mCurrentValence)

        if(tracks.count() > 0) {
            tracks.reverse()

            notifyObvs(NeutralisePlaylistMessage.Calcualting)
            val playlistID = createPlaylist(getCurrentUserID()!!)

            if(playlistID != null) {
                addTracksToPlaylist(playlistID, tracks)
                notifyComplete(playlistID)
                return
            }
        }

        notifyObvs(NeutralisePlaylistMessage.CompleteNoPlaylist)
    }

    private fun notifyObvs(message: NeutralisePlaylistMessage) {
        val lbm = LocalBroadcastManager.getInstance(mContext)

        val intent = Intent(MiscConsts.NEUTRALISE_PLAYLIST_MESSAGE)
        intent.putExtra(MiscConsts.NEUTRALISE_PLAYLIST_MESSAGE, message)

        lbm.sendBroadcast(intent)
    }

    private fun notifyComplete(playlistID: String) {
        val lbm = LocalBroadcastManager.getInstance(mContext)

        val intent = Intent(MiscConsts.NEUTRALISE_PLAYLIST_MESSAGE)
        intent.putExtra(MiscConsts.NEUTRALISE_PLAYLIST_MESSAGE, NeutralisePlaylistMessage.CompletePlaylistCreated)
        intent.putExtra(MiscConsts.NEUTRALISE_PLAYLIST_ID, playlistID)

        lbm.sendBroadcast(intent)
    }


    private fun Double.round(decimals: Int): Double {
        var multiplier = 1.0
        repeat(decimals) { multiplier *= 10 }
        return round(this * multiplier) / multiplier
    }

    private fun scaleValence(valenece: Float): Float {
        return (valenece - 0.5f) / 8
    }

    private fun getValidTracks(allTracks: HashMap<String, Float>, result: ArrayList<String>, currentValence: Float): ArrayList<String> {
        if(allTracks.count() == 0 || (currentValence in 0.49f..0.51f)) {
            return result
        }

        val toRemove = ArrayList<String>()

        // Fuck it
        for(track in allTracks) {
            val nextValence = currentValence + scaleValence(track.value)
            if(
                (currentValence < 0.5f && (nextValence > 0.5f || nextValence < 0f || nextValence < currentValence)) ||
                (currentValence > 0.5f && (nextValence < 0.5f || nextValence > 1f || nextValence > currentValence))
            ) {
                toRemove.add(track.key)
            }
        }

        toRemove.forEach { allTracks.remove(it) }

        val newAllTracks = allTracks.toList().sortedBy { (_, value) -> value }.toMap()
        val index = if(currentValence < 0.5f)  newAllTracks.keys.size - 1  else 0
        val bestKey = newAllTracks.keys.toTypedArray()[index]

        result.add(bestKey)

        val nextValence = currentValence + scaleValence(allTracks[bestKey]!!)

        allTracks.remove(bestKey)

        return getValidTracks(allTracks, result, nextValence)
    }

    private fun getNTracksWithValence(valence: Float) : ArrayList<String> {
        val trackCandidates = HashMap<String, Float>()
        val existingTracks = ArrayList(DatabaseManager.instance.getAllTrackIds())

        var offset = 0

        notifyObvs(NeutralisePlaylistMessage.PullingSongs)

        val targetNewTracks = 10
        val newTracks = ArrayList<String>()

        while (newTracks.count() < targetNewTracks && offset < 100) {
            newTracks.addAll(getTopTracks(offset).minus(existingTracks))
            offset += 20
        }

        notifyObvs(NeutralisePlaylistMessage.Anaylsing)

        // All new tracks must be added as candidates
        for (track in newTracks) {
            getTrackAnaysis(track)?.let {
                trackCandidates[track] = it.valence.round(2).toFloat()
            }
        }

        // old tracks are shuffled and added randomly to the canidates
        existingTracks.shuffle()
        while (trackCandidates.count() < 50 && existingTracks.count() > 0) {
            val nextTrack = DatabaseManager.instance.getTrackInfo(existingTracks.first())
            if(nextTrack != null){
                trackCandidates[nextTrack.tackId] = nextTrack.valence.round(2).toFloat()
            }
            existingTracks.remove(existingTracks.first())
        }

        Log.d("Status: ", "Tracks Gotten: $trackCandidates")

        val result = getValidTracks(trackCandidates, ArrayList(), valence)
        Log.d("Status: ", "Neutalize tracks: $result")

        return result
    }

    private fun getCurrentUserID(): String? {
        var result: String? = null

        val request = Request.Builder()
            .url("https://api.spotify.com/v1/me")
            .addHeader("Authorization", "Bearer " + mSpotifyAccessToken)
            .build()

        cancelCall()
        val response = mOkHttpClient.newCall(request).execute()

        if(response.isSuccessful) {
            try {
                result = JSONObject(response.body!!.string()).getString("id")
                Log.d("Status: ", "Got UserID: $result")
            } catch (e: JSONException) {
                Log.d("Status: ", "Failed to get user: $e")
            }
        } else {
            Log.d("Status: ", "Failed to get user")
        }

        return result

    }

    private fun createPlaylist(userID: String): String? {
        val c: Date = Calendar.getInstance().time
        val df = getDateInstance()
        val formattedDate = df.format(c)

        val desc = mContext.getString(R.string.playlist_desc, (mCurrentValence * 100).toInt())

        val json = "{ \"name\": \"${formattedDate}\", \"description\": \"${desc}\", \"public\":false }"

        val requestBody = json.toRequestBody()

        val request = Request.Builder()
            .url("https://api.spotify.com/v1/users/${userID}/playlists")
            .addHeader("Authorization", "Bearer $mSpotifyAccessToken")
            .post(requestBody)
            .build()

        cancelCall()
        val response = mOkHttpClient.newCall(request).execute()

        if(response.isSuccessful) {
            try {
                val result = JSONObject(response.body!!.string()).getString("id")
                Log.d("Status: ", "playlist Created! ID:$result")
                return result
            } catch (e: JSONException) {
                Log.d("Status: ", "Failed to create playlist: $e")
            }
        } else {
            Log.d("Status: ", "Failed to create playlist: $response")
        }

        return null
    }

    private fun addTracksToPlaylist(playlistID: String, tracks: ArrayList<String>): Boolean {
        var tracksString = ""

        for(track in tracks) {
            tracksString += "spotify:track:${track},"
        }

        val request = Request.Builder()
            .url("https://api.spotify.com/v1/playlists/${playlistID}/tracks?uris=${tracksString}")
            .addHeader("Authorization", "Bearer $mSpotifyAccessToken")
            .post("".toRequestBody())
            .build()

        cancelCall()
        val response = mOkHttpClient.newCall(request).execute()

        if(response.isSuccessful) {
            try {
                Log.d("Status: ", "tracks added")
                return true
            } catch (e: JSONException) {
                Log.d("Status: ", "Failed to add tracks: $e")
            }
        } else {
            Log.d("Status: ", "Failed to add tracks")
        }

        return false
    }


    private fun getTrackAnaysis(id: String): TrackInfo? {
        var result: TrackInfo? = null

        val request = Request.Builder()
            .url(String.format("https://api.spotify.com/v1/audio-features/%s", id))
            .addHeader("Authorization", "Bearer " + mSpotifyAccessToken!!)
            .build()

        cancelCall()
        val response = mOkHttpClient.newCall(request).execute()

        if(response.isSuccessful) {
            try {
                result = TrackInfo(
                    id,
                    JSONObject(response.body!!.string())
                )
                DatabaseManager.instance.addTrackInfo(result)
                Log.d("Status: ", "Got new analysis: $result")
            } catch (e: JSONException) {
                Log.d("Status: ", "Failed to parse data: $e")
            }
        } else {
            Log.d("Status: ", "Failed to fetch data")
        }

        return result
    }

    private fun getTopTracks(offset: Int): ArrayList<String> {
        var result = ArrayList<String>()

        val request = Request.Builder()
            .url(String.format("https://api.spotify.com/v1/me/top/tracks?time_range=short_term&offset=%d&limit=20", offset))
            .addHeader("Authorization", "Bearer " + mSpotifyAccessToken!!)
            .build()

        cancelCall()
        val response = mOkHttpClient.newCall(request).execute()
        if(response.isSuccessful) {
            try {
                val jsonObject = JSONObject(response.body!!.string())

                val topTracksUris = ArrayList<String>()

                val tracks = jsonObject.getJSONArray("items")

                for (i in 0 until tracks.length()) {
                    topTracksUris.add(tracks.getJSONObject(i).getString("id"))
                }

                Log.d("top tracks gotten :", topTracksUris.toString())

                result = topTracksUris

            } catch (e: JSONException) {
                Log.d("Status: ", "Failed to parse data: $e")
            }
        } else {
            Log.d("Status: ", "Failed to fetch data")
        }

        return result
    }

    private fun cancelCall() {
        if (mCall != null) {
            mCall!!.cancel()
        }
    }
}