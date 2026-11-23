package com.radiogtavi

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.PlayerState
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class RadioViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        const val CLIENT_ID = "7db5cd521ce14642b07a675d085d65de"
        const val REDIRECT_URI = "radiogtavi://callback"
    }

    private val prefs = Prefs(app)
    private var remote: SpotifyAppRemote? = null
    private var codeVerifier: String = ""

    val allPlaylists = MutableStateFlow<List<Playlist>>(emptyList())
    val stations = MutableStateFlow<List<Playlist>>(emptyList())
    val current = MutableStateFlow(0)
    val nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val status = MutableStateFlow("Connexion...")
    val needsLogin = MutableStateFlow(true)
    val picking = MutableStateFlow(false)

    // ---------- Connexion Spotify (Authorization Code + PKCE) ----------

    fun buildLoginIntent(): Intent {
        val bytes = ByteArray(64).also { SecureRandom().nextBytes(it) }
        codeVerifier = Base64.encodeToString(
            bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray()),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        val url = "https://accounts.spotify.com/authorize" +
            "?client_id=" + URLEncoder.encode(CLIENT_ID, "UTF-8") +
            "&response_type=code" +
            "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, "UTF-8") +
            "&scope=" + URLEncoder.encode("playlist-read-private playlist-read-collaborative", "UTF-8") +
            "&code_challenge_method=S256" +
            "&code_challenge=" + challenge +
            "&show_dialog=true"
        return Intent(Intent.ACTION_VIEW, Uri.parse(url))
    }

    fun handleRedirect(uri: Uri) {
        if (uri.scheme != "radiogtavi") return
        val code = uri.getQueryParameter("code")
        if (code == null) {
            status.value = "Connexion refusee : ${uri.getQueryParameter("error") ?: "inconnue"}"
            return
        }
        viewModelScope.launch {
            try {
                val token = withContext(Dispatchers.IO) { exchangeCode(code) }
                onToken(token)
            } catch (e: Exception) {
                status.value = "Erreur token : ${e.message}"
            }
        }
    }

    private fun exchangeCode(code: String): String {
        val body = "grant_type=authorization_code" +
            "&code=" + URLEncoder.encode(code, "UTF-8") +
            "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, "UTF-8") +
            "&client_id=" + URLEncoder.encode(CLIENT_ID, "UTF-8") +
            "&code_verifier=" + URLEncoder.encode(codeVerifier, "UTF-8")
        val conn = URL("https://accounts.spotify.com/api/token").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.outputStream.use { it.write(body.toByteArray()) }
        val ok = conn.responseCode in 200..299
        val text = (if (ok) conn.inputStream else conn.errorStream).bufferedReader().readText()
        if (!ok) throw Exception(text)
        return JSONObject(text).getString("access_token")
    }

    // ---------- Logique radio (inchangee) ----------

    fun hasToken() = prefs.token != null

    fun onToken(token: String) {
        prefs.token = token
        needsLogin.value = false
        loadPlaylists(token)
        connectRemote()
    }

    fun startIfPossible() {
        val t = prefs.token
        if (t != null) {
            needsLogin.value = false
            loadPlaylists(t)
            connectRemote()
        }
    }

    private fun loadPlaylists(token: String) = viewModelScope.launch {
        try {
            val all = SpotifyApi(token).getMyPlaylists()
            allPlaylists.value = all
            refreshStations()
            if (prefs.stationIds.isEmpty()) picking.value = true
        } catch (e: Exception) {
            status.value = "Erreur : ${e.message}"
            prefs.token = null
            needsLogin.value = true
        }
    }

    private fun refreshStations() {
        val ids = prefs.stationIds
        stations.value = ids.mapNotNull { id -> allPlaylists.value.find { it.id == id } }
        val last = prefs.lastStationId
        val idx = stations.value.indexOfFirst { it.id == last }
        current.value = if (idx >= 0) idx else 0
    }

    fun saveStations(ids: List<String>) {
        prefs.stationIds = ids
        refreshStations()
        picking.value = false
        if (stations.value.isNotEmpty()) tune(current.value)
    }

    fun openPicker() { picking.value = true }

    private fun connectRemote() {
        val params = ConnectionParams.Builder(CLIENT_ID)
            .setRedirectUri(REDIRECT_URI)
            .showAuthView(true)
            .build()
        SpotifyAppRemote.connect(getApplication(), params, object : Connector.ConnectionListener {
            override fun onConnected(r: SpotifyAppRemote) {
                remote = r
                status.value = "Connecté"
                r.playerApi.subscribeToPlayerState().setEventCallback { onState(it) }
                if (stations.value.isNotEmpty()) tune(current.value)
            }
            override fun onFailure(t: Throwable) {
                status.value = "Spotify : ${t.message}"
            }
        })
    }

    private var lastUri: String? = null

    private fun onState(s: PlayerState) {
        s.track?.let { nowPlaying.value = NowPlaying(it.name, it.artist?.name ?: "") }
        // Mode radio : on force toujours aleatoire + boucle sur la playlist
        remote?.playerApi?.setShuffle(true)
        remote?.playerApi?.setRepeat(com.spotify.protocol.types.Repeat.ALL)
        // Pas de pause : on relance si Spotify s'arrete
        if (s.isPaused && stations.value.isNotEmpty() && lastUri != null) {
            remote?.playerApi?.resume()
        }
    }

    fun tune(index: Int) {
        val list = stations.value
        if (list.isEmpty()) return
        val i = index.coerceIn(0, list.size - 1)
        current.value = i
        val p = list[i]
        prefs.lastStationId = p.id
        lastUri = p.uri
        nowPlaying.value = null
        remote?.playerApi?.setShuffle(true)
        remote?.playerApi?.play(p.uri)
        remote?.playerApi?.setShuffle(true)
        remote?.playerApi?.setRepeat(com.spotify.protocol.types.Repeat.ALL)
    }

    fun next() { if (stations.value.isNotEmpty()) tune((current.value + 1) % stations.value.size) }
    fun prev() {
        val n = stations.value.size
        if (n > 0) tune((current.value - 1 + n) % n)
    }

    override fun onCleared() {
        remote?.let { SpotifyAppRemote.disconnect(it) }
    }
}
