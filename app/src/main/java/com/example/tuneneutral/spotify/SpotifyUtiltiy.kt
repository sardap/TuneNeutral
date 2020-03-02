package com.example.tuneneutral.spotify

import android.content.Context
import android.content.Intent
import android.net.Uri

class SpotifyUtiltiy {
    companion object {
        fun openPlaylistInSpotify(context: Context, playlistID: String) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("spotify:playlist:${playlistID}")
            intent.putExtra(
                Intent.EXTRA_REFERRER,
                Uri.parse("android-app://" + context.packageName)
            )

            context.startActivity(intent)
        }

        fun openTrackInSpotify(context: Context, trackID: String) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse("spotify:track:${trackID}")
            intent.putExtra(
                Intent.EXTRA_REFERRER,
                Uri.parse("android-app://" + context.packageName)
            )

            context.startActivity(intent)
        }


    }
}