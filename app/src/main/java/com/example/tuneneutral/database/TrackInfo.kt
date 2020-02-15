package com.example.tuneneutral.database

data class TrackInfo(
    val tackId: String,
    val topTrack: Boolean,
    val durationMs: Int,
    val key: Int,
    val mode: Int,
    val timeSignature: Int,
    val acousticness: Double,
    val danceability: Double,
    val energy: Double,
    val instrumentalness: Double,
    val liveness: Double,
    val loudness: Double,
    val speechiness: Double,
    val valence: Double,
    val tempo: Double
)