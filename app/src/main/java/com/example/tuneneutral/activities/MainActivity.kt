package com.example.tuneneutral.activities

import android.annotation.SuppressLint
import android.app.ActionBar
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.example.tuneneutral.R
import com.example.tuneneutral.Uris
import com.example.tuneneutral.database.DatabaseManager
import com.example.tuneneutral.fragments.RatingFragment
import com.example.tuneneutral.fragments.SongDatabaseFragment
import com.example.tuneneutral.fragments.StatsFragment
import com.example.tuneneutral.fragments.CalendarFragment
import com.example.tuneneutral.fragments.adapters.SongDBListAdapter
import com.example.tuneneutral.playlistGen.PullNewTracks
import com.example.tuneneutral.spotify.SpotifyConstants
import com.example.tuneneutral.spotify.SpotifyUserInfo
import com.example.tuneneutral.utility.DateUtility
import com.google.gson.JsonSyntaxException
import com.spotify.sdk.android.authentication.AuthenticationClient
import com.spotify.sdk.android.authentication.AuthenticationRequest
import com.spotify.sdk.android.authentication.AuthenticationResponse
import kotlinx.android.synthetic.main.activity_main.view.*
import kotlinx.android.synthetic.main.import_database_window.*
import kotlinx.android.synthetic.main.version_info.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.*


