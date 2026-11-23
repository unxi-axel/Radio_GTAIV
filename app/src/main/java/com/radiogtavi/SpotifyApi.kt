package com.radiogtavi

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class SpotifyApi(private val token: String) {
    private val client = OkHttpClient()

    suspend fun getMyPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Playlist>()
        var url: String? = "https://api.spotify.com/v1/me/playlists?limit=50"
        while (url != null) {
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Erreur API Spotify : ${resp.code}")
                val json = JsonParser.parseString(resp.body!!.string()).asJsonObject
                json.getAsJsonArray("items").forEach { el ->
                    if (el.isJsonNull) return@forEach
                    val o = el.asJsonObject
                    val images = o.getAsJsonArray("images")
                    val img = if (images != null && images.size() > 0)
                        images[0].asJsonObject.get("url").asString else null
                    val tracks = o.getAsJsonObject("tracks")?.get("total")?.asInt ?: 0
                    result.add(
                        Playlist(
                            id = o.get("id").asString,
                            name = o.get("name").asString,
                            imageUrl = img,
                            trackCount = tracks
                        )
                    )
                }
                url = json.get("next")?.takeIf { !it.isJsonNull }?.asString
            }
        }
        result
    }
}
