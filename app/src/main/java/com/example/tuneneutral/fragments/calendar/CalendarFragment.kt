package com.example.tuneneutral.fragments.calendar

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.example.tuneneutral.R
import com.example.tuneneutral.SpotifyUtiltiy
import com.example.tuneneutral.Uris
import com.example.tuneneutral.database.DatabaseManager
import com.example.tuneneutral.database.DayRating
import java.util.*
import kotlin.collections.ArrayList

// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_DATE = "ARG_DATE"

/**
 * A simple [Fragment] subclass.
 * Activities that contain this fragment must implement the
 * [CalendarFragment.OnFragmentInteractionListener] interface
 * to handle interaction events.
 * Use the [CalendarFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class CalendarFragment : Fragment() {
    private var param1: Long? = null
    private var listener: OnFragmentInteractionListener? = null

    private lateinit var mRecyclerView: RecyclerView
    private lateinit var mViewAdapter: RecyclerView.Adapter<*>
    private lateinit var mViewManager: RecyclerView.LayoutManager
    private lateinit var mDates : ArrayList<DayRating>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getLong(ARG_DATE)
        }
    }

    override fun onStart() {
        super.onStart()

        mDates = DatabaseManager.instance.getDayRatings()
        mDates.reverse()
        mDates.add(0, DayRating(Calendar.getInstance().timeInMillis, -1, ""))

        mRecyclerView = view!!.findViewById(R.id.date_list)
        mViewAdapter = CalendarListAdatper(mDates,
            object : CalendarListAdatper.OnItemClickListener{
                override fun onItemClick(pos: Int) {
                    SpotifyUtiltiy.OpenPlaylistInSpotify(context!!, mDates[pos].playlistID)
                }
            },
            object : CalendarListAdatper.OnItemClickListener {
                override fun onItemClick(pos: Int) {
                    if(listener != null) {
                        listener?.onFragmentInteraction(Uris.OPEN_RATING_FRAGMENT)
                    }
                }
            }
        )
        mViewManager = LinearLayoutManager(activity)

        mRecyclerView.apply {
            layoutManager = mViewManager

            adapter = mViewAdapter
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_calendar, container, false)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnFragmentInteractionListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement OnFragmentInteractionListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     *
     *
     * See the Android Training lesson [Communicating with Other Fragments]
     * (http://developer.android.com/training/basics/fragments/communicating.html)
     * for more information.
     */
    interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        fun onFragmentInteraction(uri: Uri)
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param date Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment CalendarFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(date: Long) =
            CalendarFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_DATE, date)
                }
            }
    }
}
