package com.example.tuneneutral

import android.util.Log
import com.example.tuneneutral.database.TrackInfo
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import kotlin.collections.ArrayList

class SpotifyEndpoints {
    companion object {

        private val mOkHttpClient = OkHttpClient()

        fun getCurrentUserID(accessToken: String): String? {
            var result: String? = null

            val request = Request.Builder()
                .url("https://api.spotify.com/v1/me")
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            val response = sendRequestGetResponse(request)

            if (response.isSuccessful) {
                try {
                    result = JSONObject(response.body!!.string()).getString("id")
                    Log.d("Status: ", "Got UserID: $result")
                } catch (e: JSONException) {
                    Log.d("Status: ", "Failed to get user: $e")
                }
            } else {
                Log.d("Status: ", "Failed to get user")
            }

            return result

        }

        fun createPlaylist(
            accessToken: String,
            userID: String,
            playlistName: String,
            playlistDesc: String
        ): String? {

            val json =
                "{ \"name\": \"${playlistName}\", \"description\": \"${playlistDesc}\", \"public\":false }"

            val requestBody = json.toRequestBody()

            val request = Request.Builder()
                .url("https://api.spotify.com/v1/users/${userID}/playlists")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(requestBody)
                .build()

            val response = sendRequestGetResponse(request)

            if (response.isSuccessful) {
                try {
                    val result = JSONObject(response.body!!.string()).getString("id")
                    Log.d("Status: ", "playlist Created! ID:$result")
                    return result
                } catch (e: JSONException) {
                    Log.d("Status: ", "Failed to create playlist: $e")
                }
            } else {
                Log.d("Status: ", "Failed to create playlist: $response")
            }

            return null
        }

        fun addTracksToPlaylist(
            accessToken: String,
            playlistID: String,
            tracks: ArrayList<String>
        ): Boolean {
            var tracksString = StringBuilder()

            tracks.forEach { tracksString.append("spotify:track:${it},") }

            val request = Request.Builder()
                .url("https://api.spotify.com/v1/playlists/${playlistID}/tracks?uris=${tracksString}")
                .addHeader("Authorization", "Bearer $accessToken")
                .post("".toRequestBody())
                .build()

            val response = sendRequestGetResponse(request)

            if (response.isSuccessful) {
                try {
                    Log.d("Status: ", "tracks added")
                    return true
                } catch (e: JSONException) {
                    Log.d("Status: ", "Failed to add tracks: $e")
                }
            } else {
                Log.d("Status: ", "Failed to add tracks")
            }

            return false
        }


        fun getTrackAnaysis(accessToken: String, id: String): TrackInfo? {
            var result: TrackInfo? = null

            val request = Request.Builder()
                .url(String.format("https://api.spotify.com/v1/audio-features/%s", id))
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            val response = sendRequestGetResponse(request)

            if (response.isSuccessful) {
                try {
                    result = TrackInfo(
                        id,
                        JSONObject(response.body!!.string())
                    )
                    Log.d("Status: ", "Got new analysis: $result")
                } catch (e: JSONException) {
                    Log.d("Status: ", "Failed to parse data: $e")
                }
            } else {
                Log.d("Status: ", "Failed to fetch data")
            }

            return result
        }

        fun getTopTracks(accessToken: String, timePeriod: String, offset: Int): ArrayList<String> {
            var result = ArrayList<String>()

            val request = Request.Builder()
                .url("https://api.spotify.com/v1/me/top/tracks?time_range=${timePeriod}&offset=${offset}&limit=20")
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            val response = sendRequestGetResponse(request)

            if (response.isSuccessful) {
                try {
                    val jsonObject = JSONObject(response.body!!.string())

                    val topTracksUris = ArrayList<String>()

                    val tracks = jsonObject.getJSONArray("items")

                    for (i in 0 until tracks.length()) {
                        topTracksUris.add(tracks.getJSONObject(i).getString("id"))
                    }

                    Log.d("top tracks gotten :", topTracksUris.toString())

                    result = topTracksUris

                } catch (e: JSONException) {
                    Log.d("Status: ", "Failed to parse data: $e")
                }
            } else {
                Log.d("Status: ", "Failed to fetch data")
            }

            return result
        }

        private fun sendRequestGetResponse(request: Request): Response {
            return mOkHttpClient.newCall(request).execute()
        }

    }
}