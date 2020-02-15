package com.example.tuneneutral.database

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.lang.Exception


class DatabaseManager private constructor() {

    private object HOLDER {
        val INSTANCE = DatabaseManager()
        const val FILE_NAME = "database.json"
        const val DATABASE_TAG = "database"
    }

    companion object {
        val instance: DatabaseManager by lazy { HOLDER.INSTANCE }
    }

    private val mGson = Gson()
    private lateinit var mContext: Context
    private lateinit var mDb: Database

    @Synchronized
    fun giveContext(context: Context) {
        mContext = context
        loadDB()
    }

    @Synchronized
    fun addPullHistory(trackSources: TrackSources, pullHistory: PullHistory) {
        mDb.pullHistory[trackSources] = pullHistory

        writeDB()
    }

    @Synchronized
    fun addDateInfo(date: DayRating) {
        val dates = mDb.dayRatings

        assert(dates.count() > 0 && (dates[dates.count() - 1].timestamp < date.timestamp))

        dates.add(date)
        writeDB()
    }

    @Synchronized
    fun getPullHistroy() : HashMap<TrackSources, PullHistory> {
        return HashMap(mDb.pullHistory)
    }

    @Synchronized
    fun deletePullHistroy(key: TrackSources) {
        mDb.pullHistory.remove(key)

        writeDB()
    }

    @Synchronized
    fun getDayRatings(): ArrayList<DayRating> {
        return ArrayList(mDb.dayRatings)
    }

    @Synchronized
    fun getDatesInRange(startTimeStamp: Long, endTimeStamp: Long): ArrayList<DayRating> {
        val dates = mDb.dayRatings
        val result = ArrayList<DayRating>()

        for (i in 0 until dates.count()) {
            val timestamp = dates[i].timestamp
            if(timestamp in startTimeStamp..endTimeStamp) {
                result.add(dates[i])
            }
        }

        return result
    }

    @Synchronized
    fun addTrackInfo(track: TrackInfo) {
        Log.d(HOLDER.DATABASE_TAG, "Adding new track ${track}}")
        mDb.tracks.add(track)
        writeDB()
    }

    @Synchronized
    fun getTrackInfo(trackId: String): TrackInfo? {
        val tracks = mDb.tracks

        for (track in tracks) {
            if(track.tackId == trackId) {
                return track
            }
        }

        return null
    }

    @Synchronized
    fun getAllTrackIds(): List<String> {
        val result = ArrayList<String>()

        for (track in getAllTracks()) {
            result.add(track.tackId)
        }

        return result
    }

    @Synchronized
    fun getTopTrackIds(): List<String> {
        val result = ArrayList<String>()

        for (track in getTopTracks()) {
            result.add(track.tackId)
        }

        return result
    }

    @Synchronized
    fun getTopTracks(): List<TrackInfo> {
        val result = ArrayList<TrackInfo>()

        for (track in getAllTracks()) {
            if(track.topTrack) {
                result.add(track)
            }
        }

        return result
    }

    @Synchronized
    fun getAllTracks(): List<TrackInfo> {
        return ArrayList(mDb.tracks)
    }

    @Synchronized
    fun commitChanges() {
        writeDB()
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

            mDb = mGson.fromJson(jsonStr, Database::class.java)

        } catch (e: Exception) {
            Log.e(HOLDER.DATABASE_TAG, "Json file parsing: $e")
            initaliseDB()
            loadDB()
        }
    }

    @Synchronized
    private fun writeDB() {
        try {
            val outputStreamWriter = OutputStreamWriter(mContext.openFileOutput(HOLDER.FILE_NAME, Context.MODE_PRIVATE))
            outputStreamWriter.write(mGson.toJson(mDb))
            outputStreamWriter.close()
        } catch (e: IOException) {
            Log.e("Exception", "File write failed: $e")
        }
    }

    private fun initaliseDB() {
        try {
            val outputStreamWriter = OutputStreamWriter(mContext.openFileOutput(HOLDER.FILE_NAME, Context.MODE_PRIVATE))

            outputStreamWriter.write(
                mGson.toJson(Database(ArrayList(), HashMap(), ArrayList()))
            )
            outputStreamWriter.close()
        } catch (e: IOException) {
            Log.e("Exception", "File write failed: $e")
        }
    }
}
