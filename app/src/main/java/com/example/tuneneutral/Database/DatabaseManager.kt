package com.example.tuneneutral.Database

import android.content.Context
import android.util.Log
import com.example.tuneneutral.TrackInfo
import org.json.JSONObject
import java.io.*
import java.io.File.separator


class DatabaseManager private constructor() {

    private object HOLDER {
        val INSTANCE = DatabaseManager()
        const val FILE_NAME = "database.json"
        const val DATABASE_TAG = "database"
    }

    companion object {
        val instance: DatabaseManager by lazy { HOLDER.INSTANCE }
    }

    private lateinit var mContext: Context
    private lateinit var mDb: JSONObject

    @Synchronized
    fun giveContex(context: Context) {
        mContext = context
        loadDB()
    }

    @Synchronized
    fun addTrackInfo(track: TrackInfo) {
        val tracks = mDb.getJSONArray("Tracks")
        tracks.put(track.jsonObject)
        writeDB()
    }

    @Synchronized
    fun getTrackInfo(trackId: String): TrackInfo? {
        val tracks = mDb.getJSONArray("Tracks")

        for (i in 0..tracks.length()) {
            if((tracks[i] as JSONObject).getString("id") == trackId) {
                return TrackInfo(trackId, (tracks[i] as JSONObject))
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
        val tracks = mDb.getJSONArray("Tracks")
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

        } catch (e: FileNotFoundException) {
            Log.e(HOLDER.DATABASE_TAG, "File not found: $e")
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
            outputStreamWriter.write("{ \"Tracks\" : [] }")
            outputStreamWriter.close()
        } catch (e: IOException) {
            Log.e("Exception", "File write failed: $e")
        }
    }
}
