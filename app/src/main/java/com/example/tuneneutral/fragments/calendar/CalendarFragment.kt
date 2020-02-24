package com.example.tuneneutral.fragments.calendar

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.view.children
import androidx.fragment.app.Fragment
import com.example.tuneneutral.R
import com.example.tuneneutral.Uris
import com.example.tuneneutral.database.DatabaseManager
import com.example.tuneneutral.spotify.SpotifyUtiltiy
import com.example.tuneneutral.utility.DateUtility
import com.kizitonwose.calendarview.CalendarView
import com.kizitonwose.calendarview.model.CalendarDay
import com.kizitonwose.calendarview.model.DayOwner
import com.kizitonwose.calendarview.model.ScrollMode
import com.kizitonwose.calendarview.ui.DayBinder
import com.kizitonwose.calendarview.ui.ViewContainer
import com.kizitonwose.calendarview.utils.yearMonth
import kotlinx.android.synthetic.main.calendar_day_layout.view.*
import kotlinx.android.synthetic.main.fragment_calendar.view.*
import kotlinx.android.synthetic.main.legend_layout.*
import kotlinx.android.synthetic.main.month_header.view.month_title
import org.threeten.bp.LocalDate
import org.threeten.bp.temporal.WeekFields
import java.util.*
import kotlin.math.abs


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

    private class ViewHolder(view: View) {
        val calendarView: CalendarView = view.calendar_view
        val monthTitle: TextView = view.month_title
        val yearTitle: TextView = view.year_title
        val rateTodayButton: Button = view.rate_today_button
        val monthLeftButton: Button = view.month_left_button
        val monthRightButton: Button = view.month_right_button
        val progressBar: ProgressBar = view.progress_bar
    }

    private var param1: Long? = null
    private var listener: OnFragmentInteractionListener? = null
    private var mCurrentDate = DateUtility.now

    private lateinit var mViewHolder: ViewHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getLong(ARG_DATE)
        }
    }

    override fun onStart() {
        super.onStart()

        mViewHolder = ViewHolder(view!!)

        initCalendarView()

        mViewHolder.rateTodayButton.setOnClickListener {
            rateToday()
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

    private fun initCalendarView() {
        fun startLoading() {
            mViewHolder.calendarView.visibility = View.GONE
            mViewHolder.progressBar.visibility = View.VISIBLE
        }

        fun finishLoading() {
            mViewHolder.calendarView.visibility = View.VISIBLE
            mViewHolder.progressBar.visibility = View.GONE
        }

        mViewHolder.monthLeftButton.setOnClickListener {
            mCurrentDate = mCurrentDate.minusMonths(1)
            setMonth(mCurrentDate)
        }

        mViewHolder.monthRightButton.setOnClickListener {
            mCurrentDate = mCurrentDate.plusMonths(1)
            setMonth(mCurrentDate)
        }

        class DayViewContainer(view: View) : ViewContainer(view) {
            val dateText = view.calendar_day_text
            val ratingText = view.rating_text
            val openButton = view.open_button
            val dateBackground = view.date_background
            val dateRatingLine = view.date_rating_line
            val spotifyPlaylistLine = view.spotify_playlist_line
        }

        val minColorInt =  context!!.getColor(R.color.colorNothing)
        val minColor = Color.parseColor(String.format("#%06X", minColorInt))

        mViewHolder.calendarView.dayBinder = object : DayBinder<DayViewContainer> {
            // Called only when a new container is needed.
            override fun create(view: View) = DayViewContainer(view)

            // Called every time we need to reuse a container.
            override fun bind(container: DayViewContainer, day: CalendarDay) {

                fun resetDayVisibility() {
                    container.dateRatingLine.visibility = View.INVISIBLE
                    container.ratingText.visibility = View.INVISIBLE
                    container.spotifyPlaylistLine.visibility = View.INVISIBLE
                }

                val color: Int
                val border: Drawable

                // Set date
                container.dateText.text = day.date.dayOfMonth.toString()

                if(day.owner == DayOwner.THIS_MONTH) {
                    // Set data
                    val dayRating = DatabaseManager.instance.getDayRating(day.date.toEpochDay())

                    if(dayRating != null) {
                        val rating = dayRating.rating

                        container.ratingText.text = rating.toString()
                        container.ratingText.visibility = View.VISIBLE

                        container.dateRatingLine.visibility = View.VISIBLE

                        val maxColorInt = if(rating >= 50) {
                            context!!.getColor(R.color.colorHappy)
                        } else {
                            context!!.getColor(R.color.colorSad)
                        }

                        val maxColor = Color.parseColor(String.format("#%06X", maxColorInt))

                        val percent = abs(0.5f - (rating / 100f)) / 0.5f

                        val resultRed = (minColor.red + percent * (maxColor.red - minColor.red)).toInt()
                        val resultGreen = (minColor.green + percent * (maxColor.green - minColor.green)).toInt()
                        val resultBlue = (minColor.blue + percent * (maxColor.blue - minColor.blue)).toInt()

                        color = Color.rgb(resultRed, resultGreen, resultBlue)

                        if(dayRating.playlistID != "") {
                            container.spotifyPlaylistLine.visibility = View.VISIBLE

                            container.openButton.setOnClickListener {
                                SpotifyUtiltiy.OpenPlaylistInSpotify(context!!, dayRating.playlistID)
                            }
                        }
                    } else {
                        resetDayVisibility()
                        color = context!!.getColor(R.color.colorDeufaltText)
                    }

                    border = if(day.date == DateUtility.now) {
                        context!!.getDrawable(R.drawable.today_background_border)!!
                    } else {
                        context!!.getDrawable(R.drawable.this_month_background_border)!!
                    }

                    val end = day.date.withDayOfMonth(day.date.lengthOfMonth())
                    if(day.date == end) {
                        finishLoading()
                    }

                } else {
                    color = context!!.getColor(R.color.colorDayTextNotActive)

                    border = context!!.getDrawable(R.drawable.other_month_background_border)!!


                    resetDayVisibility()
                }

                container.dateText.setTextColor(color)
                container.ratingText.setTextColor(color)
                container.dateBackground.background = border
            }
        }


        mViewHolder.calendarView.scrollMode = ScrollMode.PAGED
        mViewHolder.calendarView.dayHeight = 250

        setMonth(mCurrentDate)
    }

    private fun setMonth(date: LocalDate) {

        val textID = when(date.month.value) {
            1 -> R.string.month_jan
            2 -> R.string.month_feb
            3 -> R.string.month_mar
            4 -> R.string.month_apl
            5 -> R.string.month_may
            6 -> R.string.month_jun
            7 -> R.string.month_jul
            8 -> R.string.month_aug
            9 -> R.string.month_sep
            10 -> R.string.month_oct
            11 -> R.string.month_nov
            12 -> R.string.month_dec
            else -> throw Exception()
        }

        mViewHolder.monthTitle.text  = getString(textID)
        mViewHolder.yearTitle.text = date.year.toString()

        val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

        val daysOfWeek = arrayOf("sun", "mon", "tue", "wed", "thu", "fri", "sat")
        legend_layout.children.forEachIndexed { index, view ->
            (view as TextView).apply {
                text = daysOfWeek[index]
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            }
        }

        mViewHolder.calendarView.setup(date.yearMonth, date.yearMonth, firstDayOfWeek)
    }

    private fun rateToday() {
        listener?.onFragmentInteraction(Uris.OPEN_RATING_FRAGMENT)
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

        private const val MONDAY = 2
    }
}
