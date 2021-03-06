package xyz.stalinsky.ampd

/*
    TODO: Remove all the SuppressLint annotations once SessionResult results are made public
 */

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import androidx.media2.common.MediaItem
import androidx.media2.common.MediaMetadata
import androidx.media2.common.UriMediaItem
import androidx.media2.session.*
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.database.StandaloneDatabaseProvider
import com.google.android.exoplayer2.drm.DrmSessionManagerProvider
import com.google.android.exoplayer2.ext.media2.SessionPlayerConnector
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.*
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import kotlinx.parcelize.Parcelize
import java.io.BufferedReader
import java.io.Closeable
import java.io.File
import java.io.StringReader
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MusicService : MediaLibraryService() {
    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var session: MediaLibrarySession

    private lateinit var cacheDataSource: CacheDataSource.Factory

    override fun onCreate() {
        super.onCreate()
        cacheDataSource = CacheDataSource.Factory().setUpstreamDataSourceFactory(DefaultDataSource.Factory(this@MusicService)).setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        cacheDataSource.setCache(SimpleCache(File(this@MusicService.cacheDir, "media"), LeastRecentlyUsedCacheEvictor(256 * 1024 * 1024), StandaloneDatabaseProvider(this@MusicService)))

        session = MediaLibrarySession.Builder(this,
            SessionPlayerConnector(ExoPlayer.Builder(this)
                .setAudioAttributes(AudioAttributes.Builder().setContentType(C.CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(), true)
                .setMediaSourceFactory(object : MediaSource.Factory {
                    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider?) = this

                    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy?) = this

                    override fun getSupportedTypes() = intArrayOf()

                    override fun createMediaSource(mediaItem: com.google.android.exoplayer2.MediaItem) = ProgressiveMediaSource.Factory(cacheDataSource).createMediaSource(mediaItem)
                })
                .build()),
            executor,
            Callback()).build()

    }

    override fun onDestroy() {
        super.onDestroy()

        cacheDataSource.cache?.release()
        // SessionPlayer should be closed after the session
        val player = session.player
        session.close()
        player.close()
        executor.shutdown()
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    class Callback : MediaLibrarySession.MediaLibrarySessionCallback() {
        private var connectionState = ConnectionState.DISCONNECTED
        get() {
            synchronized(field) {
                return field
            }
        }

        private var host: String? = null
        private var port: Int? = null

        private lateinit var timeoutInhibitor: MpdTimeoutInhibitor

        // Key - A connected controller
        // Value - A map of subscribed items and the number of times the controller subscribed to this item
        private val subscribers: MutableMap<MediaSession.ControllerInfo, MutableMap<String, Int>> = hashMapOf()

        // Key - A parent ID
        // Value - The number of controllers that are subscribed to this item
        private val subscriptions: MutableMap<String, Int> = hashMapOf()

        private var mediaLibrary: String? = null

        // Key - A connected controller
        // Value - A map of parent IDs this controller has gotten using getChildren() and the number of times they called getChildren() on this item
        private val cache: MutableMap<MediaSession.ControllerInfo, MutableMap<String, Int>> = hashMapOf()

        // Key - an ID of a parent MediaItem
        // Value - A pair where the first value is a reference count of controllers that point to this key in the map 'cache'
        // and the second value is a list of IDs that are the children
        private val refCounts: MutableMap<String, Pair<Int, Set<String>>> = hashMapOf()

        // Key - An ID of a MediaItem
        // Value - A pair where the first value is a reference count and the second value is the MediaItem
        // Used as a cache for items that were fetched using getChildren() so that onCreateMediaItem() can return the appropriate mediaItem when
        // the controller requests it using its mediaId
        private val mediaItems: MutableMap<String, Pair<Int, MediaItem>> = hashMapOf()

        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): SessionCommandGroup {
            cache[controller] = hashMapOf()
            subscribers[controller] = hashMapOf()
            return SessionCommandGroup.Builder()
                .addAllPredefinedCommands(SessionCommand.COMMAND_VERSION_2)
                .addCommand(COMMAND_SET_MPD_ADDRESS)
                .addCommand(COMMAND_SET_MEDIA_LIBRARY)
                .addCommand(COMMAND_CONNECT)
                .addCommand(COMMAND_PUT_CHILDREN)
                .build()
        }

        @SuppressLint("RestrictedApi")
        override fun onCustomCommand(session: MediaSession,
                                     controller: MediaSession.ControllerInfo,
                                     customCommand: SessionCommand,
                                     args: Bundle?): SessionResult {
            when (customCommand) {
                COMMAND_SET_MPD_ADDRESS -> {
                    if (args == null || !args.containsKey(COMMAND_ARG_MPD_HOST) || !args.containsKey(COMMAND_ARG_MPD_PORT))
                        return SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE, null)

                    host = args.getString(COMMAND_ARG_MPD_HOST, "")
                    port = args.getInt(COMMAND_ARG_MPD_PORT, -1)
                }

                COMMAND_SET_MEDIA_LIBRARY -> {
                    if (args == null || !args.containsKey(COMMAND_ARG_MEDIA_LIBRARY))
                        return SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE, null)

                    mediaLibrary = args.getString(COMMAND_ARG_MEDIA_LIBRARY)!!
                }

                COMMAND_CONNECT -> {
                    if (host == null || mediaLibrary == null)
                        return SessionResult(SessionResult.RESULT_ERROR_INVALID_STATE, null)

                    if (connectionState != ConnectionState.DISCONNECTED)
                        timeoutInhibitor.send(null)

                    changeConnectionState(session, ConnectionState.CONNECTING)

                    try {
                        timeoutInhibitor = MpdTimeoutInhibitor(InetSocketAddress(host, port ?: -1), {
                            changeConnectionState(session, ConnectionState.CONNECTED)
                        }, {
                            for (id in subscriptions)
                                (session as MediaLibrarySession).notifyChildrenChanged(id.key, 0, null)
                        }) {
                            changeConnectionState(session, ConnectionState.DISCONNECTED)
                        }
                    } catch (e: Exception) {
                        changeConnectionState(session, ConnectionState.DISCONNECTED)
                        return SessionResult(SessionResult.RESULT_ERROR_IO, null)
                    }
                }

                COMMAND_PUT_CHILDREN -> {
                    if (args == null || !args.containsKey(COMMAND_ARG_PARENT_ID))
                        return SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE, null)

                    val id = args.getString(COMMAND_ARG_PARENT_ID)!!

                    if (!cache[controller]!!.contains(id))
                        return SessionResult(SessionResult.RESULT_ERROR_INVALID_STATE, null)

                    if (cache[controller]!![id]!! == 1) {
                        if (refCounts[id]!!.first == 1) {
                            for (item in refCounts[id]!!.second) {
                                if (mediaItems[item]!!.first == 1)
                                    mediaItems.remove(item)
                                else
                                    mediaItems[item] = Pair(mediaItems[item]!!.first - 1, mediaItems[item]!!.second)
                            }

                            refCounts.remove(id)
                        } else {
                            refCounts[id] = Pair(refCounts[id]!!.first - 1, refCounts[id]!!.second)
                        }

                        cache[controller]!!.remove(id)
                    } else {
                        cache[controller]!![id] = cache[controller]!![id]!! - 1
                    }
                }
            }

            return SessionResult(SessionResult.RESULT_SUCCESS, null)
        }

        override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
            // Remove subscriptions
            for (id in subscribers[controller]!!) {
                // If all the subscriptions to this item come from this single controller then remove the ID
                if (subscriptions[id.key] == id.value) {
                    subscriptions.remove(id.key)
                } else {
                    subscriptions[id.key] = subscriptions[id.key]!! - id.value
                }
            }

            subscribers.remove(controller)

            // Remove fetched items
            for (id in cache[controller]!!.keys) {
                val pair = refCounts[id]!!
                if (pair.first == 1) {
                    for (item in pair.second) {
                        if (mediaItems[item]!!.first == 1)
                            mediaItems.remove(item)
                        else
                            mediaItems[item] = Pair(mediaItems[item]!!.first - 1, mediaItems[item]!!.second)
                    }

                    refCounts.remove(id)
                } else {
                    refCounts[id] = Pair(pair.first - 1, pair.second)
                }
            }

            cache.remove(controller)
        }

        private fun changeConnectionState(session: MediaSession, state: ConnectionState) {
            synchronized(connectionState) {
                val shouldBroadcast = connectionState != state
                connectionState = state
                if (shouldBroadcast)
                    session.broadcastCustomCommand(COMMAND_MPD_CONNECTION_STATUS_CHANGED, Bundle().apply { putParcelable(COMMAND_ARG_MPD_CONNECTION_STATUS, connectionState) })
            }
        }

        private val metadataBuilder = MediaMetadata.Builder().putLong(MediaMetadata.METADATA_KEY_PLAYABLE, 0)
        private val rootNodes = listOf(MediaItem.Builder()
            .setMetadata(metadataBuilder.putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "/artists")
                .putLong(MediaMetadata.METADATA_KEY_BROWSABLE, MediaMetadata.BROWSABLE_TYPE_ARTISTS)
                .putString(MediaMetadata.METADATA_KEY_TITLE, "Artists")
                .build())
            .build(),
            MediaItem.Builder()
                .setMetadata(metadataBuilder.putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "/albums")
                    .putLong(MediaMetadata.METADATA_KEY_BROWSABLE, MediaMetadata.BROWSABLE_TYPE_ALBUMS)
                    .putString(MediaMetadata.METADATA_KEY_TITLE, "Albums")
                    .build())
                .build())

        override fun onRewind(session: MediaSession, controller: MediaSession.ControllerInfo): Int {
            session.player.seekTo(0L).get()
            return SessionResult.RESULT_SUCCESS
        }

        override fun onCreateMediaItem(session: MediaSession, controller: MediaSession.ControllerInfo, mediaId: String) =
            mediaItems[mediaId]?.second

        @SuppressLint("RestrictedApi")
        override fun onSubscribe(session: MediaLibrarySession, controller: MediaSession.ControllerInfo, parentId: String, params: LibraryParams?): Int {
            // TODO: Handle root nodes differently
            if (parentId != "/") {
                // Increase refcount of the item
                if (!subscriptions.containsKey(parentId))
                    subscriptions[parentId] = 1
                else
                    subscriptions[parentId] = subscriptions[parentId]!! + 1

                // Increase the number of times or add the item to the set of subscribed items
                if (subscribers[controller]!!.contains(parentId)) {
                    subscribers[controller]!![parentId] = subscribers[controller]!![parentId]!! + 1
                } else {
                    subscribers[controller]!![parentId] = 1
                }
            }

            return LibraryResult.RESULT_SUCCESS
        }

        @SuppressLint("RestrictedApi")
        override fun onUnsubscribe(session: MediaLibrarySession, controller: MediaSession.ControllerInfo, parentId: String): Int {
            if (parentId != "/") {
                if (!subscriptions.containsKey(parentId))
                    return LibraryResult.RESULT_ERROR_BAD_VALUE

                if (!subscribers[controller]!!.containsKey(parentId)) {
                    return LibraryResult.RESULT_ERROR_INVALID_STATE
                }

                var refCount = subscriptions[parentId]!!
                if (refCount == 1)
                    subscriptions.remove(parentId)
                else
                    subscriptions[parentId] = refCount - 1

                refCount = subscribers[controller]!![parentId]!!
                if (refCount == 1)
                    subscribers[controller]!!.remove(parentId)
                else
                    subscribers[controller]!![parentId] = refCount - 1
            }

            return LibraryResult.RESULT_SUCCESS
        }

        @SuppressLint("RestrictedApi")
        override fun onGetLibraryRoot(session: MediaLibrarySession, controller: MediaSession.ControllerInfo, params: LibraryParams?): LibraryResult {
            if (mediaLibrary == null) return LibraryResult(LibraryResult.RESULT_ERROR_INVALID_STATE)

            return LibraryResult(LibraryResult.RESULT_SUCCESS,
                MediaItem.Builder()
                    .setMetadata(MediaMetadata.Builder()
                        .putLong(MediaMetadata.METADATA_KEY_PLAYABLE, 0)
                        .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "/")
                        .putLong(MediaMetadata.METADATA_KEY_BROWSABLE, MediaMetadata.BROWSABLE_TYPE_MIXED)
                        .build())
                    .build(),
                params)
        }

        @SuppressLint("RestrictedApi")
        override fun onGetChildren(session: MediaLibrarySession,
                                   controller: MediaSession.ControllerInfo,
                                   parentId: String,
                                   page: Int,
                                   pageSize: Int,
                                   params: LibraryParams?): LibraryResult {
            if (connectionState != ConnectionState.CONNECTED) {
                return LibraryResult(LibraryResult.RESULT_ERROR_INVALID_STATE)
            }

            when {
                parentId == "/" -> {
                    return LibraryResult(LibraryResult.RESULT_SUCCESS, rootNodes, params)
                }

                parentId == "/artists" -> {
                    val artists = mutableListOf<MediaItem>()
                    val result = timeoutInhibitor.send("list artist group MUSICBRAINZ_ARTISTID\n") ?: return LibraryResult(LibraryResult.RESULT_ERROR_IO)
                    val reader = BufferedReader(StringReader(result))

                    var artistId = reader.readLine()

                    val metadataBuilder = MediaMetadata.Builder()
                        .putLong(MediaMetadata.METADATA_KEY_BROWSABLE, MediaMetadata.BROWSABLE_TYPE_ARTISTS)
                        .putLong(MediaMetadata.METADATA_KEY_PLAYABLE, 0)

                    while (!artistId.equals("OK")) {
                        artistId = artistId.drop("MUSICBRAINZ_ARTISTID: ".length)
                        if (artistId.isEmpty()) {
                            artistId = reader.readLine()

                            while (artistId.startsWith("Artist: ")) {
                                artists.add(MediaItem.Builder()
                                    .setMetadata(metadataBuilder.putString(MediaMetadata.METADATA_KEY_MEDIA_ID,
                                        "/artists/noid/${artistId.drop("Artist: ".length)}")
                                        .putString(MediaMetadata.METADATA_KEY_TITLE, artistId.drop("Artist: ".length)) // The artistId is also their name
                                        .build())
                                    .build())

                                artistId = reader.readLine()
                            }
                        } else {
                            val line = reader.readLine()

                            val name = line.drop("Artist: ".length)
                            artists.add(MediaItem.Builder()
                                .setMetadata(metadataBuilder.putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "/artists/$artistId")
                                    .putString(MediaMetadata.METADATA_KEY_TITLE, name)
                                    .build())
                                .build())

                            // Some artists have multiple names but have the same MusicBrainz ID
                            // We use the first name that is returned by MPD
                            artistId = reader.readLine()

                            while (artistId.startsWith("Artist: ")) artistId = reader.readLine()
                        }
                    }

                    return LibraryResult(LibraryResult.RESULT_SUCCESS, artists, params)
                }

                parentId == "/albums" -> {
                    val albums = mutableListOf<MediaItem>()
                    val result = timeoutInhibitor.send("list album group MUSICBRAINZ_ALBUMID group artist group MUSICBRAINZ_ARTISTID\n") ?: return LibraryResult(LibraryResult.RESULT_ERROR_IO)
                    var reader = BufferedReader(StringReader(result))

                    var line = reader.readLine()

                    val sb = StringBuilder()
                    sb.appendLine("command_list_begin")

                    val itemBuilder = MediaItem.Builder()

                    val metadataBuilder = MediaMetadata.Builder()
                        .putLong(MediaMetadata.METADATA_KEY_BROWSABLE, MediaMetadata.BROWSABLE_TYPE_ALBUMS)
                        .putLong(MediaMetadata.METADATA_KEY_PLAYABLE, 0)

                    while (!line.equals("OK")) {
                        val artistId = line.drop("MUSICBRAINZ_ARTISTID: ".length)
                        if (artistId.isEmpty()) {
                            line = reader.readLine()

                            while (line.startsWith("Artist: ")) {
                                val artistName = line.drop("Artist: ".length)
                                line = reader.readLine()
                                while (line.startsWith("MUSICBRAINZ_ALBUMID: ")) {
                                    val albumId = line.drop("MUSICBRAINZ_ALBUMID: ".length)

                                    metadataBuilder.setExtras(Bundle().apply {
                                        putString(METADATA_EXTRA_ARTIST_ID, "/artists/noid/$artistName")
                                    })

                                    if (albumId.isEmpty()) {
                                        // ARTISTID is empty && ALUBMID is empty
                                        line = reader.readLine()
                                        val albumTitle = line.drop("Album: ".length)

                                        sb.appendLine("find \"((artist == \\\"${
                                            artistName.replace("\"", "\\\\\\\"")
                                        }\\\") AND (album == \\\"${albumTitle.replace("\"", "\\\\\\\"")}\\\"))\" window 0:1")

                                        // TODO: What if the artist or the album contain a '/'?
                                        metadataBuilder.putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "/albums/noid/noid/$artistName/$albumTitle")
                                            .putString(MediaMetadata.METADATA_KEY_TITLE, albumTitle)
                                            .putString(MediaMetadata.METADATA_KEY_ARTIST, artistName)
                                    } else {
                                        // ARTISTID is empty && ALUBMID isn't empty
                                        line = reader.readLine()
                                        val albumTitle = line.drop("Album: ".length)

                                        sb.appendLine("find \"((artist == \\\"${
                                            artistName.replace("\"", "\\\\\\\"")
                                        }\\\") AND (MUSICBRAINZ_ALBUMID == \\\"$albumId\\\"))\" window 0:1")

                                        metadataBuilder.putString(MediaMetadata.METADATA_KEY_MEDIA_ID,
                                            "/albums/$albumId") // TODO: Check if MusicBrainz ALBUMID is unique per artist or unique in general
                                            .putString(MediaMetadata.METADATA_KEY_TITLE, albumTitle).putString(MediaMetadata.METADATA_KEY_ARTIST, artistName)
                                    }

                                    albums.add(itemBuilder.setMetadata(metadataBuilder.build()).build())

                                    line = reader.readLine()
                                }
                            }
                        } else {
                            line = reader.readLine()
                            // The same artistID can have multiple names
                            while (line.startsWith("Artist: ")) {
                                val artistName = line.drop("Artist: ".length)

                                line = reader.readLine()
                                while (line.startsWith("MUSICBRAINZ_ALBUMID: ")) {
                                    val albumId = line.drop("MUSICBRAINZ_ALBUMID: ".length)

                                    metadataBuilder.setExtras(Bundle().apply {
                                        putString(METADATA_EXTRA_ARTIST_ID, "/artists/$artistId")
                                    })

                                    if (albumId.isEmpty()) {
                                        // ARTISTID isn't empty && ALUBMID is empty
                                        line = reader.readLine()
                                        val albumTitle = line.drop("Album: ".length)
                                        sb.appendLine("find \"((MUSICBRAINZ_ARTISTID == \\\"$artistId\\\") AND (album == \\\"${
                                            albumTitle.replace("\"", "\\\\\\\"")
                                        }\\\"))\" window 0:1")

                                        metadataBuilder.putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "/albums/noid/$artistId/$albumTitle")
                                            .putString(MediaMetadata.METADATA_KEY_TITLE, albumTitle)
                                            .putString(MediaMetadata.METADATA_KEY_ARTIST, artistName)
                                    } else {
                                        // ARTISTID isn't empty && ALUBMID isn't empty
                                        line = reader.readLine()
                                        val albumTitle = line.drop("Album: ".length)

                                        sb.appendLine("find \"((MUSICBRAINZ_ARTISTID == \\\"$artistId\\\") AND (MUSICBRAINZ_ALBUMID == \\\"$albumId\\\"))\" window 0:1")
                                        // TODO: Check if MusicBrainz ALBUMID is unique per artist or unique in general
                                        metadataBuilder.putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "/albums/$albumId")
                                            .putString(MediaMetadata.METADATA_KEY_TITLE, albumTitle)
                                            .putString(MediaMetadata.METADATA_KEY_ARTIST, artistName)
                                    }

                                    albums.add(itemBuilder.setMetadata(metadataBuilder.build()).build())

                                    line = reader.readLine()
                                }
                            }
                        }
                    }

                    sb.appendLine("command_list_end")

                    reader = BufferedReader(StringReader(timeoutInhibitor.send(sb.toString())))

                    line = reader.readLine()
                    while (line != "OK") {
                        val filename: String = line.drop("file: ".length)
                        var artistId = ""
                        var artistName = ""
                        var albumId = ""
                        var albumTitle = ""
                        line = reader.readLine()
                        while (!line.startsWith("file: ") && line != "OK") {
                            when {
                                line.startsWith("MUSICBRAINZ_ARTISTID: ") -> artistId = line.drop("MUSICBRAINZ_ARTISTID: ".length)

                                line.startsWith("Artist: ") -> artistName = line.drop("Artist: ".length)

                                line.startsWith("MUSICBRAINZ_ALBUMID: ") -> albumId = line.drop("MUSICBRAINZ_ALBUMID: ".length)

                                line.startsWith("Album: ") -> albumTitle = line.drop("Album: ".length)
                            }

                            line = reader.readLine()
                        }

                        val album = if (albumId.isEmpty()) {
                            if (artistId.isEmpty()) albums.find { it.metadata!!.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) == "/albums/noid/noid/$artistName/$albumTitle" }!!
                            else albums.find { it.metadata!!.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) == "/albums/noid/$artistId/$albumTitle" }!!
                        } else {
                            albums.find { it.metadata!!.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) == "/albums/$albumId" }!!
                        }

                        album.metadata = MediaMetadata.Builder(album.metadata!!)
                            .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, "${mediaLibrary}/${filename.substringBeforeLast('/')}/cover.jpg")
                            .build()
                    }

                    return LibraryResult(LibraryResult.RESULT_SUCCESS, albums, params)
                }

                parentId.startsWith("/artists") -> {
                    val artistId = parentId.drop("/artists".length)
                    val result = timeoutInhibitor.send("find \"(artist == \\\"${
                        artistId.drop("/noid/".length)
                            .replace("\"", "\\\\\\\"")
                    }\\\")\"\n") ?: return LibraryResult(LibraryResult.RESULT_ERROR_IO)
                    val reader = if (artistId.startsWith("/noid")) BufferedReader(StringReader(result))
                    else BufferedReader(StringReader(timeoutInhibitor.send("find \"(MUSICBRAINZ_ARTISTID == \\\"${artistId.drop(1)}\\\")\"\n")))

                    val items = mutableListOf<MediaItem>()
                    var line = reader.readLine()
                    while (line != "OK") {
                        val filename: String = line.drop("file: ".length)
                        var duration: Long = 0
                        var artistName = ""
                        var albumId = ""
                        var albumTitle = ""
                        var title = ""
                        line = reader.readLine()
                        while (!line.startsWith("file: ") && line != "OK") {
                            when {
                                line.startsWith("Artist: ") -> artistName = line.drop("Artist: ".length)

                                line.startsWith("MUSICBRAINZ_ALBUMID: ") -> albumId = "/albums/${line.drop("MUSICBRAINZ_ALBUMID: ".length)}"

                                line.startsWith("Album: ") -> albumTitle = line.drop("Album: ".length)

                                line.startsWith("duration: ") -> duration = line.drop("duration: ".length).toDouble().toLong()

                                line.startsWith("Title: ") -> title = line.drop("Title: ".length)
                            }

                            line = reader.readLine()
                        }

                        val mediaItem = UriMediaItem.Builder(Uri.parse("$mediaLibrary/$filename"))
                            .setMetadata(MediaMetadata.Builder()
                                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, filename)
                                .putLong(MediaMetadata.METADATA_KEY_BROWSABLE, MediaMetadata.BROWSABLE_TYPE_NONE)
                                .putLong(MediaMetadata.METADATA_KEY_PLAYABLE, 1)
                                .putLong(MediaMetadata.METADATA_KEY_DURATION, duration * 1000)
                                .putString(MediaMetadata.METADATA_KEY_ARTIST, artistName)
                                .putString(MediaMetadata.METADATA_KEY_ALBUM, albumTitle)
                                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                                .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, "${mediaLibrary}/${filename.substringBeforeLast('/')}/cover.jpg")
                                .setExtras(Bundle().apply {
                                    putString(METADATA_EXTRA_ARTIST_ID, parentId)
                                    putString(METADATA_EXTRA_ALBUM_ID, albumId.ifEmpty {
                                        if (parentId.startsWith("/artists/noid"))
                                            "/albums/noid/noid/$artistName/$albumTitle"
                                        else
                                            "/albums/noid/${artistId.drop(1)}/$albumTitle"
                                    })
                                })
                                .build())
                            .build()

                        items.add(mediaItem)
                    }

                    if (refCounts[parentId] != null) {
                        for (item in refCounts[parentId]!!.second)
                            mediaItems.remove(item)
                    }

                    items.forEach {
                        val id = it.metadata!!.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)!!
                        mediaItems[id] = Pair(mediaItems[id]?.first?.plus(1) ?: 1, mediaItems[id]?.second ?: it)
                    }

                    val set = hashSetOf<String>()
                    items.forEach {
                        set.add(it.metadata!!.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)!!)
                    }

                    refCounts[parentId] = Pair(refCounts[parentId]?.first?.plus(1) ?: 1, set)

                    cache[controller]?.put(parentId, cache[controller]?.get(parentId)?.plus(1) ?: 1)

                    return LibraryResult(LibraryResult.RESULT_SUCCESS, items, params)
                }

                parentId.startsWith("/albums") -> {
                    val albumId = parentId.drop("/albums".length)
                    val result = if (albumId.startsWith("/noid/noid")) {
                        val artist = albumId.drop("/noid/noid/".length).takeWhile { it != '/' }.replace("\"", "\\\\\\\"")
                        val album = albumId.drop("/noid/noid/".length).dropWhile { it != '/' }.drop(1).replace("\"", "\\\\\\\"")
                        timeoutInhibitor.send("find \"((artist == \\\"$artist\\\") AND (album == \\\"$album\\\"))\" sort disc\n")
                    } else if (albumId.startsWith("/noid")) {
                        val artist = albumId.drop("/noid/".length).takeWhile { it != '/' }
                        val album = albumId.drop("/noid/".length).dropWhile { it != '/' }.drop(1).replace("\"", "\\\\\\\"")
                        timeoutInhibitor.send("find \"((MUSICBRAINZ_ARTISTID == \\\"$artist\\\") AND (album == \\\"$album\\\"))\" sort disc\n")
                    } else {
                        timeoutInhibitor.send("find \"(MUSICBRAINZ_ALBUMID == \\\"${albumId.drop(1)}\\\")\" sort disc\n")
                    }

                    if (result == null)
                        return LibraryResult(LibraryResult.RESULT_ERROR_IO)

                    val reader = BufferedReader(StringReader(result))

                    val items = mutableListOf<MediaItem>()
                    var line = reader.readLine()
                    while (line != "OK") {
                        val filename: String = line.drop("file: ".length)
                        var duration: Long = 0
                        var artistId = ""
                        var artistName = ""
                        var albumTitle = ""
                        var title = ""
                        var disc = 0.toLong()
                        var track = 0.toLong()
                        line = reader.readLine()
                        while (!line.startsWith("file: ") && line != "OK") {
                            when {
                                line.startsWith("MUSICBRAINZ_ARTISTID: ") -> artistId = "/artists/${line.drop("MUSICBRAINZ_ARTISTID: ".length)}"

                                line.startsWith("Artist: ") -> artistName = line.drop("Artist: ".length)

                                line.startsWith("Album: ") -> albumTitle = line.drop("Album: ".length)

                                line.startsWith("duration: ") -> duration = line.drop("duration: ".length).toDouble().toLong()

                                line.startsWith("Title: ") -> title = line.drop("Title: ".length)

                                line.startsWith("Disc: ") -> disc = line.drop("Disc: ".length).toLong()

                                line.startsWith("Track: ") -> track = line.drop("Track: ".length).toLong()
                            }

                            line = reader.readLine()
                        }

                        val mediaItem = UriMediaItem.Builder(Uri.parse("$mediaLibrary/$filename"))
                            .setMetadata(MediaMetadata.Builder()
                                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, filename)
                                .putLong(MediaMetadata.METADATA_KEY_BROWSABLE, MediaMetadata.BROWSABLE_TYPE_NONE)
                                .putLong(MediaMetadata.METADATA_KEY_PLAYABLE, 1)
                                .putLong(MediaMetadata.METADATA_KEY_DURATION, duration * 1000)
                                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                                .putString(MediaMetadata.METADATA_KEY_ARTIST, artistName)
                                .putString(MediaMetadata.METADATA_KEY_ALBUM, albumTitle)
                                .putLong(MediaMetadata.METADATA_KEY_DISC_NUMBER, disc)
                                .putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, track)
                                .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, "${mediaLibrary}/${filename.substringBeforeLast('/')}/cover.jpg")
                                .setExtras(Bundle().apply {
                                    putString(METADATA_EXTRA_ARTIST_ID, artistId.ifEmpty { "/artists/noid/$artistName" })
                                    putString(METADATA_EXTRA_ALBUM_ID, parentId)
                                })
                                .build())
                            .build()

                        items.add(mediaItem)
                    }

                    if (refCounts[parentId] != null) {
                        for (item in refCounts[parentId]!!.second)
                            mediaItems.remove(item)
                    }

                    items.forEach {
                        val id = it.metadata!!.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)!!
                        mediaItems[id] = Pair(mediaItems[id]?.first?.plus(1) ?: 1, mediaItems[id]?.second ?: it)
                    }

                    val set = hashSetOf<String>()
                    items.forEach {
                        set.add(it.metadata!!.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)!!)
                    }

                    refCounts[parentId] = Pair(refCounts[parentId]?.first?.plus(1) ?: 1, set)
                    cache[controller]?.put(parentId, cache[controller]?.get(parentId)?.plus(1) ?: 1)

                    return LibraryResult(LibraryResult.RESULT_SUCCESS, items, params)
                }
            }

            return LibraryResult(LibraryResult.RESULT_ERROR_BAD_VALUE)
        }
    }

    @Parcelize
    enum class ConnectionState : Parcelable {
        DISCONNECTED, CONNECTING, CONNECTED;
    }

    companion object {
        val COMMAND_MPD_CONNECTION_STATUS_CHANGED = SessionCommand("xyz.stalinsky.ampd.MusicService.COMMAND_MPD_CONNECTION_STATUS_CHANGED", null)

        const val COMMAND_ARG_MPD_CONNECTION_STATUS = "xyz.stalinsky.ampd.MusicService.COMMAND_EXTRA_MPD_CONNECTION_STATUS_CHANGED.0"

        val COMMAND_SET_MPD_ADDRESS = SessionCommand("xyz.stalinsky.ampd.MusicService.COMMAND_SET_MPD_ADDRESS", null)
        val COMMAND_SET_MEDIA_LIBRARY = SessionCommand("xyz.stalinsky.ampd.MusicService.COMMAND_SET_MEDIA_LIBRARY", null)
        val COMMAND_CONNECT = SessionCommand("xyz.stalinsky.ampd.MusicService.COMMAND_CONNECT", null)
        val COMMAND_PUT_CHILDREN = SessionCommand("xyz.stalinsky.ampd.MusicService.COMMAND_PUT_CHILDREN", null)

        const val COMMAND_ARG_MPD_HOST = "xyz.stalinsky.ampd.MusicService.COMMAND_EXTRA_SET_MPD_ADDRESS.0"
        const val COMMAND_ARG_MPD_PORT = "xyz.stalinsky.ampd.MusicService.COMMAND_EXTRA_SET_MPD_ADDRESS.1"

        const val COMMAND_ARG_MEDIA_LIBRARY = "xyz.stalinsky.ampd.MusicService.COMMAND_EXTRA_SET_MEDIA_LIBRARY.0"

        const val COMMAND_ARG_PARENT_ID = "xyz.stalinsky.ampd.MusicService.COMMAND_EXTRA_PUT_CHILDREN.0"

        const val METADATA_EXTRA_ARTIST_ID = "xyz.stalinsky.MusicService.METADATA_EXTRA_ARTIST_ID"
        const val METADATA_EXTRA_ALBUM_ID = "xyz.stalinsky.MusicService.METADATA_EXTRA_ALBUM_ID"
    }
}

