package com.example.tuneneutral.database

data class Database(
    val dayRatings: ArrayList<DayRating>,
    val pullHistory: HashMap<TrackSources, PullHistory>,
    val tracks: ArrayList<TrackInfo>
)
