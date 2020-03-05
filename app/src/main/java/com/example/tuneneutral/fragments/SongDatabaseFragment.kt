package com.example.tuneneutral.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tuneneutral.R
import com.example.tuneneutral.database.DatabaseManager
import com.example.tuneneutral.database.TrackInfo
import com.example.tuneneutral.fragments.adapters.SongDBListAdapter
import com.example.tuneneutral.spotify.SpotifyEndpoints
import com.example.tuneneutral.spotify.SpotifyUserInfo
import kotlinx.android.synthetic.main.fragment_song_database.view.*
import kotlinx.coroutines.*

/**
 * A simple [Fragment] subclass.
 * Use the [SongDatabaseFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class SongDatabaseFragment : Fragment() {
    private class ViewHolder(view: View) {
        val songsListView: RecyclerView = view.song_list_view
    }

    private lateinit var mViewHolder: ViewHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_song_database, container, false)
    }

    override fun onStart() {
        super.onStart()
        mViewHolder = ViewHolder(view!!)
        initSongAdapter()
    }

    private fun initSongAdapter() {
        val songsListView = mViewHolder.songsListView
        val dataSet = ArrayList(DatabaseManager.instance.getAllTracks())

        val adapter = SongDBListAdapter(dataSet)
        songsListView.apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
        }

        val uiScope = CoroutineScope(Dispatchers.Main)
        val accessToken = SpotifyUserInfo.SpotifyAccessToken
        if(accessToken != null) {
            for(i in dataSet.indices) {
                uiScope.launch {
                    if(dataSet[i].trackInfo == null) {
                        dataSet[i].trackInfo = getTrackInfo(accessToken, dataSet[i].trackID)
                        adapter.notifyItemChanged(i)
                    }

                }
            }
        }
    }

    private suspend fun getTrackInfo(accessToken: String, trackID: String): TrackInfo? = withContext(
        Dispatchers.Default) {
        return@withContext SpotifyEndpoints.getTrackInfo(accessToken, trackID)
    }

    companion object {
        @JvmStatic
        fun newInstance() =
            SongDatabaseFragment().apply {
                arguments = Bundle().apply {
                }
            }
    }
}
