package com.example.tuneneutral.playlistGen

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.tuneneutral.MiscConsts
import com.example.tuneneutral.NeutralisePlaylistMessage
import com.example.tuneneutral.R
import com.example.tuneneutral.SpotifyEndpoints.Companion.addTracksToPlaylist
import com.example.tuneneutral.SpotifyEndpoints.Companion.createPlaylist
import com.example.tuneneutral.SpotifyEndpoints.Companion.getCurrentUserID
import com.example.tuneneutral.database.DatabaseManager
import com.example.tuneneutral.database.TrackInfo
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.text.DateFormat.getDateInstance
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.round


class GenrateNeutralisedPlaylist(private val mSpotifyAccessToken: String, private val mCurrentValence: Float, private val mContext: Context) : Runnable {

    override fun run() {
        val tracks = getNTracksWithValence(mCurrentValence)

        if(tracks.count() > 0) {
            tracks.reverse()

            notifyObvs(NeutralisePlaylistMessage.Calcualting)

            val c: Date = Calendar.getInstance().time
            val df = getDateInstance()
            val formattedDate = df.format(c)

            val desc = mContext.getString(R.string.playlist_desc, (mCurrentValence * 100).toInt())

            val playlistID = createPlaylist(mSpotifyAccessToken, getCurrentUserID(mSpotifyAccessToken)!!, formattedDate, desc)

            if(playlistID != null) {
                addTracksToPlaylist(mSpotifyAccessToken, playlistID, tracks)
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
        intent.putExtra(
            MiscConsts.NEUTRALISE_PLAYLIST_MESSAGE,
            NeutralisePlaylistMessage.CompletePlaylistCreated
        )
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

        notifyObvs(NeutralisePlaylistMessage.Anaylsing)

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

    private fun calcStepsToNothing(currentValence: Float): ArrayList<Float> {
        val result = ArrayList<Float>()
        var updatedValence = currentValence

        while (updatedValence !in 0.49f..0.51f) {
            val diff = 0.5f - updatedValence
            val min = 0.0.coerceAtMost(diff.toDouble())
            val max = 0.0.coerceAtLeast(diff.toDouble())

            val nextNum = ThreadLocalRandom.current().nextDouble(min, max).toFloat()
            result.add(nextNum)
            updatedValence += nextNum
        }

        return result
    }
}