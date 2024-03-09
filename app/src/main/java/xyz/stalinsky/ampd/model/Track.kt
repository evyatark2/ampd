package xyz.stalinsky.ampd.model

data class Track(val id: String, val file: String, val title: String, val albumId: String, val artistId: String, val side: Int, val track: Int)