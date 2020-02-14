package com.example.tuneneutral

import android.app.ActionBar
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.tuneneutral.database.DatabaseManager
import com.example.tuneneutral.fragments.RatingFragment
import com.example.tuneneutral.fragments.calendar.CalendarFragment
import com.example.tuneneutral.playlistGen.PullNewTracks
import com.spotify.sdk.android.authentication.AuthenticationClient
import com.spotify.sdk.android.authentication.AuthenticationRequest
import com.spotify.sdk.android.authentication.AuthenticationResponse
import java.util.*


class MainActivity : AppCompatActivity(),
    CalendarFragment.OnFragmentInteractionListener,
    RatingFragment.OnFragmentInteractionListener
{

    private enum class State {
        ShowingCalander, ShowingRating
    }

    private class SpotifyLoginDialogViewHolder(view: Dialog) {
        val mSpotifyLoginButton = view.findViewById<Button>(R.id.spotify_login_button)
    }

    private var mState = State.ShowingCalander
    private lateinit var mSpotifyLoginDialog: Dialog
    private lateinit var mSpotifyLoginDialogViewHolder: SpotifyLoginDialogViewHolder
    private lateinit var mPullSongsThread: Thread

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        DatabaseManager.instance.giveContext(this)
    }

    override fun onStart() {
        super.onStart()

        mSpotifyLoginDialog = Dialog(this, android.R.style.ThemeOverlay_Material_Light)
        mSpotifyLoginDialog.setContentView(R.layout.login_to_spotify_dialog)

        val window = mSpotifyLoginDialog.window
        if(window != null){
            window.setLayout(ActionBar.LayoutParams.WRAP_CONTENT, ActionBar.LayoutParams.WRAP_CONTENT)
            window.setGravity(Gravity.BOTTOM)
        }

        mSpotifyLoginDialog.window?.attributes?.dimAmount = 0.7f
        mSpotifyLoginDialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        mSpotifyLoginDialogViewHolder = SpotifyLoginDialogViewHolder(mSpotifyLoginDialog)

        mSpotifyLoginDialogViewHolder.mSpotifyLoginButton.setOnClickListener {
            logIntoSpotify()
            mSpotifyLoginDialog.dismiss()
        }

        mSpotifyLoginDialog.setCanceledOnTouchOutside(false)

        when(mState) {
            State.ShowingCalander -> changeToCalandarFragment()
            State.ShowingRating -> changeToRatingFragment()
        }
    }

    override fun onResume() {
        super.onResume()

        checkSpotifyLogin()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (SpotifyConstants.AUTH_TOKEN_REQUEST_CODE == requestCode) {
            val response = AuthenticationClient.getResponse(resultCode, data)

            if(response.accessToken != null) {
                SpotifyUserInfo.SpotifyAccessToken = response.accessToken
                SpotifyUserInfo.TimeGotten = Calendar.getInstance().timeInMillis

                mPullSongsThread = Thread(PullNewTracks(SpotifyUserInfo.SpotifyAccessToken!!))
                mPullSongsThread.start()

                changeToCalandarFragment()
            } else {
                val toast = Toast.makeText(this, "Unable to Login into Spotify Error:${response.error}", Toast.LENGTH_LONG)
                toast.setGravity(Gravity.CENTER, 0, 0)
                toast.show()
            }
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

    private fun checkSpotifyLogin(): Boolean {
        val timeGotten = SpotifyUserInfo.TimeGotten
        if(
            !mSpotifyLoginDialog.isShowing && SpotifyUserInfo.SpotifyAccessToken == null ||
            timeGotten != null && timeGotten + 3600000 < Calendar.getInstance().timeInMillis
        ) {
            openSpotifyLoginDialog()
            return true
        }

        return false
    }

    private fun openSpotifyLoginDialog() {
        mSpotifyLoginDialog.show()
    }

    private fun getAuthenticationRequest(type: AuthenticationResponse.Type): AuthenticationRequest {
        return AuthenticationRequest.Builder(SpotifyConstants.CLIENT_ID, type, SpotifyConstants.REDIRECT_URI)
            .setShowDialog(false)
            .setScopes(arrayOf("user-top-read", "playlist-read-private", "playlist-modify-private", "user-read-private"))
            .build()
    }

    private fun logIntoSpotify() {
        val request = getAuthenticationRequest(AuthenticationResponse.Type.TOKEN)

        AuthenticationClient.openLoginActivity(
            this,
            SpotifyConstants.AUTH_TOKEN_REQUEST_CODE,
            request
        )
    }

    private fun changeToRatingFragment() {
        changeFragment(RatingFragment.newInstance())
    }

    private fun changeToCalandarFragment() {
        changeFragment(CalendarFragment.newInstance(Calendar.getInstance().timeInMillis))
    }

    private fun changeFragment(fragment: Fragment) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }
}
