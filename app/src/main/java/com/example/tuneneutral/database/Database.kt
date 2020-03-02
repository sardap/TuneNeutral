package com.example.tuneneutral.database

data class Database(
    val dayRatings: HashMap<Long, DayRating>,
    val pullHistory: HashMap<TrackSources, PullHistory>,
    val tracks: ArrayList<Track>,
    var UserSettings: UserSettings
)