package com.example.tuneneutral.utility

import org.threeten.bp.LocalDate

class DateUtility {
    companion object {
        val todayEpoch: Long
            get() = now.toEpochDay()

        val now: LocalDate
            get() = LocalDate.now()
    }
}