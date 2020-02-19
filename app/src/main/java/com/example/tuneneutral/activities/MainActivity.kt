package com.example.tuneneutral.activities

import android.app.ActionBar
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.example.tuneneutral.R
import com.example.tuneneutral.Uris
import com.example.tuneneutral.database.Database
import com.example.tuneneutral.database.DatabaseManager
import com.example.tuneneutral.fragments.RatingFragment
import com.example.tuneneutral.fragments.StatusBar
import com.example.tuneneutral.fragments.calendar.CalendarFragment
import com.example.tuneneutral.playlistGen.PullNewTracks
import com.example.tuneneutral.spotify.SpotifyConstants
import com.example.tuneneutral.spotify.SpotifyUserInfo
import com.example.tuneneutral.utility.DateUtility
import com.spotify.sdk.android.authentication.AuthenticationClient
import com.spotify.sdk.android.authentication.AuthenticationRequest
import com.spotify.sdk.android.authentication.AuthenticationResponse
import java.util.*


class MainActivity : AppCompatActivity(),
    CalendarFragment.OnFragmentInteractionListener,
    RatingFragment.OnFragmentInteractionListener
{
    companion object {
        private const val MOVE_DEFAULT_TIME: Long = 1000 / 5
        private const val FADE_DEFAULT_TIME: Long = 300 / 5
    }

    private enum class State {
        ShowingCalander, ShowingRating
    }

    private class SpotifyLoginDialogViewHolder(view: Dialog) {
        val spotifyLoginButton = view.findViewById<Button>(R.id.spotify_login_button)
    }

    private class StatusBarViewHolder(view: View) {
        val progressBar = view.findViewById<ProgressBar>(R.id.progress_bar)
        val loadingMessage = view.findViewById<TextView>(R.id.loading_message)
    }

    private class ViewHolder(view: View) {
        val fragmentContainer = view.findViewById<FrameLayout>(R.id.fragment_container)
        val statisBarViewHolder = StatusBarViewHolder(view)
    }

    private var mState = State.ShowingCalander
    private var mSpotifyLoginDialog: Dialog? = null

    private lateinit var mSpotifyLoginDialogViewHolder: SpotifyLoginDialogViewHolder
    private lateinit var mStatusBarFrameLayout: FrameLayout
    private lateinit var mFragmentManager: FragmentManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        DatabaseManager.instance.giveContext(this)
    }

    override fun onStart() {
        super.onStart()

        mStatusBarFrameLayout = findViewById(R.id.fragment_status_bar)

        mFragmentManager = supportFragmentManager

        logIntoSpotify()

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

                val lastPull = DatabaseManager.instance.getLastPullTime()

                if(DatabaseManager.instance.getAllTracks().count() < 20 || lastPull == null || lastPull < DateUtility.todayEpoch) {
                    pullSongs()
                }


            } else {
                initloginWindow()
                checkSpotifyLogin()

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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)

        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)

        val debugItem = menu?.findItem(R.id.enable_debug)

        if(debugItem != null) {
            debugItem.isChecked = DatabaseManager.instance.getUserSettings().debugMode
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.enable_debug -> {
                item.isChecked = toggleDebug()
                true
            }
            R.id.clear_database -> {
                clearDatabase()
                true
            }
            R.id.pull_songs -> {
                pullSongs()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun toggleDebug(): Boolean {
        val userSettings = DatabaseManager.instance.getUserSettings()
        userSettings.debugMode = !userSettings.debugMode
        DatabaseManager.instance.setUserSettings()
        return userSettings.debugMode
    }

    private fun clearDatabase() {
        DatabaseManager.instance.clearDatabase()
        finish()
        startActivity(intent)
    }

    private fun pullSongs() {
        val accessToken = SpotifyUserInfo.SpotifyAccessToken

        assert(accessToken != null)

        if(accessToken != null) {
            Thread(PullNewTracks(accessToken)).start()
        }
    }

    private fun initloginWindow() {

        val dialog = Dialog(this, android.R.style.ThemeOverlay_Material_Light)
        dialog.setContentView(R.layout.login_to_spotify_dialog)

        val window = dialog.window
        if(window != null){
            window.setLayout(ActionBar.LayoutParams.WRAP_CONTENT, ActionBar.LayoutParams.WRAP_CONTENT)
            window.setGravity(Gravity.BOTTOM)
        }

        dialog.window?.attributes?.dimAmount = 0.7f
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        mSpotifyLoginDialogViewHolder = SpotifyLoginDialogViewHolder(
            dialog
        )

        mSpotifyLoginDialogViewHolder.spotifyLoginButton.setOnClickListener {
            logIntoSpotify()
            dialog.dismiss()
        }

        dialog.setCanceledOnTouchOutside(false)

        mSpotifyLoginDialog = dialog
    }

    private fun checkSpotifyLogin(): Boolean {
        val timeGotten = SpotifyUserInfo.TimeGotten

        if(
            mSpotifyLoginDialog != null && !mSpotifyLoginDialog!!.isShowing && SpotifyUserInfo.SpotifyAccessToken == null ||
            timeGotten != null && timeGotten + 3600000 < Calendar.getInstance().timeInMillis
        ) {
            openSpotifyLoginDialog()
            return true
        }

        return false
    }

    private fun openSpotifyLoginDialog() {
        mSpotifyLoginDialog?.show()
    }

    private fun getAuthenticationRequest(type: AuthenticationResponse.Type): AuthenticationRequest {
        return AuthenticationRequest.Builder(
            SpotifyConstants.CLIENT_ID, type,
            SpotifyConstants.REDIRECT_URI
        )
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

    private fun initStatusBar() {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_status_bar, StatusBar.newInstance())
        transaction.commit()
    }

    private fun showStatusBar() {

    }

    private fun changeToRatingFragment() {
        changeFragment(RatingFragment.newInstance())
    }

    private fun changeToCalandarFragment() {
        changeFragment(CalendarFragment.newInstance(Calendar.getInstance().timeInMillis))
    }

    private fun changeFragment(nextFragment: Fragment) {
        val previousFragment = mFragmentManager.findFragmentById(R.id.fragment_container)

        val transaction = mFragmentManager.beginTransaction()

        if(previousFragment != null) {
            if(previousFragment is CalendarFragment) {
                transaction.setCustomAnimations(R.animator.slide_in_left, R.animator.slide_out_right, 0, 0)
            } else {
                transaction.setCustomAnimations(R.animator.slide_in_right, R.animator.slide_out_left, 0, 0)
            }
        }


        transaction.replace(R.id.fragment_container, nextFragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }
}