class MpdTimeoutInhibitor(remote: SocketAddress, onConnected: () -> Unit, onUpdate: () -> Unit, onShutdown: () -> Unit) : Closeable {
    private val socket = SocketChannel.open()

    private val requestPipe = Pipe.open()
    private val resultPipe = Pipe.open()
    private val thread: Thread

    init {
        thread = Thread {
            // Connect
            socket.configureBlocking(false)
            socket.connect(remote)

            val source = requestPipe.source().configureBlocking(false) as Pipe.SourceChannel
            val sink = resultPipe.sink()
            val selector = Selector.open()

            val socketKey = socket.register(selector, SelectionKey.OP_CONNECT)
            var sourceKey = source.register(selector, SelectionKey.OP_READ)

            var handleSocket: () -> Boolean
            var handleSource: () -> Boolean
            var handleBoth: () -> Boolean

            var readResponseAndThenRec: (ByteBuffer, Int, Boolean, (ByteBuffer) -> Unit) -> Boolean = { _, _, _, _ -> false }
            // Read a complete packet from the MPD server and then do 'next' with this packet
            readResponseAndThenRec = { buffer, count, shouldDrain, next ->
                val newBuffer = ByteBuffer.allocate(count * 4096)
                newBuffer.put(buffer)
                if (socket.read(newBuffer) == -1) {
                    false
                } else {
                    if (checkPacket(newBuffer)) {
                        if (shouldDrain)
                            sourceKey.interestOps(SelectionKey.OP_READ)

                        next(newBuffer)
                    } else {
                        if (shouldDrain)
                            sourceKey.interestOps(0)

                        newBuffer.flip()
                        handleSocket = { readResponseAndThenRec(newBuffer, count + 1, shouldDrain, next) }
                    }

                    true
                }
            }

            val readResponseAndThen: (Boolean, (ByteBuffer) -> Unit) -> Boolean = { shouldDrain, next ->
                readResponseAndThenRec(ByteBuffer.allocate(0), 1, shouldDrain, next)
            }

            val cancel = {
                false
            }

            var onCommand: (Boolean) -> Boolean = { _ -> false }

            // First arg - The buffer to write to the sink after sending the 'idle' command or null to not write anything to the sink
            var writeIdle: (ByteBuffer?) -> Boolean = { false }
            var writeIdleRec: (ByteBuffer, ByteBuffer?) -> Boolean = { _, _ -> false } // writeIdleRec is pre-declared so that it is usable recursively inside itself
            writeIdleRec = { idle, buffer ->
                socket.write(idle)
                handleSocket = if (idle.position() < idle.limit()) {
                    // Can't have the user interrupting our write in the middle of it
                    sourceKey.interestOps(0);
                    { writeIdleRec(idle, buffer) }
                } else {
                    if (buffer != null) {
                        buffer.flip()
                        sink.write(ByteBuffer.allocate(4).putInt(buffer.limit()).flip() as ByteBuffer)
                        while (buffer.position() < buffer.limit()) sink.write(buffer)
                    }

                    sourceKey.interestOps(SelectionKey.OP_READ)
                    handleSource = { onCommand(true) }
                    socketKey.interestOps(SelectionKey.OP_READ)
                    handleBoth = {
                        readResponseAndThen(true) {
                            if (it.limit() != 3) {
                                onUpdate()
                            }

                            onCommand(false)
                        }
                    }

                    {
                        readResponseAndThen(true) {
                            if (it.limit() != 3) {
                                onUpdate()
                            }

                            socketKey.interestOps(SelectionKey.OP_WRITE)
                            handleBoth = { onCommand(false) }
                            handleSocket = { writeIdle(null) }
                        }
                    }
                }

                true
            }

            writeIdle = {
                writeIdleRec(ByteBuffer.allocate(5).put("idle\n".toByteArray()).flip() as ByteBuffer, it) }

            // Write a packet to the MPD server
            var writeRequest: (ByteBuffer) -> Boolean = { false }
            writeRequest = { buffer ->
                socket.write(buffer)
                handleSocket = if (buffer.position() < buffer.limit()) {
                    { writeRequest(buffer) }
                } else {
                    socketKey.interestOps(SelectionKey.OP_READ);
                    {
                        readResponseAndThen(false) {
                            socketKey.interestOps(SelectionKey.OP_WRITE)
                            handleSocket = { writeIdle(it) }
                        }
                    }
                }

                true
            }

            // Send a 'noidle' command to the MPD server
            var sendNoIdleRec: (ByteBuffer, ByteBuffer) -> Boolean = { _, _ -> false }
            sendNoIdleRec = { noidle: ByteBuffer, buffer: ByteBuffer ->
                socket.write(noidle)
                handleSocket = if (noidle.position() != noidle.limit()) {
                    { sendNoIdleRec(noidle, buffer) }
                } else {
                    socketKey.interestOps(SelectionKey.OP_READ);
                    {
                        readResponseAndThen(false) {
                            socketKey.interestOps(SelectionKey.OP_WRITE)
                            handleSocket = { writeRequest(buffer) }
                        }
                    }
                }

                true
            }

            val sendNoIdle = { buffer: ByteBuffer -> sendNoIdleRec(ByteBuffer.allocate(7).put("noidle\n".toByteArray()).flip() as ByteBuffer, buffer) }

            onCommand = { shouldWriteNoIdle ->
                var buffer = ByteBuffer.allocate(4)
                // To ease the implementation, switch source to blocking mode until we read the entire command
                sourceKey.cancel()
                // Finish the cancelation
                selector.selectNow()
                source.configureBlocking(true)

                if (source.read(buffer) == -1) {
                    false
                } else {
                    while (buffer.position() < buffer.limit())
                        source.read(buffer)

                    buffer.flip()
                    buffer = ByteBuffer.allocate(buffer.getInt())
                    while (buffer.position() < buffer.limit())
                        source.read(buffer)

                    buffer.flip()
                    source.configureBlocking(false)
                    sourceKey = source.register(selector, SelectionKey.OP_READ)
                    socketKey.interestOps(SelectionKey.OP_WRITE)
                    handleSocket = if (shouldWriteNoIdle) {
                            { sendNoIdle(buffer) }
                        } else {
                            { writeRequest(buffer) }
                        }

                    // If another request comes in from the pipe while we are handling one then it must be a coming from a shutdown request
                    handleSource = cancel
                    handleBoth = cancel
                    true
                }
            }

            handleSocket = {
                try {
                    if (socket.finishConnect()) {
                        socketKey.interestOps(SelectionKey.OP_READ)
                        handleSocket = {
                            readResponseAndThen(false) {
                                onConnected()
                                socketKey.interestOps(SelectionKey.OP_WRITE)
                                handleBoth = { onCommand(false) }
                                handleSource = { onCommand(false) }
                                handleSocket = { writeIdle(null) }
                            }
                        }

                        true
                    } else {
                        false
                    }
                } catch (e: Exception) {
                    false
                }
            }

            handleSource = cancel
            handleBoth = cancel

            while (true) {
                val keyCount = selector.select()

                if (keyCount == 2) {
                    if (!handleBoth())
                        break
                } else {
                    if (selector.selectedKeys().first() == socketKey) {
                        if (!handleSocket()) {
                            break
                        }
                    } else {
                        if (!handleSource()) {
                            break
                        }
                    }
                }

                selector.selectedKeys().clear()
            }

            selector.close()
            requestPipe.source().close()
            requestPipe.sink().close()
            requestPipe.source().close()
            requestPipe.sink().close()
            socket.close()
            onShutdown()
        }

        thread.start()
    }

