package com.example.tuneneutral.database

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.io.*
import java.lang.RuntimeException


class DatabaseManager private constructor() {

    private object HOLDER {
        val INSTANCE = DatabaseManager()
        const val FILE_NAME = "database.json"
        const val DATABASE_TAG = "database"
    }

    companion object {
        private val DATABASE_VERSION = 1
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
        mDb.dayRatings[date.timestamp] = date
        writeDB()
    }

    @Synchronized
    fun getLastPullTime(): Long? {
        val result = mDb.pullHistory.maxBy { (_, v) -> v.timestamp }

        if (result != null) {
            return result.value.timestamp
        }

        return  null
    }

    fun getPullHistroy() : HashMap<TrackSources, PullHistory> {
        return HashMap(mDb.pullHistory)
    }

    @Synchronized
    fun getUserSettings(): UserSettings {
        return mDb.UserSettings
    }

    @Synchronized
    fun setUserSettings() {
        writeDB()
    }

    @Synchronized
    fun deletePullHistroy(key: TrackSources) {
        mDb.pullHistory.remove(key)

        writeDB()
    }

    @Synchronized
    fun getDayRatings(): MutableCollection<DayRating> {
        return mDb.dayRatings.values
    }

    @Synchronized
    fun getDayRating(date: Long): DayRating? {
        return mDb.dayRatings[date]
    }

    @Synchronized
    fun hasDayRating(): Boolean {
        return mDb.dayRatings.count() > 0
    }

    fun getDayRatingsInOrder(): List<DayRating> {
        val result = ArrayList(mDb.dayRatings.values)
        result.sortedWith(compareBy { it.timestamp })
        return result
    }

    @Synchronized
    fun getDayRatingsInRange(start: Long, end: Long): List<DayRating> {
        var result = getDayRatingsInOrder()
        result = result.filter { it.timestamp in start..end }
        return result
    }

    @Synchronized
    fun addTrackInfo(track: Track) {
        Log.d(HOLDER.DATABASE_TAG, "Adding new track ${track}}")
        mDb.tracks.add(track)
        writeDB()
    }

    @Synchronized
    fun getTrackInfo(trackId: String): Track? {
        val tracks = mDb.tracks

        for (track in tracks) {
            if(track.trackID == trackId) {
                return track
            }
        }

        return null
    }

    @Synchronized
    fun getAllTrackIds(): List<String> {
        val result = ArrayList<String>()

        for (track in getAllTracks()) {
            result.add(track.trackID)
        }

        return result
    }

    @Synchronized
    fun getTopTrackIds(): List<String> {
        val result = ArrayList<String>()

        for (track in getTopTracks()) {
            result.add(track.trackID)
        }

        return result
    }

    @Synchronized
    fun getTopTracks(): List<Track> {
        val result = ArrayList<Track>()

        for (track in getAllTracks()) {
            if(track.topTrack) {
                result.add(track)
            }
        }

        return result
    }

    @Synchronized
    fun getAllTracks(): List<Track> {
        return ArrayList(mDb.tracks)
    }

    @Synchronized
    fun commitChanges() {
        writeDB()
    }

    @Synchronized
    fun clearDatabase() {
        initDB()
        loadDB()
    }

    @Synchronized
    fun dbToJson(): String {
        val result = mGson.toJson(mDb)
        if(result != null)
            return result

        throw RuntimeException("Cannot convert database to json")
    }

    @Synchronized
    fun importNewDB(jsonStr: String) {
        val newDb = mGson.fromJson(jsonStr, Database::class.java)
        mDb = newDb
        commitChanges()
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

            val database = mGson.fromJson(jsonStr, Database::class.java)
            if(database.databaseVersion == null || database.databaseVersion != DATABASE_VERSION) {
                initDB()
                loadDB()
            } else {
                mDb = database
            }

        } catch (e: JsonSyntaxException) {
            Log.e(HOLDER.DATABASE_TAG, "Json file parsing: $e")
            initDB()
            loadDB()
        } catch (e: Exception) {
            initDB()
            loadDB()
        }
    }

    @Synchronized
    private fun writeDB() {
        try {
            val outputStreamWriter = OutputStreamWriter(mContext.openFileOutput(HOLDER.FILE_NAME, Context.MODE_PRIVATE))
            outputStreamWriter.write(dbToJson())
            outputStreamWriter.close()
        } catch (e: IOException) {
            Log.e("Exception", "File write failed: $e")
        }
    }

    private fun initDB() {
        try {
            val outputStreamWriter = OutputStreamWriter(mContext.openFileOutput(HOLDER.FILE_NAME, Context.MODE_PRIVATE))

            outputStreamWriter.write(
                mGson.toJson(Database(DATABASE_VERSION, HashMap(), HashMap(), ArrayList(), UserSettings(true)))
            )
            outputStreamWriter.close()
        } catch (e: IOException) {
            Log.e("Exception", "File write failed: $e")
        }
    }
}