class MainActivity : AppCompatActivity(),
    CalendarFragment.OnFragmentInteractionListener,
    RatingFragment.OnFragmentInteractionListener
{
    companion object {
        private const val MOVE_DEFAULT_TIME: Long = 1000 / 5
        private const val FADE_DEFAULT_TIME: Long = 300 / 5
        private const val IMPORT_DATABASE_FILE = 31024

        private const val FRAGMENT_CONTAINER_NAME = "FRAGMENT_CONTAINER_NAME"
    }

    private enum class State {
        ShowingCalander, ShowingRating, ShowingStats, ShowingSongDB, ShowingNone
    }

    private class SpotifyLoginDialogViewHolder(view: Dialog) {
        val spotifyLoginButton = view.findViewById<Button>(R.id.spotify_login_button)
    }

    private class ViewHolder(view: View) {
        val fragmentContainer = view.fragment_container
        val botNavBar = view.bottom_navigation
    }

    private var mState = State.ShowingNone
    private var mSpotifyLoginDialog: Dialog? = null
    private var mPreviousFragment: Fragment? = null

    private lateinit var mSpotifyLoginDialogViewHolder: SpotifyLoginDialogViewHolder
    private lateinit var mFragmentManager: FragmentManager
    private lateinit var mViewHolder: ViewHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mViewHolder = ViewHolder(window.decorView)

        DatabaseManager.instance.giveContext(this)

        when {
            intent?.action == Intent.ACTION_SEND -> {
                if(intent.type == "text/*") {
                    showImportDatabaseDialog(intent)
                }
            }
        }

        mFragmentManager = supportFragmentManager

        if(savedInstanceState != null) {
            mState = State.ShowingSongDB
            mFragmentManager.getFragment(savedInstanceState, FRAGMENT_CONTAINER_NAME)?.also {
                changeFragment(it)
            }
        } else {
            mState = State.ShowingCalander
            refreshFrag()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        mFragmentManager.findFragmentById(R.id.fragment_container)?.also {
            mFragmentManager.putFragment(outState, FRAGMENT_CONTAINER_NAME, it)
        }
    }

    override fun onStart() {
        super.onStart()

        logIntoSpotify()

        initBotNavBar()
    }

    override fun onResume() {
        super.onResume()

        checkSpotifyLogin()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when(requestCode) {
            SpotifyConstants.AUTH_TOKEN_REQUEST_CODE -> {
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
            IMPORT_DATABASE_FILE -> {
                data?.apply {
                    showImportDatabaseDialog(data)
                }
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
                false
            }
            R.id.clear_database -> {
                clearDatabase()
                true
            }
            R.id.pull_songs -> {
                pullSongs()
                true
            }
            R.id.version_info -> {
                showVersionInfo()
                true
            }
            R.id.export_database -> {
                exportDatabase()
                true
            }
            R.id.import_database -> {
                importDatabase()
//                showImportDatabaseDialog()
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
        refreshActivy()
    }

    private fun pullSongs() {
        val accessToken = SpotifyUserInfo.SpotifyAccessToken

        assert(accessToken != null)

        if(accessToken != null) {
            Thread(PullNewTracks(accessToken)).start()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showVersionInfo() {
        class ViewHolder(dialog: Dialog) {
            val versionText = dialog.version_number
            val buildText = dialog.build_number
            val closeButton = dialog.close_button
        }

        val dialog = Dialog(this, android.R.style.ThemeOverlay_Material_Light)
        dialog.setContentView(R.layout.version_info)

        val window = dialog.window
        if(window != null){
            window.setLayout(ActionBar.LayoutParams.WRAP_CONTENT, ActionBar.LayoutParams.WRAP_CONTENT)
            window.setGravity(Gravity.CENTER)
        }

        dialog.window?.attributes?.dimAmount = 0.7f
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        val viewHolder = ViewHolder(dialog)

        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val version = pInfo.versionCode
            viewHolder.versionText.text = "Version Number $version"
            viewHolder.buildText.text = "Base RevisionCode ${pInfo.baseRevisionCode}\n" +
                    "Last Update ${pInfo.lastUpdateTime}\n" +
                    "Version Name ${pInfo.versionName}\n" +
                    "Package Name ${pInfo.packageName}"
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

        viewHolder.closeButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun exportDatabase() {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, DatabaseManager.instance.dbToJson())
            type = "text/json"
        }

        val shareIntent = Intent.createChooser(sendIntent, null)
        startActivity(shareIntent)
    }

    private fun importDatabase() {
        // ACTION_OPEN_DOCUMENT is the intent to choose a file via the system's file
        // browser.
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)

        // Filter to only show results that can be "opened", such as a
        // file (as opposed to a list of contacts or timezones)
        intent.addCategory(Intent.CATEGORY_OPENABLE)

        // Filter to show only images, using the image MIME data type.
        // If one wanted to search for ogg vorbis files, the type would be "audio/ogg".
        // To search for all documents available via installed storage providers,
        // it would be "*/*".
        intent.type = "text/*"

        startActivityForResult(intent, IMPORT_DATABASE_FILE)
    }

    private fun showImportDatabaseDialog(intent: Intent) {
        class ViewHolder(dialog: Dialog) {
            val importDbButton = dialog.Import_button
        }

        val dialog = Dialog(this, android.R.style.ThemeOverlay_Material_Light)
        dialog.setContentView(R.layout.import_database_window)

        val window = dialog.window
        if(window != null){
            window.setLayout(ActionBar.LayoutParams.WRAP_CONTENT, ActionBar.LayoutParams.WRAP_CONTENT)
            window.setGravity(Gravity.CENTER)
        }

        dialog.window?.attributes?.dimAmount = 0.7f
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        val viewHolder = ViewHolder(dialog)


        val data = intent.data
        if(data != null) {
            val inputStream = contentResolver.openInputStream(data) ?: throw Exception("Input stream null")

            val reader = BufferedReader(InputStreamReader(inputStream))

            val jsonStr = StringBuilder()
            for(line in reader.lines()) {
                jsonStr.append(line)
            }

            viewHolder.importDbButton.setOnClickListener {
                try {
                    DatabaseManager.instance.importNewDB(jsonStr.toString())
                    refreshActivy()
                } catch (e: JsonSyntaxException) {
                    Toast.makeText(applicationContext, getString(R.string.error_cannot_import_database_syntax), Toast.LENGTH_SHORT).show()
                } finally {
                    dialog.dismiss()
                }
            }

            dialog.show()
        } else {
            Toast.makeText(dialog.context, getString(R.string.error_cannot_import_database_no_extra), Toast.LENGTH_SHORT).show()
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

    private fun refreshActivy() {
        finish()
        startActivity(intent)
    }

    private fun refreshFrag() {
        when(mState) {
            State.ShowingCalander -> changeToCalandarFragment()
            State.ShowingRating -> changeToRatingFragment()
            State.ShowingStats -> changeToStatsFragment()
            State.ShowingSongDB -> changeToSongDBFragment()
            else -> throw java.lang.RuntimeException("State is not mapped to any fragment")
        }
    }

    private fun initBotNavBar() {
        mViewHolder.botNavBar.setOnNavigationItemSelectedListener {
            mState = when(it.itemId) {
                R.id.menu_nav_cal -> State.ShowingCalander
                R.id.menu_nav_stats -> State.ShowingStats
                R.id.menu_nav_rating -> State.ShowingRating
                R.id.menu_nav_songs_db -> State.ShowingSongDB
                else -> throw java.lang.RuntimeException("Unknwon Menu option")
            }
            refreshFrag()
            true
        }
    }

    private fun changeToSongDBFragment() {
        changeFragment(SongDatabaseFragment.newInstance())
    }

    private fun changeToStatsFragment() {
        changeFragment(StatsFragment.newInstance())
    }

    private fun changeToRatingFragment() {
        changeFragment(RatingFragment.newInstance())
    }

    private fun changeToCalandarFragment() {
        changeFragment(CalendarFragment.newInstance(Calendar.getInstance().timeInMillis))
    }

    private fun changeFragment(nextFragment: Fragment) {
        val currentFragment = mFragmentManager.findFragmentById(R.id.fragment_container)
        val previousFragment = mPreviousFragment

        if(previousFragment != null) {
            val removeTransaction = mFragmentManager.beginTransaction()
            removeTransaction.remove(previousFragment)
            removeTransaction.commit()
        }

        val transaction = mFragmentManager.beginTransaction()

        if(currentFragment != null) {
            // TODO Improve so no duplcation
            if(currentFragment is CalendarFragment) {
                if(nextFragment is RatingFragment) {
                    transaction.setCustomAnimations(R.animator.slide_in_left, R.animator.slide_out_right, 0, 0)
                } else if (nextFragment is StatsFragment) {
                    transaction.setCustomAnimations(R.animator.slide_in_right, R.animator.slide_out_left, 0, 0)
                }
            } else if(currentFragment is RatingFragment && nextFragment is CalendarFragment) {
                transaction.setCustomAnimations(R.animator.slide_in_right, R.animator.slide_out_left, 0, 0)
            } else if(currentFragment is StatsFragment && nextFragment is CalendarFragment) {
                transaction.setCustomAnimations(R.animator.slide_in_left, R.animator.slide_out_right, 0, 0)
            }

            mPreviousFragment = currentFragment
        }


        transaction.replace(R.id.fragment_container, nextFragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }
}
