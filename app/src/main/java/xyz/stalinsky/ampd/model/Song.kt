package xyz.stalinsky.ampd.model

data class Song(
        val id: String,
        val file: String,
        val title: String,
        val albumId: String,
        val album: String,
        val artistId: String,
        val artist: String)