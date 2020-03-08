package com.example.tuneneutral.fragments

import android.os.Bundle
import android.os.Handler
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tuneneutral.R
import com.example.tuneneutral.database.DatabaseManager
import com.example.tuneneutral.database.TrackInfo
import com.example.tuneneutral.fragments.adapters.SongDBListAdapter
import com.example.tuneneutral.spotify.SpotifyEndpoints
import com.example.tuneneutral.spotify.SpotifyUserInfo
import kotlinx.android.synthetic.main.fragment_song_database.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_song_database, container, false)

        mViewHolder = ViewHolder(view)
        initSongAdapter()

        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        if(savedInstanceState != null) {
            val runnable = Runnable {
                val listState = savedInstanceState.getParcelable<Parcelable>(RECYCLER_STATE)
                mViewHolder.songsListView.layoutManager?.onRestoreInstanceState(listState)
            }

            Handler().postDelayed(runnable, 50)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        val listState = mViewHolder.songsListView.layoutManager?.onSaveInstanceState()

        if(listState != null) {
            outState.putParcelable(RECYCLER_STATE, listState)
        }
    }

    override fun onResume() {
        super.onResume()

    }

    private fun initSongAdapter() {
        val songsListView = mViewHolder.songsListView
        val dataSet = ArrayList(DatabaseManager.instance.getAllTracks())

        val adapter = SongDBListAdapter(dataSet, context!!)
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
        private val RECYCLER_STATE = "RECYCLER_STATE"

        @JvmStatic
        fun newInstance() =
            SongDatabaseFragment().apply {
                arguments = Bundle().apply {
                }
            }
    }
}
