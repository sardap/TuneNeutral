package com.example.tuneneutral.spotify

import android.media.MediaPlayer
import android.util.Log
import com.example.tuneneutral.database.Track
import com.example.tuneneutral.database.TrackFeatures
import com.example.tuneneutral.database.TrackInfo
import com.google.gson.JsonObject
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import kotlin.collections.ArrayList
import kotlin.math.round

class SpotifyEndpoints {
    companion object {
        private const val SPOTIFY_END_POINT = "SpotifyEndPoint"

        private val mOkHttpClient = OkHttpClient()

        fun getCurrentUserID(accessToken: String): String? {
            var result: String? = null

            val request = Request.Builder()
                .url("https://api.spotify.com/v1/me")
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            val response =
                sendRequestGetResponse(
                    request
                )

            if (response.isSuccessful) {
                try {
                    result = JSONObject(response.body!!.string()).getString("id")
                    Log.d(SPOTIFY_END_POINT, "Got UserID: $result")
                } catch (e: JSONException) {
                    Log.d(SPOTIFY_END_POINT, "Failed to get user: $e")
                }
            } else {
                Log.d(SPOTIFY_END_POINT, "Failed to get user")
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

            val response =
                sendRequestGetResponse(
                    request
                )

            if (response.isSuccessful) {
                try {
                    val result = JSONObject(response.body!!.string()).getString("id")
                    Log.d(SPOTIFY_END_POINT, "playlist Created! ID:$result")
                    return result
                } catch (e: JSONException) {
                    Log.d(SPOTIFY_END_POINT, "Failed to create playlist: $e")
                }
            } else {
                Log.d(SPOTIFY_END_POINT, "Failed to create playlist: $response")
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

            val response =
                sendRequestGetResponse(
                    request
                )

            if (response.isSuccessful) {
                try {
                    Log.d(SPOTIFY_END_POINT, "tracks added")
                    return true
                } catch (e: JSONException) {
                    Log.d(SPOTIFY_END_POINT, "Failed to add tracks: $e")
                }
            } else {
                Log.d(SPOTIFY_END_POINT, "Failed to add tracks")
            }

            return false
        }


        fun getTrackAnaysis(accessToken: String, id: String, topTrack: Boolean): Track? {
            var result: Track? = null

            val request = Request.Builder()
                .url(String.format("https://api.spotify.com/v1/audio-features/%s", id))
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            val response =
                sendRequestGetResponse(
                    request
                )

            if (response.isSuccessful) {
                try {
                    val jsonObject = JSONObject(response.body!!.string())

                    result = Track(
                        id,
                        TrackFeatures(
                            topTrack,
                            jsonObject.getInt("duration_ms"),
                            jsonObject.getInt("key"),
                            jsonObject.getInt("mode"),
                            jsonObject.getInt("time_signature"),
                            jsonObject.getDouble("acousticness"),
                            jsonObject.getDouble("danceability"),
                            jsonObject.getDouble("energy"),
                            jsonObject.getDouble("instrumentalness"),
                            jsonObject.getDouble("liveness"),
                            jsonObject.getDouble("loudness"),
                            jsonObject.getDouble("speechiness"),
                            jsonObject.getDouble("valence"),
                            jsonObject.getDouble("tempo")
                        ),
                        null
                    )

                    Log.d(SPOTIFY_END_POINT, "Got new analysis: $result")
                } catch (e: JSONException) {
                    Log.d(SPOTIFY_END_POINT, "Failed to parse data: $e")
                }
            } else {
                Log.d(SPOTIFY_END_POINT, "Failed to fetch data")
            }

            return result
        }

        fun getTopTracks(accessToken: String, timePeriod: String, offset: Int): ArrayList<String> {
            var result = ArrayList<String>()

            val request = Request.Builder()
                .url("https://api.spotify.com/v1/me/top/tracks?time_range=${timePeriod}&offset=${offset}&limit=5")
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            val response =
                sendRequestGetResponse(
                    request
                )

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
                    Log.d(SPOTIFY_END_POINT, "Failed to parse data: $e")
                }
            } else {
                Log.d(SPOTIFY_END_POINT, "Failed to fetch data")
            }

            return result
        }

        fun unflollowPlaylist(accessToken: String, playlistID: String) : Boolean {
            val request = Request.Builder()
                .url("https://api.spotify.com/v1/playlists/${playlistID}/followers")
                .addHeader("Authorization", "Bearer $accessToken")
                .delete()
                .build()

            val response =
                sendRequestGetResponse(
                    request
                )

            if (response.isSuccessful) {
                try {
                    Log.d(SPOTIFY_END_POINT, "Unfollowed playlist $playlistID")
                    return true
                } catch (e: JSONException) {
                    Log.d(SPOTIFY_END_POINT, "Failed to parse data: $e")
                }
            } else {
                Log.d(SPOTIFY_END_POINT, "Failed to fetch data")
            }

            return false
        }

        fun getRecommendedTracks(
            accessToken: String,
            limit: Int,
            seedTracks: List<String>,
            targetSpeechiness: Double,
            targetAcousticness: Double,
            targetTempo: Double,
            timeSignature: Int
        ): List<String> {

            val seedTracksStr = StringBuilder()

            seedTracks.forEach { seedTracksStr.append("$it,") }

            val url = HttpUrl.Builder()
                .scheme("https")
                .host("api.spotify.com")
                .addPathSegment("v1")
                .addPathSegment("recommendations")
                .addQueryParameter("limit", "$limit")
                .addQueryParameter("seed_tracks", seedTracksStr.toString())
                .addQueryParameter("target_speechiness", "${round(targetSpeechiness * 100) / 100}")
                .addQueryParameter("target_acousticness", "${round(targetAcousticness * 100) / 100}")
                .addQueryParameter("target_tempo", "${round(targetTempo * 100) / 100}")
                .addQueryParameter("time_signature", "$timeSignature")
//                .addQueryParameter("target_valence", "$targetValence")


            val urlParsed = url.build().toString()

            val request = Request.Builder()
                .url(urlParsed)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            val response =
                sendRequestGetResponse(
                    request
                )

            if (response.isSuccessful) {
                try {
                    Log.d(SPOTIFY_END_POINT, "Got Recommended tracks $url")
                    val result = ArrayList<String>()

                    val jsonObject = JSONObject(response.body!!.string())
                    val jsonTracks = jsonObject.getJSONArray("tracks")

                    for(i in 0 until jsonTracks.length()) {
                        result.add((jsonTracks[i] as JSONObject).getString("id"))
                    }

                    return result
                } catch (e: JSONException) {
                    Log.d(SPOTIFY_END_POINT, "Failed to parse data: $e")
                }
            } else {
                Log.d(SPOTIFY_END_POINT, "Failed to fetch data ${response.body?.string()}")
            }

            return ArrayList()
        }

        fun getTrackInfo(accessToken: String, trackID: String) : TrackInfo? {
            var result: TrackInfo? = null

            val request = Request.Builder()
                .url("https://api.spotify.com/v1/tracks/$trackID")
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            val response =
                sendRequestGetResponse(
                    request
                )

            if (response.isSuccessful) {
                try {
                    val jsonObject = JSONObject(response.body!!.string())

                    val topTracksUris = ArrayList<String>()

                    Log.d("top tracks gotten :", topTracksUris.toString())

                    var coverUrl: String = "https://community.spotify.com/t5/image/serverpage/image-id/25294i2836BD1C1A31BDF2/image-size/original?v=mpbl-1&px=-1"
                    val images = jsonObject.getJSONObject("album").getJSONArray("images")

                    for (i in 0 until images.length()) {
                        if(images.getJSONObject(i).getInt("height") == 300) {
                            coverUrl = images.getJSONObject(i).getString("url")
                        }
                    }

                    val artists = ArrayList<String>()
                    val artistsIds = ArrayList<String>()
                    val jsonArtists = jsonObject.getJSONArray("artists")

                    for (i in 0 until jsonArtists.length()) {
                        artists.add(jsonArtists.getJSONObject(i).getString("name"))
                        artistsIds.add(jsonArtists.getJSONObject(i).getString("id"))
                    }

                    result = TrackInfo(
                        coverUrl,
                        jsonObject.getString("name"),
                        jsonObject.getInt("popularity"),
                        artists,
                        artistsIds,
                        jsonObject.getJSONObject("album").getString("name")
                    )

                } catch (e: JSONException) {
                    Log.d(SPOTIFY_END_POINT, "Failed to parse data: $e")
                }
            } else {
                Log.d(SPOTIFY_END_POINT, "Failed to fetch data")
            }

            return result
        }

        private fun sendRequestGetResponse(request: Request): Response {
            val result = OkHttpClient().newCall(request).execute()
            if(result.code == 429) {
                Log.e(SPOTIFY_END_POINT, "Too many Spotify requests rate limiting applied")
            }
            return result
        }
    }
}