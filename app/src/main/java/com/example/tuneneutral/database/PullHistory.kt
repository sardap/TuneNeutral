package com.example.tuneneutral.database

data class PullHistory (
    val timestamp: Long,
    val offset: Int,
    val trackSources: TrackSources
)
