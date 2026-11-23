package com.radiogtavi

data class Playlist(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val trackCount: Int
) {
    val uri: String get() = "spotify:playlist:$id"
}

data class NowPlaying(
    val title: String,
    val artist: String
)
