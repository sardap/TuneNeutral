package com.example.tuneneutral.database

import android.content.Context
import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import java.io.*


class DatabaseManager private constructor() {

    private object HOLDER {
        val INSTANCE = DatabaseManager()
        const val FILE_NAME = "database.json"
        const val DATABASE_TAG = "database"
    }

    private object DATABASE_NAME {
        const val TRACKS = "tracks"
        const val DATES = "dates"
    }

    companion object {
        val instance: DatabaseManager by lazy { HOLDER.INSTANCE }
    }

    private lateinit var mContext: Context
    private lateinit var mDb: JSONObject

    @Synchronized
    fun giveContext(context: Context) {
        mContext = context
        loadDB()
    }

    @Synchronized
    fun addDateInfo(date: DateInfo) {
        val dates = mDb.getJSONArray(DATABASE_NAME.DATES)
        dates.put(date.toJsonObject())
        writeDB()
    }

    @Synchronized
    fun getDates(startTimeStamp: Long, endTimeStamp: Long): ArrayList<DateInfo> {
        val dates = mDb.getJSONArray(DATABASE_NAME.DATES)
        val result = ArrayList<DateInfo>()

        for (i in 0..dates.length()) {
            val timestamp = (dates[i] as JSONObject).getLong("timestamp")
            if(timestamp in startTimeStamp..endTimeStamp) {
                val dateJson = (dates[i] as JSONObject)
                result.add(DateInfo(dateJson.getLong("timestamp"), dateJson.getInt("rating"), dateJson.getString("playlistID")))
            }
        }

        return result
    }

    @Synchronized
    fun addTrackInfo(track: TrackInfo) {
        val tracks = mDb.getJSONArray(DATABASE_NAME.TRACKS)
        tracks.put(track.jsonObject)
        writeDB()
    }

    @Synchronized
    fun getTrackInfo(trackId: String): TrackInfo? {
        val tracks = mDb.getJSONArray(DATABASE_NAME.TRACKS)

        for (i in 0..tracks.length()) {
            if((tracks[i] as JSONObject).getString("id") == trackId) {
                return TrackInfo(
                    trackId,
                    (tracks[i] as JSONObject)
                )
            }
        }

        return null
    }

    @Synchronized
    fun getAllTrackIds(): List<String> {
        val tracks = getAllTracks()
        val result = ArrayList<String>()

        for (track in tracks) {
            result.add(track.tackId)
        }

        return result
    }

    @Synchronized
    fun getAllTracks(): List<TrackInfo> {
        val tracks = mDb.getJSONArray(DATABASE_NAME.TRACKS)
        val result = ArrayList<TrackInfo>()
        val length = tracks.length() - 1

        for (i in 0..length) {
            val trackInfo = TrackInfo(
                (tracks[i] as JSONObject).getString("id"),
                (tracks[i] as JSONObject)
            )

            result.add(trackInfo)
        }

        return result
    }

    private fun checkDB(): Boolean {
        return mDb.has(DATABASE_NAME.DATES) && mDb.has(DATABASE_NAME.TRACKS)
    }

    @Synchronized
    private fun loadDB() {
        try {
            val inputStream: InputStream = mContext.openFileInput(HOLDER.FILE_NAME)
            val inputStreamReader = InputStreamReader(inputStream)
            val bufferedReader = BufferedReader(inputStreamReader)

            val stringBuilder = StringBuilder()
            var line: String? = bufferedReader.readLine()
            while (line != null) {
                stringBuilder.append(line)
                line = bufferedReader.readLine()
            }

            val jsonStr = stringBuilder.toString()

            mDb = JSONObject(jsonStr)

            if(!checkDB()) {
                initaliseDB()
                loadDB()
            }
        } catch (e: FileNotFoundException) {
            Log.e(HOLDER.DATABASE_TAG, "File not found: $e")
            initaliseDB()
            loadDB()
        } catch (e: JSONException) {
            Log.e(HOLDER.DATABASE_TAG, "Json file parsing: $e")
            initaliseDB()
            loadDB()
        } catch (e: IOException) {
            Log.e(HOLDER.DATABASE_TAG, "Can not read file: $e")
        }
    }

    @Synchronized
    private fun writeDB() {
        try {
            val outputStreamWriter = OutputStreamWriter(mContext.openFileOutput(HOLDER.FILE_NAME, Context.MODE_PRIVATE))
            outputStreamWriter.write(mDb.toString())
            outputStreamWriter.close()
        } catch (e: IOException) {
            Log.e("Exception", "File write failed: $e")
        }
    }

    private fun initaliseDB() {
        try {
            val outputStreamWriter = OutputStreamWriter(mContext.openFileOutput(HOLDER.FILE_NAME, Context.MODE_PRIVATE))
            outputStreamWriter.write("{ \"${DATABASE_NAME.TRACKS}\" : [], \"${DATABASE_NAME.DATES}\" : [] }")
            outputStreamWriter.close()
        } catch (e: IOException) {
            Log.e("Exception", "File write failed: $e")
        }
    }
}
