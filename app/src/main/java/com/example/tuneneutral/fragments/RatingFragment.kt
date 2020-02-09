package com.example.tuneneutral.fragments

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.tuneneutral.*
import com.example.tuneneutral.MiscConsts.NEUTRALISE_PLAYLIST_MESSAGE
import com.example.tuneneutral.database.DatabaseManager
import com.example.tuneneutral.database.DateInfo
import java.util.*


class RatingFragment : Fragment() {

    companion object {
        fun newInstance() = RatingFragment()
    }

    enum class Action {
        Complete
    }

    interface OnFragmentInteractionListener {
        fun onFragmentInteraction(action: Action)
    }

    private enum class State {
        WaitingInput, GenPlaylist, Complete
    }

    private var mState = State.WaitingInput
    private var listener: OnFragmentInteractionListener? = null

    private lateinit var viewModel: RatingViewModel
    private lateinit var mReceiver: MyReceiver

    private lateinit var mRatingSeekBar: SeekBar
    private lateinit var mRatingText: TextView
    private lateinit var mNeutralisedButton: Button

    private val seekBarChangeListener: OnSeekBarChangeListener = object : OnSeekBarChangeListener {
        override fun onProgressChanged(
            seekBar: SeekBar,
            progress: Int,
            fromUser: Boolean
        ) { // updated continuously as the user slides the thumb
            updateProgressText()
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) { // called when the user first touches the SeekBar

        }

        override fun onStopTrackingTouch(seekBar: SeekBar) { // called after the user finishes moving the SeekBar
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.rating_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProviders.of(this).get(RatingViewModel::class.java)
    }

    override fun onStart() {
        super.onStart()

        mRatingSeekBar = view!!.findViewById(R.id.rating_input_seekbar)
        mRatingText = view!!.findViewById(R.id.rating_text)
        mNeutralisedButton = view!!.findViewById(R.id.neutralise_button)

        mRatingSeekBar.setOnSeekBarChangeListener(seekBarChangeListener)

        updateProgressText()

        mNeutralisedButton.setOnClickListener {
            neutraliseClicked()
        }
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(context!!).unregisterReceiver(mReceiver)
    }

    override fun onResume() {
        mReceiver =
            MyReceiver(this)
        LocalBroadcastManager.getInstance(context!!).registerReceiver(mReceiver, IntentFilter(NEUTRALISE_PLAYLIST_MESSAGE))
        super.onResume()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnFragmentInteractionListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement OnFragmentInteractionListener")
        }
    }


    private fun changeState(newState: State) {
        if(newState == State.WaitingInput) {
            mNeutralisedButton.visibility = View.VISIBLE
        } else if(newState == State.GenPlaylist) {
            mNeutralisedButton.visibility = View.GONE
        } else if (newState == State.Complete) {
            listener?.onFragmentInteraction(Action.Complete)
        }

        mState = newState
    }

    private fun updateProgressText() {
        mRatingText.text = getString(R.string.default_rating, mRatingSeekBar.progress)
    }

    private fun neutraliseClicked() {
        changeState(State.GenPlaylist)

        val currentValence = mRatingSeekBar.progress / 100f

        val generateNeutralised =
            GenrateNeutralisedPlaylist(
                SpotifyUserInfo.SpotifyAccessToken!!,
                currentValence,
                context!!
            )

        val neutraliseThread = Thread(generateNeutralised)


        neutraliseThread.start()
    }

    private class MyReceiver(private val ratingFragment: RatingFragment) : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val message = intent.getSerializableExtra(NEUTRALISE_PLAYLIST_MESSAGE)

            val stringId = when (message) {
                NeutralisePlaylistMessage.PullingSongs -> R.string.loading_pulling_songs
                NeutralisePlaylistMessage.Anaylsing -> R.string.loading_anayalsing
                NeutralisePlaylistMessage.Calcualting -> R.string.loading_calcauting
                NeutralisePlaylistMessage.CompletePlaylistCreated -> R.string.loading_compelte_playlist
                NeutralisePlaylistMessage.CompleteNoPlaylist-> R.string.loading_compelte_no_playlist
                else -> throw RuntimeException()
            }

            Toast.makeText(context, context.getString(stringId), Toast.LENGTH_LONG).show()

            if (message == NeutralisePlaylistMessage.CompletePlaylistCreated) {
                val playlistID = intent.getStringExtra(MiscConsts.NEUTRALISE_PLAYLIST_ID)
                if (playlistID != null) {
                    DatabaseManager.instance.addDateInfo(
                        DateInfo(
                            Calendar.getInstance().timeInMillis,
                            ratingFragment.mRatingSeekBar.progress,
                            playlistID
                        )
                    )
                }

                ratingFragment.changeState(State.Complete)
            }

            if (message == NeutralisePlaylistMessage.CompleteNoPlaylist) {
                DatabaseManager.instance.addDateInfo(
                    DateInfo(
                        Calendar.getInstance().timeInMillis,
                        ratingFragment.mRatingSeekBar.progress,
                        ""
                    )
                )

                ratingFragment.changeState(State.Complete)
            }
        }
    }
}
