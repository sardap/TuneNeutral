package com.example.tuneneutral.fragments.calendar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tuneneutral.R
import com.example.tuneneutral.database.DayRating
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*


class CalendarListAdatper(
    private val mDataSet: ArrayList<DayRating>,
    private val mPlaylist: OnItemClickListener,
    private val mNewEntry: OnItemClickListener
    ) :
    RecyclerView.Adapter<CalendarListAdatper.ViewHolder>()
{

    interface OnItemClickListener {
        fun onItemClick(pos: Int)
    }

    private object ViewTypes {
        const val COMEPLETE = 0
        const val NO_PLAYLIST = 1
        const val TODAY = 2
    }

    val mDateFormat = DateFormat.getDateInstance()


    // This seems crazy but fuck it
    interface RatingSubtitle {
        val ratingTextView: TextView
    }

    abstract class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleTextView: TextView = view.findViewById(R.id.title)
    }

    class DateViewTodayHolder(view: View) : ViewHolder(view) {
        val createNewEntryButton: Button = view.findViewById(R.id.button_rate_today)
    }

    class DateViewHolderNoPlaylist(view: View) : ViewHolder(view), RatingSubtitle {
        override val ratingTextView: TextView = view.findViewById(R.id.rating_text)
    }

    class DateViewHolderComplete(view: View) : ViewHolder(view), RatingSubtitle {
        val viewPlaylistButton: Button = view.findViewById(R.id.view_playlist_button)
        override val ratingTextView: TextView = view.findViewById(R.id.rating_text)
    }

    override fun getItemViewType(position: Int): Int {
        if(mDataSet[position].rating == -1) {
            return ViewTypes.TODAY
        }

        if(mDataSet[position].playlistID == "") {
            return ViewTypes.NO_PLAYLIST
        }

        return ViewTypes.COMEPLETE
    }


    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

        return when(viewType) {
            ViewTypes.COMEPLETE -> DateViewHolderComplete(LayoutInflater.from(parent.context).inflate(R.layout.date_view_complete, parent, false))
            ViewTypes.NO_PLAYLIST -> DateViewHolderNoPlaylist(LayoutInflater.from(parent.context).inflate(R.layout.date_view_no_playlist, parent, false))
            ViewTypes.TODAY -> DateViewTodayHolder(LayoutInflater.from(parent.context).inflate(R.layout.date_view_today, parent, false))
            else -> throw RuntimeException()
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val currentElement = mDataSet[position]

        val date = Date(currentElement.timestamp)
        val formattedDate = mDateFormat.format(date)

        val sdf = SimpleDateFormat("EEEE")
        val dayString: String = sdf.format(date)

        holder.titleTextView.text = String.format(holder.titleTextView.text.toString(), dayString, formattedDate)

        if(holder is RatingSubtitle) {
            holder.ratingTextView.text = String.format(holder.ratingTextView.text.toString(), currentElement.rating)
        }

        if(holder is DateViewHolderComplete) {
            holder.viewPlaylistButton.setOnClickListener {
                mPlaylist.onItemClick(position)
            }
        }

        if(holder is DateViewTodayHolder) {
            holder.createNewEntryButton.setOnClickListener {
                mNewEntry.onItemClick(position)
            }
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = mDataSet.size
}