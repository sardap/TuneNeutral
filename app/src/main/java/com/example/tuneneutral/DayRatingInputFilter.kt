package com.example.tuneneutral

import android.text.*

class DayRatingInputFilter : InputFilter  {

    override fun filter(source:CharSequence, start:Int, end:Int, dest: Spanned, dstart:Int, dend:Int): CharSequence? {
        try
        {
            val input = (dest.subSequence(0, dstart).toString() + source + dest.subSequence(dend, dest.length)).toInt()

            if(input > 100)
                return ""

            return null
        }
        catch (nfe:NumberFormatException) {}
        return ""
    }
}