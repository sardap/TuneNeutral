package com.example.tuneneutral.database

data class PullHistory (
    var timestamp: Long,
    var offset: Int,
    var pullComplete: Boolean
)
