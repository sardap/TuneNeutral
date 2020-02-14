package com.example.tuneneutral.database

data class Database(
    val dayRatings: ArrayList<DayRating>,
    val pullHistory: ArrayList<PullHistory>,
    val tracks: ArrayList<TrackInfo>
)
