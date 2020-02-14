package com.example.tuneneutral.playlistGen

import com.example.tuneneutral.SpotifyEndpoints.Companion.getTopTracks
import com.example.tuneneutral.SpotifyEndpoints.Companion.getTrackAnaysis
import com.example.tuneneutral.database.DatabaseManager
import com.example.tuneneutral.database.TrackInfo

class PullNewTracks(private val mSpotifyAccessToken: String) : Runnable {

    private enum class SpotifyTimeRange {
        short_term, medium_term, long_term
    }


    override fun run() {
        pullTopTracks(mSpotifyAccessToken, SpotifyTimeRange.short_term)
    }

    private fun pullTopTracks(accessToken: String, timePeriod: SpotifyTimeRange) {
        val existingTracks = DatabaseManager.instance.getAllTrackIds()

        var offset = 0

        val newTracks = ArrayList<String>()
        var lastFetchTracks: ArrayList<String>

        do {
            lastFetchTracks = getTopTracks(accessToken, timePeriod.toString(), offset)
            newTracks.addAll(lastFetchTracks.minus(existingTracks))
            offset += 20
        }while (offset < 200 && lastFetchTracks.count() > 0)

        pullTrackInfo(newTracks)
    }

    private fun pullTrackInfo(trackIDs: ArrayList<String>) {
        for (track in trackIDs) {
            val trackInfo = getTrackAnaysis(mSpotifyAccessToken, track)
            if(trackInfo != null) {
                DatabaseManager.instance.addTrackInfo(trackInfo)
            }
        }
    }
}