    // NOTE: send() IS NOT THREAD SAFE
    // send a null to shutdown the inhibitor
    fun send(command: String?): String? {
        if (command == null) {
            requestPipe.sink().close()
            thread.join()
            return null
        }

        var buffer: ByteBuffer
        val size: Int
        try {
            writeAll(requestPipe.sink(), ByteBuffer.allocate(4).putInt(command.length).flip() as ByteBuffer)
            writeAll(requestPipe.sink(), ByteBuffer.allocate(command.length).put(command.toByteArray()).flip() as ByteBuffer)
            buffer = ByteBuffer.allocate(4)
            if (!readAll(resultPipe.source(), buffer)) return null

            buffer.flip()
            size = buffer.int
            buffer = ByteBuffer.allocate(size)
            readAll(resultPipe.source(), buffer)
        } catch (e: Exception) {
            return null
        }

        val arr = ByteArray(size)
        buffer.flip()
        buffer.get(arr)
        return arr.toString(Charsets.UTF_8)
    }

    override fun close() {
    }

    private fun readAll(socket: ReadableByteChannel, buffer: ByteBuffer): Boolean {
        var pos = 0
        while (pos < buffer.limit()) {
            val temp = socket.read(buffer)
            if (temp == -1)
                return false

            pos += temp
        }

        return true
    }

    private fun writeAll(socket: WritableByteChannel, buffer: ByteBuffer) {
        while (buffer.position() < buffer.limit()) socket.write(buffer)
    }

    // Returns true if buffer is a complete packet i.e. the last line starts with an 'OK' or an 'ACK'. buffer must be at least of size 1; The buffer's position must be at the end
    private fun checkPacket(buffer: ByteBuffer): Boolean {
        var pos = buffer.position()
        if (buffer.get(pos - 1) != '\n'.code.toByte()) return false

        pos--
        while (pos != 0 && buffer.get(pos - 1) != '\n'.code.toByte())
            pos--

        if (buffer.position() - pos < 3)
            return false

        val line = ByteArray(3)
        val prevPos = buffer.position()
        buffer.position(pos)
        buffer.get(line, 0, 3)
        buffer.position(prevPos)
        val string = line.toString(Charsets.UTF_8)
        return string == "ACK" || string.startsWith("OK")
    }

    companion object {
        private const val TAG = "MPDTimeoutInhibitor"
    }
}