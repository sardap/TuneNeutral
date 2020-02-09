package com.example.tuneneutral.fragments.calendar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tuneneutral.R
import com.example.tuneneutral.database.DateInfo
import java.text.DateFormat
import java.util.*


class CalendarListAdatper(private val mDataSet: ArrayList<DateInfo>, private val mOnItemClick: OnItemClickListener) : RecyclerView.Adapter<CalendarListAdatper.ViewHolder>() {

    public interface OnItemClickListener {
        fun onItemClick(pos: Int)
    }

    private object ViewTypes {
        const val COMEPLETE = 0
        const val NO_PLAYLIST = 1
    }

    open class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleTextView: TextView = view.findViewById(R.id.title)
        val ratingTextView: TextView = view.findViewById(R.id.rating_text)
    }

    class DateViewHolderComplete(view: View) : ViewHolder(view) {
        val viewPlaylistButton: Button = view.findViewById(R.id.view_playlist_button)
    }

    override fun getItemViewType(position: Int): Int {
        var result = ViewTypes.COMEPLETE
        if(mDataSet[position].playlistID == "") {
            result = ViewTypes.NO_PLAYLIST
        }
        return result
    }


    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

        return when(viewType) {
            ViewTypes.COMEPLETE -> DateViewHolderComplete(LayoutInflater.from(parent.context).inflate(R.layout.date_view_complete, parent, false))
            ViewTypes.NO_PLAYLIST -> ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.date_view_no_playlist, parent, false))
            else -> throw RuntimeException()
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val currentElement = mDataSet[position]

        val c = Date(currentElement.timestamp)
        val df = DateFormat.getDateInstance()
        val formattedDate = df.format(c)

        holder.titleTextView.text = String.format(holder.titleTextView.text.toString(), formattedDate)
        holder.ratingTextView.text = String.format(holder.ratingTextView.text.toString(), currentElement.rating)

        if(holder is DateViewHolderComplete) {
            holder.viewPlaylistButton.setOnClickListener {
                mOnItemClick.onItemClick(position)
            }
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = mDataSet.size
}