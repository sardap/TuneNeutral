package com.example.tuneneutral.database

import org.json.JSONObject

class DateInfo(val timestamp: Long, val rating: Int, val playlistID: String) {
    fun toJsonObject() : JSONObject {
        val result = JSONObject()
        result.put("timestamp", timestamp)
        result.put("rating", rating)
        result.put("playlistID", playlistID)
        return result
    }
}