package com.example.tuneneutral.database

data class DayRating(
    val timestamp: Long,
    val rating: Int,
    val playlistID: String
)