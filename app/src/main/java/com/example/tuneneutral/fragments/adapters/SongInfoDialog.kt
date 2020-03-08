package com.example.tuneneutral.fragments.adapters

import android.app.ActionBar
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import com.example.tuneneutral.R
import com.example.tuneneutral.database.Track
import kotlinx.android.synthetic.main.song_info_dialog.*

class SongInfoDialog(context: Context) : Dialog(context, android.R.style.ThemeOverlay_Material_Light) {
    class ViewHolder(view: Dialog) {
        private val songTitle: TextView = view.song_title
        private val artistTitle: TextView = view.artists_title
        private val popularityTitle: TextView = view.popularity_title
        private val lengthTitle: TextView = view.length_title
        private val valenceTitle: TextView = view.valence_title
        private val tempoTitle: TextView = view.tempo_title
        private val danceabilityTitle: TextView = view.danceability_title
        private val energyTitle: TextView = view.energy_title
        private val loudnessTitle: TextView = view.loudness_title
        private val keyTitle: TextView = view.key_title

        fun bind(track: Track, context: Context) {
            val trackInfo = track.trackInfo
            if(trackInfo != null) {
                songTitle.text = context.getString(R.string.song_view_title, trackInfo.title)
                artistTitle.text = context.getString(R.string.song_view_artists_title, trackInfo.artistsNames)
                popularityTitle.text = context.getString(R.string.popularity, trackInfo.popularity.toString())
            }

            val trackFeatures = track.trackFeatures

            lengthTitle.text = context.getString(R.string.length, ((trackFeatures.durationMs / 1000f) / 60f))
            valenceTitle.text = context.getString(R.string.valence, trackFeatures.valence)
            tempoTitle.text = context.getString(R.string.tempo, trackFeatures.tempo)
            danceabilityTitle.text = context.getString(R.string.danceability, trackFeatures.danceability)
            energyTitle.text = context.getString(R.string.energy, trackFeatures.energy)
            loudnessTitle.text = context.getString(R.string.loudness, trackFeatures.loudness)
            keyTitle.text = context.getString(R.string.key, trackFeatures.key)
        }
    }

    private val mViewHolder: ViewHolder

    init {
        setContentView(R.layout.song_info_dialog)

        val window = super.getWindow()
        if(window != null) {
            window.setLayout(ActionBar.LayoutParams.WRAP_CONTENT, ActionBar.LayoutParams.WRAP_CONTENT)
            window.setGravity(Gravity.CENTER)
            window.attributes.dimAmount = 0.7f
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }

        setCanceledOnTouchOutside(true)

        mViewHolder = ViewHolder(this)
    }

    fun bind(track: Track) {
        mViewHolder.bind(track, context)
    }
}