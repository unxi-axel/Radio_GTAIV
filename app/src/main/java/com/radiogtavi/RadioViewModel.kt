package com.radiogtavi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RadioViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        const val CLIENT_ID = "7db5cd521ce14642b07a675d085d65de"
        const val REDIRECT_URI = "radiogtavi://callback"
    }

    private val prefs = Prefs(app)
    private var remote: SpotifyAppRemote? = null

    val allPlaylists = MutableStateFlow<List<Playlist>>(emptyList())
    val stations = MutableStateFlow<List<Playlist>>(emptyList())
    val current = MutableStateFlow(0)
    val nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val status = MutableStateFlow("Connexion...")
    val needsLogin = MutableStateFlow(true)
    val picking = MutableStateFlow(false)

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
