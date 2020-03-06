package com.example.tuneneutral.database

data class Database(
    val databaseVersion: Int?,
    val dayRatings: HashMap<Long, DayRating>,
    val pullHistory: HashMap<TrackSources, PullHistory>,
    val tracks: ArrayList<Track>,
    var userSettings: UserSettings,
    val blacklistedTracks: ArrayList<String>
)