package com.example.tuneneutral.utility

import org.threeten.bp.LocalDate

class DateUtility {
    companion object {
        val TodayEpoch: Long
            get() = LocalDate.now().toEpochDay()
    }
}