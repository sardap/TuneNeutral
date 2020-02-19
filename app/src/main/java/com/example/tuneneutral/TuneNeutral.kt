package com.example.tuneneutral

import android.app.Application
import com.example.tuneneutral.database.DatabaseManager
import com.jakewharton.threetenabp.AndroidThreeTen

class TuneNeutral : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidThreeTen.init(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        DatabaseManager.instance.commitChanges()
    }
}