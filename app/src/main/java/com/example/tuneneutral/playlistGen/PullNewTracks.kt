package com.example.tuneneutral.playlistGen

import android.util.Log
import com.example.tuneneutral.spotify.SpotifyEndpoints.Companion.getRecommendedTracks
import com.example.tuneneutral.spotify.SpotifyEndpoints.Companion.getTopTracks
import com.example.tuneneutral.spotify.SpotifyEndpoints.Companion.getTrackAnaysis
import com.example.tuneneutral.database.DatabaseManager
import com.example.tuneneutral.database.PullHistory
import com.example.tuneneutral.database.TrackInfo
import com.example.tuneneutral.database.TrackSources
import com.example.tuneneutral.utility.DateUtility
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class PullNewTracks(private val mSpotifyAccessToken: String) : Runnable {

    private enum class SpotifyTimeRange {
        short_term, medium_term, long_term
    }

    private class TrackSource(val trackSource: TrackSources, val timePeriod: SpotifyTimeRange)

    private companion object {
        const val WEEK_IN_MILS = 604800000L
        const val LOGGER_TAG = "PullTracks"
    }

    private val mTopTrackPullInfo = arrayListOf(
        TrackSource(TrackSources.TopTracksShort, SpotifyTimeRange.short_term),
        TrackSource(TrackSources.TopTracksMed, SpotifyTimeRange.medium_term),
        TrackSource(TrackSources.TopTracksLong, SpotifyTimeRange.long_term)
    )

    override fun run() {
        val pullHistory = DatabaseManager.instance.getPullHistroy()

        cleanPullHistory(pullHistory)

        val existingTracks = DatabaseManager.instance.getAllTrackIds()
        val prePullSongs = existingTracks.count()

        if(
            mTopTrackPullInfo.all { pullHistory.containsKey(it.trackSource) } ||
            existingTracks.count() > 100 && ThreadLocalRandom.current().nextInt(2) == 0
        ) {
            pullRecommendedTracks()
        } else {
            pullTopTracks(pullHistory)
        }

        DatabaseManager.instance.commitChanges()

        val postPullCount = DatabaseManager.instance.getAllTracks().count()
        if(postPullCount < 20 || prePullSongs in postPullCount..postPullCount + 4) {
            run()
        }
    }

    private fun pullRecommendedTracks() {
        val trackInfo = DatabaseManager.instance.getTopTracks().shuffled()

        val seedTracks = ArrayList<TrackInfo>()

        for(i in 0 until 5) {
            seedTracks.add(trackInfo[i])
        }

        val newTracks = getRecommendedTracks(
            mSpotifyAccessToken,
            5,
            seedTracks.map { it.tackId },
            seedTracks.map { it.speechiness }.average(),
            seedTracks.map { it.acousticness }.average(),
            seedTracks.map { it.tempo }.average(),
            seedTracks.map { it.timeSignature }.average().toInt()
        )

        pullTrackInfo(newTracks.minus(DatabaseManager.instance.getAllTrackIds()), false)

        DatabaseManager.instance.addPullHistory(TrackSources.RecomendedTracks, PullHistory(DateUtility.todayEpoch, 0, false))
    }

    private fun pullTopTracks(pullHistory: HashMap<TrackSources, PullHistory>) {
        val topTracksPullInfoStack = Stack<TrackSource>()
        mTopTrackPullInfo.forEach { topTracksPullInfoStack.push(it) }

        while (topTracksPullInfoStack.count() > 0) {
            val top = topTracksPullInfoStack.pop()
            if(pullHistory.containsKey(top.trackSource) && !pullHistory[top.trackSource]!!.pullComplete) {
                pullTopTracks(mSpotifyAccessToken, top.timePeriod, pullHistory[top.trackSource]!!)
                DatabaseManager.instance.addPullHistory(top.trackSource, pullHistory[top.trackSource]!!)
                break
            }
        }

        if(topTracksPullInfoStack.count() == 0) {
            val i = mTopTrackPullInfo.indexOfFirst { !pullHistory.containsKey(it.trackSource) }
            val nextPull = mTopTrackPullInfo[i]
            PullHistory(DateUtility.todayEpoch, 0, false).apply {
                pullTopTracks(mSpotifyAccessToken, nextPull.timePeriod, this)
                DatabaseManager.instance.addPullHistory(nextPull.trackSource, this)
            }
        }
    }

    private fun cleanPullHistory(pullHistory: HashMap<TrackSources, PullHistory>) {
        val currentTime = DateUtility.todayEpoch

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
            pullTrackInfo(lastFetchTracks.minus(existingTracks), true)
            offset += 20
            Log.d(LOGGER_TAG, "Pulled ${lastFetchTracks.count()} tracks total time taken ${(Calendar.getInstance().timeInMillis - startTime)}")
        }while (Calendar.getInstance().timeInMillis - startTime < 5000 && lastFetchTracks.count() > 0)

        lastPullHistory.offset = offset

        if(lastFetchTracks.count() <= 0) {
            lastPullHistory.pullComplete = true
        }
    }

    private fun pullTrackInfo(trackIDs: List<String>, topTrack: Boolean) {
        for (track in trackIDs) {
            val trackInfo = getTrackAnaysis(mSpotifyAccessToken, track, topTrack)
            if(trackInfo != null) {
                DatabaseManager.instance.addTrackInfo(trackInfo)
            }
        }
    }
}