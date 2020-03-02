package com.example.tuneneutral.fragments

import android.app.ActionBar
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.DatePicker
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.tuneneutral.*
import com.example.tuneneutral.MiscConsts.NEUTRALISE_PLAYLIST_MESSAGE
import com.example.tuneneutral.playlistGen.GenrateNeutralisedPlaylist
import com.example.tuneneutral.database.DatabaseManager
import com.example.tuneneutral.spotify.SpotifyUserInfo
import com.example.tuneneutral.utility.DateUtility
import kotlinx.android.synthetic.main.fragment_rating.view.*
import org.threeten.bp.LocalDate


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

    private class DialogViewHolder(dialog: Dialog) {
        val mMessageText = dialog.findViewById<TextView>(R.id.loading_message)
    }

    private class ViewHolder(view: View) {
        val ratingText: TextView = view.rating_text
        val ratingSeekBar: SeekBar = view.rating_input_seekbar
        val neutralisedButton: Button = view.neutralise_button
        val datePicker: DatePicker = view.debug_date_picker
    }

    private var mState = State.WaitingInput
    private var mListener: OnFragmentInteractionListener? = null

    private lateinit var mReceiver: MyReceiver

    private lateinit var mDialog: Dialog
    private lateinit var mDialogViewHolder: DialogViewHolder
    private lateinit var mViewHolder: ViewHolder

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
        return inflater.inflate(R.layout.fragment_rating, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()

        mViewHolder = ViewHolder(view!!)

        mViewHolder.ratingSeekBar.setOnSeekBarChangeListener(seekBarChangeListener)

        updateProgressText()

        mViewHolder.neutralisedButton.setOnClickListener {
            neutraliseClicked()
        }

        mViewHolder.datePicker.visibility = if(DatabaseManager.instance.getUserSettings().debugMode) {
            View.VISIBLE
        } else {
            View.GONE
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
            mListener = context
        } else {
            throw RuntimeException("$context must implement OnFragmentInteractionListener")
        }
    }

    private fun changeState(newState: State) {
        if (newState == State.Complete) {
            mListener?.onFragmentInteraction(Action.Complete)
        }

        mState = newState
    }

    private fun updateProgressText() {
        mViewHolder.ratingText.text = getString(R.string.default_rating, mViewHolder.ratingSeekBar.progress)
    }

    private fun neutraliseClicked() {
        changeState(State.GenPlaylist)

        val currentValence = mViewHolder.ratingSeekBar.progress / 100f

        mDialog = Dialog(context!!, R.style.AppThemeDarkMode)
        mDialog.setContentView(R.layout.creating_playlist_popup)

        val window = mDialog .window
        if(window != null){
            window.setLayout(ActionBar.LayoutParams.WRAP_CONTENT, ActionBar.LayoutParams.WRAP_CONTENT)
            window.setGravity(Gravity.CENTER)
            window.setBackgroundDrawable(ColorDrawable(Color.argb(100, 0, 0, 0)))
        }

        mDialog.window?.attributes?.dimAmount = 0.7f
        mDialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        val timeStamp = if(DatabaseManager.instance.getUserSettings().debugMode) {
            LocalDate.of(mViewHolder.datePicker.year, mViewHolder.datePicker.month + 1, mViewHolder.datePicker.dayOfMonth).toEpochDay()
        } else {
            DateUtility.todayEpoch
        }

        mDialogViewHolder = DialogViewHolder(mDialog)

        val generateNeutralised =
            GenrateNeutralisedPlaylist(
                SpotifyUserInfo.SpotifyAccessToken!!,
                currentValence,
                context!!,
                timeStamp
            )

        val neutraliseThread = Thread(generateNeutralised)

        neutraliseThread.start()
        mDialog.show()
    }

    private class MyReceiver(private val ratingFragment: RatingFragment) : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val message = intent.getSerializableExtra(NEUTRALISE_PLAYLIST_MESSAGE)

            val stringId = when (message) {
                NeutralisePlaylistMessage.PullingSongs -> R.string.loading_pulling_songs
                NeutralisePlaylistMessage.Anaylsing -> R.string.loading_anayalsing
                NeutralisePlaylistMessage.Calcualting -> R.string.loading_calcauting
                else -> null
            }

            if(stringId != null) {
                ratingFragment.mDialogViewHolder.mMessageText.text = context.getString(stringId)
            }

            if(message == NeutralisePlaylistMessage.Complete) {
                ratingFragment.mDialog.dismiss()
                ratingFragment.changeState(State.Complete)
            }
        }
    }
}
