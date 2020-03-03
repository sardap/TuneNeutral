package com.example.tuneneutral.database

data class Track(
    val trackID: String,
    val trackFeatures: TrackFeatures,
    var trackInfo: TrackInfo?
) {
    val topTrack = trackFeatures.topTrack
    val durationMs = trackFeatures.durationMs
    val key = trackFeatures.key
    val mode = trackFeatures.mode
    val timeSignature = trackFeatures.timeSignature
    val acousticness = trackFeatures.acousticness
    val danceability = trackFeatures.danceability
    val energy = trackFeatures.energy
    val instrumentalness = trackFeatures.instrumentalness
    val liveness = trackFeatures.liveness
    val loudness = trackFeatures.loudness
    val speechiness = trackFeatures.speechiness
    val valence = trackFeatures.valence
    val tempo = trackFeatures.tempo
}