package xyz.stalinsky.ampd

import android.graphics.Bitmap
import android.net.Uri

data class Artist(val name: String)

data class Album(val title: String, val artistId: String)

// Represents a song "out in the wild"
data class Song(val title: String, val artistId: String, val artist: String, val albumId: String, val album: String, val art: String?)

// Represents a song that is part of an album
data class AlbumSong(val title: String, val artistId: String, val track: Int, val disc: Int, val uri: Uri)
