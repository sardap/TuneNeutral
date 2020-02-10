package com.example.tuneneutral

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.fragment.app.Fragment
import com.example.tuneneutral.database.DatabaseManager
import com.example.tuneneutral.fragments.LoginFragment
import com.example.tuneneutral.fragments.RatingFragment
import com.example.tuneneutral.fragments.calendar.CalendarFragment
import com.spotify.sdk.android.authentication.AuthenticationClient
import java.lang.RuntimeException
import java.util.*

class MainActivity : AppCompatActivity(),
    CalendarFragment.OnFragmentInteractionListener,
    RatingFragment.OnFragmentInteractionListener
{

    private enum class State {
        ShowingCalander, ShowingRating
    }

    private var mState = State.ShowingCalander

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        DatabaseManager.instance.giveContext(this)
    }

    override fun onStart() {
        super.onStart()

        when(mState) {
            State.ShowingCalander -> changeToCalandarFragment()
            State.ShowingRating -> changeToRatingFragment()
        }
    }

    override fun onResume() {
        super.onResume()

        val timeGotten = SpotifyUserInfo.TimeGotten
        if(SpotifyUserInfo.SpotifyAccessToken == null || timeGotten != null && timeGotten + 3600000 < Calendar.getInstance().timeInMillis) {
            changeToLoginFragment()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (SpotifyConstants.AUTH_TOKEN_REQUEST_CODE == requestCode) {
            val response = AuthenticationClient.getResponse(resultCode, data)

            SpotifyUserInfo.SpotifyAccessToken = response.accessToken
            SpotifyUserInfo.TimeGotten = Calendar.getInstance().timeInMillis

            changeToCalandarFragment()
        }
    }

    override fun onFragmentInteraction(uri: Uri) {
        when(uri) {
            Uris.OPEN_RATING_FRAGMENT -> changeToRatingFragment()
            else -> throw RuntimeException()
        }
    }

    override fun onFragmentInteraction(action: RatingFragment.Action) {
        when(action) {
            RatingFragment.Action.Complete -> changeToCalandarFragment()
        }
    }

    private fun changeToRatingFragment() {
        changeFragment(RatingFragment.newInstance())
    }

    private fun changeToCalandarFragment() {
        changeFragment(CalendarFragment.newInstance(Calendar.getInstance().timeInMillis))
    }

    private fun changeToLoginFragment() {
        changeFragment(LoginFragment.newInstance())
    }

    private fun changeFragment(fragment: Fragment) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }
}
