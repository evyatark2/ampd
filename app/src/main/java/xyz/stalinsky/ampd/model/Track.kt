package xyz.stalinsky.ampd.model

data class Track(
        val id: String,
        val file: String,
        val title: String,
        val albumId: String,
        val album: String,
        val artistId: String,
        val artist: String,
        val side: Int,
        val track: Int)