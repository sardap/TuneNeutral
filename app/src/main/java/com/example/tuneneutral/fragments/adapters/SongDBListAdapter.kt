package com.example.tuneneutral.fragments.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.example.tuneneutral.R
import com.example.tuneneutral.database.DatabaseManager
import com.example.tuneneutral.database.Track
import com.example.tuneneutral.spotify.SpotifyUtiltiy
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.song_list_view.view.*

class SongDBListAdapter(
    private val mDataSet: ArrayList<Track>
) : RecyclerView.Adapter<SongDBListAdapter.ViewHolder>(){

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view){
        private val songCover: ImageView = view.song_cover
        private val songTitle: TextView = view.song_title
        private val songAlbumTitle: TextView = view.album_title
        private val artistsTitle: TextView = view.artists_title
        private val songCoverLoading: ProgressBar = view.song_cover_loading_bar
        private val songPostion: TextView = view.pos_text
        private val infoButton: ImageButton = view.info_button
        private val blacklistButton: ImageButton = view.blacklist_button

        fun bind(track: Track, position: Int, blacklistClickListener: View.OnClickListener) {

            val trackInfo = track.trackInfo
            if(trackInfo != null) {
                songCoverLoading.visibility = View.GONE
                songCover.visibility = View.VISIBLE
                Picasso.get().load(trackInfo.coverImageUrl).into(songCover)
                songTitle.text = itemView.context.getString(R.string.song_view_title, trackInfo.title)
                songAlbumTitle.text = itemView.context.getString(R.string.song_view_album_title, trackInfo.albumTitle)
                artistsTitle.text = itemView.context.getString(R.string.song_view_artists_title, trackInfo.artistsNames.joinToString(postfix = ","))

                Log.d("COVER_LIST", "$position: ${track.trackID} BINDING NEW Title: ${trackInfo.title}")
            }

            songCover.setOnClickListener {
                SpotifyUtiltiy.openTrackInSpotify(itemView.context, track.trackID)
            }

            songPostion.text = (position + 1).toString()

            blacklistButton.setOnClickListener(blacklistClickListener)

            infoButton.setOnClickListener {
                Toast.makeText(itemView.context, "Coming soon", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.song_list_view,
                parent,
                false
            )
        )
    }

    override fun getItemCount() = mDataSet.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(
            mDataSet[position],
            position,
            View.OnClickListener {
                val track = mDataSet[position]
                mDataSet.removeAt(position)
                notifyItemRemoved(position)
                notifyItemRangeChanged(position, mDataSet.size)
                DatabaseManager.instance.blacklistTrack(track)
            }
        )
    }

}