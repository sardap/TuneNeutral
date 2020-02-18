package com.example.tuneneutral

import android.app.Application
import com.jakewharton.threetenabp.AndroidThreeTen

class TuneNeutral : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidThreeTen.init(this)
    }
}