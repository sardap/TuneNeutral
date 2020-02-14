package com.example.tuneneutral.playlistGen

import android.util.Log
import com.example.tuneneutral.SpotifyEndpoints.Companion.getTopTracks
import com.example.tuneneutral.SpotifyEndpoints.Companion.getTrackAnaysis
import com.example.tuneneutral.database.DatabaseManager
import com.example.tuneneutral.database.PullHistory
import com.example.tuneneutral.database.TrackSources
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class PullNewTracks(private val mSpotifyAccessToken: String) : Runnable {

    private enum class SpotifyTimeRange {
        short_term, medium_term, long_term
    }

    private companion object {
        const val MAX_SHORT_LIST = 200
        const val MIN_IN_MILS = 60000
        const val WEEK_IN_MILS = 604800000L
        const val LOGGER_TAG = "PullTracks"
    }

    override fun run() {
        val pullHistory = DatabaseManager.instance.getPullHistroy()
        cleanPullHistory(pullHistory)

        var pulled = false

        val ary = ArrayList<Pair<TrackSources, SpotifyTimeRange>>()
        ary.add(Pair(TrackSources.TopTracksShort, SpotifyTimeRange.short_term))
        ary.add(Pair(TrackSources.TopTracksMed, SpotifyTimeRange.medium_term))
        ary.add(Pair(TrackSources.TopTracksLong, SpotifyTimeRange.long_term))

        for (entry in ary) {
            if(pullHistory.containsKey(entry.first) && !pullHistory[entry.first]!!.pullComplete) {
                pullTopTracks(mSpotifyAccessToken, entry.second, pullHistory[entry.first]!!)
                DatabaseManager.instance.addPullHistory(entry.first, pullHistory[entry.first]!!)
                pulled = true
                break
            }
        }

        if(!pulled) {
            val nextPull = ary[ary.indexOfFirst { !pullHistory.containsKey(it.first) }]
            PullHistory(Calendar.getInstance().timeInMillis, 0, false).apply {
                pullTopTracks(mSpotifyAccessToken, nextPull.second, this)
                DatabaseManager.instance.addPullHistory(nextPull.first, this)
            }
        }

        DatabaseManager.instance.commitChanges()
    }

    private fun cleanPullHistory(pullHistory: HashMap<TrackSources, PullHistory>) {
        val currentTime = Calendar.getInstance().timeInMillis

        fun processPull(key: TrackSources, entry: PullHistory, time: Long) {
            if(entry.pullComplete && entry.timestamp < currentTime - time) {
                pullHistory.remove(key)
                DatabaseManager.instance.deletePullHistroy(key)
            }
        }

        if(pullHistory.containsKey(TrackSources.TopTracksShort)) {
            processPull(TrackSources.TopTracksShort, pullHistory[TrackSources.TopTracksShort]!!, WEEK_IN_MILS)
        }

        if(pullHistory.containsKey(TrackSources.TopTracksMed)) {
            processPull(TrackSources.TopTracksMed, pullHistory[TrackSources.TopTracksMed]!!, WEEK_IN_MILS * 2)
        }

        if(pullHistory.containsKey(TrackSources.TopTracksLong)) {
            processPull(TrackSources.TopTracksLong, pullHistory[TrackSources.TopTracksLong]!!, WEEK_IN_MILS * 10)
        }
    }

    private fun pullTopTracks(accessToken: String, timePeriod: SpotifyTimeRange, lastPullHistory: PullHistory) {
        Log.d(LOGGER_TAG, "Pulling top tracks from range:${timePeriod}")

        val existingTracks = DatabaseManager.instance.getAllTrackIds()

        var offset = lastPullHistory.offset

        var lastFetchTracks: ArrayList<String>

        val startTime = Calendar.getInstance().timeInMillis
        do {
            lastFetchTracks = getTopTracks(accessToken, timePeriod.toString(), offset)
            pullTrackInfo(lastFetchTracks.minus(existingTracks))
            offset += 20
            Log.d(LOGGER_TAG, "Pulled ${lastFetchTracks.count()} tracks total time taken ${(Calendar.getInstance().timeInMillis - startTime)}")
        }while (Calendar.getInstance().timeInMillis - startTime < 5000 && lastFetchTracks.count() > 0)

        lastPullHistory.offset = offset

        if(lastFetchTracks.count() <= 0) {
            lastPullHistory.pullComplete = true
        }
    }

    private fun pullTrackInfo(trackIDs: List<String>) {
        for (track in trackIDs) {
            val trackInfo = getTrackAnaysis(mSpotifyAccessToken, track)
            if(trackInfo != null) {
                DatabaseManager.instance.addTrackInfo(trackInfo)
            }
        }
    }
}