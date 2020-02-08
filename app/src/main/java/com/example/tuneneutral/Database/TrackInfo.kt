package com.example.tuneneutral.Database

import org.json.JSONObject

class TrackInfo(public val tackId: String, jsonObject: JSONObject)  {
    val durationMs = jsonObject.getInt("duration_ms")
    val key = jsonObject.getInt("key")
    val mode = jsonObject.getInt("mode")
    val timeSignature = jsonObject.getInt("time_signature")
    val acousticness = jsonObject.getDouble("acousticness")
    val danceability = jsonObject.getDouble("danceability")
    val energy = jsonObject.getDouble("energy")
    val instrumentalness = jsonObject.getDouble("instrumentalness")
    val liveness = jsonObject.getDouble("liveness")
    val loudness = jsonObject.getDouble("loudness")
    val speechiness = jsonObject.getDouble("speechiness")
    val valence = jsonObject.getDouble("valence")
    val tempo = jsonObject.getDouble("tempo")
    val jsonObject = jsonObject
}