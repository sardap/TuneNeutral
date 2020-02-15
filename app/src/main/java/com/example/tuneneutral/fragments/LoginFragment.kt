package com.example.tuneneutral.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.example.tuneneutral.*
import com.example.tuneneutral.spotify.SpotifyConstants

import com.spotify.sdk.android.authentication.AuthenticationClient
import com.spotify.sdk.android.authentication.AuthenticationRequest
import com.spotify.sdk.android.authentication.AuthenticationResponse

/**
 * A simple [Fragment] subclass.
 * Activities that contain this fragment must implement the
 * [LoginFragment.OnFragmentInteractionListener] interface
 * to handle interaction events.
 * Use the [LoginFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class LoginFragment : Fragment() {
    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment LoginFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance() = LoginFragment().apply {}
    }

    private lateinit var mSpotifyLoginButton: Button

    override fun onStart() {
        super.onStart()

        mSpotifyLoginButton = view!!.findViewById(R.id.spotify_login_button)

        mSpotifyLoginButton.setOnClickListener {
            onButtonPressed()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    fun onButtonPressed() {
        logIntoSpotify()
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
            activity,
            SpotifyConstants.AUTH_TOKEN_REQUEST_CODE,
            request
        )
    }
}
