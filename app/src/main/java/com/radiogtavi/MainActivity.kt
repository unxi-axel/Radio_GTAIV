package com.radiogtavi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse

class MainActivity : ComponentActivity() {

    private val vm: RadioViewModel by viewModels()
    private val REQ = 1337

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm.startIfPossible()
        setContent { RadioScreen(vm, onLogin = { login() }) }
    }

    private fun login() {
    val req = AuthorizationRequest.Builder(
        RadioViewModel.CLIENT_ID,
        AuthorizationResponse.Type.TOKEN,
        RadioViewModel.REDIRECT_URI
    )
    override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    val uri = intent?.data ?: return
    val resp = AuthorizationResponse.fromUri(uri)
    if (resp.type == AuthorizationResponse.Type.TOKEN) vm.onToken(resp.accessToken)
    else vm.status.value = "Connexion refusee : ${resp.error ?: resp.type}"
    }
        .setScopes(arrayOf("playlist-read-private", "playlist-read-collaborative"))
        .setShowDialog(true)
        .build()
    AuthorizationClient.openLoginInBrowser(this, req)
    }

    @Deprecated("Deprecated in Java")
    }
}
