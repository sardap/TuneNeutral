package com.example.tuneneutral.database

data class TrackInfo (
    val coverImageUrl: String,
    val title: String,
    val popularity: Int,
    val artistsNames: ArrayList<String>,
    val artistsIDs: ArrayList<String>,
    val albumTitle: String
)
