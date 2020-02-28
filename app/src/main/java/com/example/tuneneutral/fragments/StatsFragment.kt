package com.example.tuneneutral.fragments


import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import com.example.tuneneutral.R
import com.example.tuneneutral.database.DatabaseManager
import com.example.tuneneutral.database.DayRating
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.android.synthetic.main.fragment_stats.view.*
import java.time.LocalDate
import java.time.ZoneId
import java.util.*
import kotlin.collections.ArrayList


/**
 * A simple [Fragment] subclass.
 * Use the [StatsFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class StatsFragment : Fragment() {
    private class ViewHolder(view: View) {
        val lineChart: LineChart = view.line_chart
        val startDateButton: Button = view.start_date_button
        val startDateText: TextView = view.start_date_text
        val endDateButton: Button = view.end_date_button
        val endDateText: TextView = view.end_date_text
    }

    private lateinit var mViewHolder: ViewHolder
    private lateinit var mDateFormater: java.text.DateFormat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_stats, container, false)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onResume() {
        super.onResume()

        mDateFormater = DateFormat.getDateFormat(context!!)

        mViewHolder = ViewHolder(view!!)

        val startDatePickerDialog = createDatePickerDialog(mViewHolder.startDateText, -7)
        mViewHolder.startDateButton.setOnClickListener {
            startDatePickerDialog.show()
        }

        val endDatePickerDialog = createDatePickerDialog(mViewHolder.endDateText, 0)
        mViewHolder.endDateButton.setOnClickListener {
            endDatePickerDialog.show()
        }

        dateSelectorsChanged()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun dateSelectorsChanged() {
        val startDate = mDateFormater
            .parse(mViewHolder.startDateText.text.toString())
            ?.toInstant()
            ?.atZone(ZoneId.systemDefault())
            ?.toLocalDate()
            ?.toEpochDay()

        val endDate = mDateFormater
            .parse(mViewHolder.endDateText.text.toString())
            ?.toInstant()
            ?.atZone(ZoneId.systemDefault())
            ?.toLocalDate()
            ?.toEpochDay()

        if(startDate != null && endDate != null) {
            val dates = DatabaseManager.instance.getDayRatingsInRange(startDate, endDate)

            initLineChart(dates)
        }
    }

    private fun initLineChart(dates: List<DayRating>) {
        val lineChart = mViewHolder.lineChart

        val lineData = LineData()
        val entries = ArrayList<Entry>()


        for(date in dates) {
            val epochDay = date.timestamp.toFloat()
            entries.add(Entry(epochDay, date.rating.toFloat()))
        }

        val lineDataSet = LineDataSet(entries , "test")
        lineDataSet.color = Color.GRAY
        lineDataSet.lineWidth = 3f
        lineDataSet.circleRadius = 7f
        lineDataSet.setDrawFilled(true)
        lineDataSet.valueTextSize = 15f

        lineData.addDataSet(lineDataSet)

        lineChart.data = lineData

        val xAxis = lineChart.xAxis

        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return mDateFormater.format(
                    Date.from(
                        LocalDate.ofEpochDay(value.toLong())
                            .atStartOfDay()
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                    )
                )
            }
        }

        xAxis.axisMinimum = dates.first().timestamp.toFloat()
        xAxis.axisMaximum = dates.last().timestamp.toFloat()
        xAxis.labelCount = (dates.last().timestamp - dates.first().timestamp).toInt()
        xAxis.labelRotationAngle = 0.5f
        xAxis.textSize = 12f
        xAxis.labelRotationAngle = 50f
        xAxis.setDrawAxisLine(true)
        xAxis.setDrawGridLines(true)

        lineChart.notifyDataSetChanged()
        lineChart.invalidate()
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun createDatePickerDialog(textView: TextView, dayOffset: Int): DatePickerDialog {
        val currentDate = Calendar.getInstance()
        currentDate.add(Calendar.DAY_OF_MONTH, dayOffset)

        val result = DatePickerDialog(
            context!!,
            DatePickerDialog.OnDateSetListener { view, year, month, dayOfMonth ->
                val calendar = Calendar.getInstance()
                calendar.set(year, month, dayOfMonth)
                val dateRepresentation = mDateFormater.format(calendar.time)

                textView.text = dateRepresentation.toString()
                dateSelectorsChanged()
            },
            currentDate.get(Calendar.YEAR),
            currentDate.get(Calendar.MONTH),
            currentDate.get(Calendar.DAY_OF_MONTH)
        )
        result.setTitle(getString(R.string.select_date))

        textView.text = mDateFormater.format(currentDate.time)

        return result
    }


    companion object {
        @JvmStatic
        fun newInstance() =
            StatsFragment().apply {
                arguments = Bundle().apply {
                }
            }
    }
}
