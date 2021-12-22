package xyz.stalinsky.ampd

import android.net.Uri
import android.os.Bundle
import androidx.media2.common.MediaItem
import androidx.media2.common.MediaMetadata
import androidx.media2.common.UriMediaItem
import androidx.media2.session.*
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.drm.DrmSessionManager
import com.google.android.exoplayer2.drm.DrmSessionManagerProvider
import com.google.android.exoplayer2.ext.media2.SessionPlayerConnector
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource
import com.google.android.exoplayer2.source.MediaSourceFactory
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.Closeable
import java.io.StringReader
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.Pipe
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class MusicService : MediaLibraryService() {
    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var session: MediaLibrarySession

    override fun onCreate() {
        super.onCreate()

        val connection = OkHttpClient.Builder().cache(Cache(cacheDir, 24 * 1024 * 1024)) // 256 MiB
            .build()

        session = MediaLibrarySession.Builder(this,
            SessionPlayerConnector(ExoPlayer.Builder(this)
                .setAudioAttributes(AudioAttributes.Builder().setContentType(C.CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(), true)
                .setMediaSourceFactory(object : MediaSourceFactory {
                    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider?): MediaSourceFactory {
                        TODO("Not yet implemented")
                    }

                    override fun setDrmSessionManager(drmSessionManager: DrmSessionManager?): MediaSourceFactory {
                        TODO("Not yet implemented")
                    }

                    override fun setDrmHttpDataSourceFactory(drmHttpDataSourceFactory: HttpDataSource.Factory?): MediaSourceFactory {
                        TODO("Not yet implemented")
                    }

                    override fun setDrmUserAgent(userAgent: String?): MediaSourceFactory {
                        TODO("Not yet implemented")
                    }

                    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy?): MediaSourceFactory {
                        TODO("Not yet implemented")
                    }

                    override fun getSupportedTypes(): IntArray {
                        TODO("Not yet implemented")
                    }

                    override fun createMediaSource(mediaItem: com.google.android.exoplayer2.MediaItem) =
                        ProgressiveMediaSource.Factory(OkHttpDataSource.Factory {
                            connection.newCall(Request.Builder().url(it.url).build())
                        }).createMediaSource(mediaItem)

                })
                .build()),
            executor,
            Callback()).build()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Player should be closed after the session
        val player = session.player
        session.close()
        player.close()
        executor.shutdown()
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    class Callback : MediaLibrarySession.MediaLibrarySessionCallback() {
        private var connected = false
        private lateinit var timeoutInhibitor: MpdTimeoutInhibitor

        private val clients: MutableMap<MediaSession.ControllerInfo, Stack<Pair<String, MutableMap<String, MediaItem>>>> = HashMap()

        private var mediaLibrary: String? = null

        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): SessionCommandGroup {
            clients[controller] = Stack()

            return SessionCommandGroup.Builder()
                .addAllPredefinedCommands(SessionCommand.COMMAND_VERSION_2)
                .addCommand(COMMAND_MPD_CONNECT)
                .addCommand(COMMAND_SET_MEDIA_LIBRARY)
                .build()
        }

        override fun onCustomCommand(session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle?): SessionResult {
            when (customCommand) {
                COMMAND_MPD_CONNECT -> {
                    if (connected) {
                        timeoutInhibitor.close()
                        connected = false
                    }

                    if (args == null || !args.containsKey(COMMAND_ARG_MPD_HOST) || !args.containsKey(COMMAND_ARG_MPD_PORT)) return SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE,
                        null)

                    val host = args.getString(COMMAND_ARG_MPD_HOST, "")
                    val port = args.getInt(COMMAND_ARG_MPD_PORT, -1)

                    val channel = try {
                        SocketChannel.open(InetSocketAddress(host, port))
                    } catch (e: Exception) {
                        return SessionResult(SessionResult.RESULT_ERROR_PERMISSION_DENIED, null)
                    }

                    timeoutInhibitor = MpdTimeoutInhibitor(channel)
                    connected = true
                }

                COMMAND_SET_MEDIA_LIBRARY -> {
                    if (args == null || !args.containsKey(COMMAND_ARG_MEDIA_LIBRARY)) return SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE, null)

                    mediaLibrary = args.getString(COMMAND_ARG_MEDIA_LIBRARY)!!
                }
            }

            return SessionResult(SessionResult.RESULT_SUCCESS, null)
        }

        override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
            clients.remove(controller)
            if (clients.isEmpty()) {
                timeoutInhibitor.close()
                connected = false
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

        override fun onCreateMediaItem(session: MediaSession, controller: MediaSession.ControllerInfo, mediaId: String): MediaItem? {
            val mediaItem = clients[controller]?.peek()?.second?.get(mediaId) ?: return null

            return UriMediaItem.Builder(Uri.parse("$mediaLibrary/${mediaItem.metadata!!.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)}"))
                .setMetadata(mediaItem.metadata)
                .build()
        }

        override fun onSubscribe(session: MediaLibrarySession, controller: MediaSession.ControllerInfo, parentId: String, params: LibraryParams?): Int {
            if (parentId.startsWith("/artists/") || parentId.startsWith("/albums/")) clients[controller]!!.push(Pair(parentId, mutableMapOf()))

            session.notifyChildrenChanged(controller, parentId, rootNodes.size, params)

            return LibraryResult.RESULT_SUCCESS
        }

        override fun onUnsubscribe(session: MediaLibrarySession, controller: MediaSession.ControllerInfo, parentId: String): Int {
            if (!clients[controller]!!.empty() && clients[controller]!!.peek().first != parentId) return LibraryResult.RESULT_ERROR_BAD_VALUE

            clients[controller]!!.pop()

            return LibraryResult.RESULT_SUCCESS
        }

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

        override fun onGetChildren(session: MediaLibrarySession,
            controller: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?): LibraryResult {
            if (!clients[controller]!!.empty() && clients[controller]!!.peek()?.first != parentId) return LibraryResult(LibraryResult.RESULT_ERROR_INVALID_STATE)

            when {
                parentId == "/" -> {
                    return LibraryResult(LibraryResult.RESULT_SUCCESS, rootNodes, params)
                }

                parentId == "/artists" -> {
                    val artists = mutableListOf<MediaItem>()
                    val buffer = timeoutInhibitor.send("list artist group MUSICBRAINZ_ARTISTID\n")
                    val reader = BufferedReader(StringReader(StandardCharsets.UTF_8.decode(buffer).toString()))
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
                                .build()) // Some artists have multiple names but have the same MusicBrainz ID
                            // We use the first name that is returned by MPD
                            artistId = reader.readLine()

                            while (artistId.startsWith("Artist: ")) artistId = reader.readLine()
                        }
                    }

                    return LibraryResult(LibraryResult.RESULT_SUCCESS, artists, params)
                }

                parentId == "/albums" -> {
                    val albums = mutableListOf<MediaItem>()
                    val buffer = timeoutInhibitor.send("list album group MUSICBRAINZ_ALBUMID group artist group MUSICBRAINZ_ARTISTID\n")
                    var reader = BufferedReader(StringReader(StandardCharsets.UTF_8.decode(buffer).toString()))
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

                                    if (albumId.isEmpty()) { // ARTISTID is empty && ALUBMID is empty
                                        line = reader.readLine()
                                        val albumTitle = line.drop("Album: ".length)

                                        sb.appendLine("find \"((artist == \\\"${
                                            artistName.replace("\"", "\\\\\\\"")
                                        }\\\") AND (album == \\\"${albumTitle.replace("\"", "\\\\\\\"")}\\\"))\" window 0:1")

                                        metadataBuilder.putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "/albums/noid/noid/$artistName/$albumTitle")
                                            .putString(MediaMetadata.METADATA_KEY_TITLE, albumTitle) // The artistId is also their name
                                            .putString(MediaMetadata.METADATA_KEY_ARTIST, artistName)
                                    } else { // ARTISTID is empty && ALUBMID isn't empty
                                        line = reader.readLine()
                                        val albumTitle = line.drop("Album: ".length)

                                        sb.appendLine("find \"((artist == \\\"${
                                            artistName.replace("\"", "\\\\\\\"")
                                        }\\\") AND (MUSICBRAINZ_ALBUMID == \\\"$albumId\\\"))\" window 0:1")

                                        metadataBuilder.putString(MediaMetadata.METADATA_KEY_MEDIA_ID,
                                            "/albums/$albumId") // TODO: Check if MusicBrainz ALBUMID is unique per artist or unique in general
                                            .putString(MediaMetadata.METADATA_KEY_TITLE, albumTitle) // The artistId is also their name
                                            .putString(MediaMetadata.METADATA_KEY_ARTIST, artistName)
                                    }

                                    albums.add(itemBuilder.setMetadata(metadataBuilder.build()).build())

                                    line = reader.readLine()
                                }
                            }
                        } else {
                            line = reader.readLine() // The same artistID can have multiple names
                            while (line.startsWith("Artist: ")) {
                                val artistName = line.drop("Artist: ".length)

                                line = reader.readLine()
                                while (line.startsWith("MUSICBRAINZ_ALBUMID: ")) {
                                    val albumId = line.drop("MUSICBRAINZ_ALBUMID: ".length)

                                    metadataBuilder.setExtras(Bundle().apply {
                                        putString(METADATA_EXTRA_ARTIST_ID, "/artists/$artistId")
                                    })

                                    if (albumId.isEmpty()) { // ARTISTID isn't empty && ALUBMID is empty
                                        line = reader.readLine()
                                        val albumTitle = line.drop("Album: ".length)
                                        sb.appendLine("find \"((MUSICBRAINZ_ARTISTID == \\\"$artistId\\\") AND (album == \\\"${
                                            albumTitle.replace("\"", "\\\\\\\"")
                                        }\\\"))\" window 0:1")

                                        metadataBuilder.putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "/albums/noid/$artistId/$albumTitle")
                                            .putString(MediaMetadata.METADATA_KEY_TITLE, albumTitle) // The artistId is also their name
                                            .putString(MediaMetadata.METADATA_KEY_ARTIST, artistName)
                                    } else { // ARTISTID isn't empty && ALUBMID isn't empty
                                        line = reader.readLine()
                                        val albumTitle = line.drop("Album: ".length)

                                        sb.appendLine("find \"((MUSICBRAINZ_ARTISTID == \\\"$artistId\\\") AND (MUSICBRAINZ_ALBUMID == \\\"$albumId\\\"))\" window 0:1")

                                        metadataBuilder.putString(MediaMetadata.METADATA_KEY_MEDIA_ID,
                                            "/albums/$albumId") // TODO: Check if MusicBrainz ALBUMID is unique per artist or unique in general
                                            .putString(MediaMetadata.METADATA_KEY_TITLE, albumTitle) // The artistId is also their name
                                            .putString(MediaMetadata.METADATA_KEY_ARTIST, artistName)
                                    }

                                    albums.add(itemBuilder.setMetadata(metadataBuilder.build()).build())

                                    line = reader.readLine()
                                }
                            }
                        }
                    }

                    sb.appendLine("command_list_end")

                    reader = BufferedReader(StringReader(StandardCharsets.UTF_8.decode(timeoutInhibitor.send(sb.toString())).toString()))
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
                    val buffer = if (artistId.startsWith("/noid")) timeoutInhibitor.send("find \"(artist == \\\"${
                        artistId.drop("/noid/".length).replace("\"", "\\\\\\\"")
                    }\\\")\"\n")
                    else timeoutInhibitor.send("find \"(MUSICBRAINZ_ARTISTID == \\\"${artistId.drop(1)}\\\")\"\n")

                    val items = mutableListOf<MediaItem>()
                    val reader = BufferedReader(StringReader(StandardCharsets.UTF_8.decode(buffer).toString()))
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

                                line.startsWith("MUSICBRAINZ_ALBUMID: ") -> albumId = line.drop("MUSICBRAINZ_ALBUMID: ".length)

                                line.startsWith("Album: ") -> albumTitle = line.drop("Album: ".length)

                                line.startsWith("duration: ") -> duration = line.drop("duration: ".length).toDouble().toLong()

                                line.startsWith("Title: ") -> title = line.drop("Title: ".length)
                            }

                            line = reader.readLine()
                        }

                        val mediaItem = MediaItem.Builder()
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
                                    putString(METADATA_EXTRA_ARTIST_ID, artistId)
                                    putString(METADATA_EXTRA_ALBUM_ID, albumId)
                                })
                                .build())
                            .build()

                        items.add(mediaItem)
                    }

                    clients[controller]!!.peek().second.putAll(items.associateBy({ it.metadata!!.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)!! }, { it }))

                    return LibraryResult(LibraryResult.RESULT_SUCCESS, items, params)
                }

                parentId.startsWith("/albums") -> {
                }
            }

            return LibraryResult(LibraryResult.RESULT_ERROR_BAD_VALUE)
        }
    }

    companion object {
        // When a client receives this command, the only valid command they can send to the server is controller.disconnect()
        val COMMAND_MPD_CONNECT = SessionCommand("xyz.stalinsky.ampd.MusicService.COMMAND_MPD_CONNECT", null)
        val COMMAND_SET_MEDIA_LIBRARY = SessionCommand("xyz.stalinsky.ampd.MusicService.COMMAND_SET_MEDIA_LIBRARY", null)

        const val COMMAND_ARG_MPD_HOST = "xyz.stalinsky.ampd.MusicService.COMMAND_EXTRA_MPD_CONNECT.0"
        const val COMMAND_ARG_MPD_PORT = "xyz.stalinsky.ampd.MusicService.COMMAND_EXTRA_MPD_CONNECT.1"

        const val COMMAND_ARG_MEDIA_LIBRARY = "xyz.stalinsky.ampd.MusicService.COMMAND_EXTRA_SET_MEDIA_LIBRARY.0"

        const val METADATA_EXTRA_ARTIST_ID = "xyz.stalinsky.MusicService.METADATA_EXTRA_ARTIST_ID"
        const val METADATA_EXTRA_ALBUM_ID = "xyz.stalinsky.MusicService.METADATA_EXTRA_ARTIST_ID"
    }
}

// Socket MUST be created using SocketChannel.open()
class MpdTimeoutInhibitor(private val channel: SocketChannel) : Closeable {
    private val requestPipe = Pipe.open()
    private val resultQueue = LinkedBlockingQueue<ByteBuffer>()
    private val thread = Thread {
        val selector = Selector.open()
        var idle = true
        var shouldDrain = false
        requestPipe.source().configureBlocking(false)
        val pipeKey = requestPipe.source().register(selector, SelectionKey.OP_READ)

        var resultCount = 0
        var result: ByteBuffer = ByteBuffer.allocate(0)

        var commandLength = 0
        var request: ByteBuffer? = null

        // Fetch the "OK MPD 0.xx.x" message
        val buffer = ByteBuffer.allocate(64)
        channel.read(buffer)
        BufferedReader(StringReader(StandardCharsets.UTF_8.decode(buffer).toString())).readLine()

        channel.configureBlocking(false)
        val socketKey = channel.register(selector, SelectionKey.OP_WRITE)

        while (true) {
            val keySize = selector.select()
            val keys = selector.selectedKeys()
            if (keySize == 2) { // 1. A command was requested from the pipe and the MPD server responded to our "idle" at the same time - in this case, none of the keys are writeable
                // 2. A command was requested from the pipe and we are in the middle of issuing an "idle" command - in this case, one key is writeable

                if (keys.none { it.isWritable }) { // Deregister the pipe while we drain the socket
                    requestPipe.source().keyFor(selector).interestOps(0)
                    assert(idle)
                } else { // Don't issue the idle command and instead issue the requested command
                    val sizeBuffer = ByteBuffer.allocate(4)
                    requestPipe.source().read(sizeBuffer)
                    sizeBuffer.rewind()
                    commandLength = sizeBuffer.int
                    if (commandLength == 0) break

                    request = ByteBuffer.allocate(commandLength)
                    requestPipe.source().read(request)
                    request.rewind()
                    channel.write(request)

                    idle = false
                    shouldDrain = false
                    socketKey.interestOps(SelectionKey.OP_READ)
                }
            } else {
                val key = keys.first()
                if (key == socketKey) {
                    if (key.isWritable) {
                        if (idle) {
                            channel.write(ByteBuffer.allocate(5).put("idle\n".toByteArray()).rewind() as ByteBuffer)
                        } else {
                            if (shouldDrain) channel.write(ByteBuffer.allocate(7).put("noidle\n".toByteArray()).rewind() as ByteBuffer)
                            else channel.write(request)
                        }

                        key.interestOps(SelectionKey.OP_READ)
                    } else {
                        resultCount++ // 1448 is for debugging purposes
                        val newResult = ByteBuffer.allocate(1448 * resultCount)
                        newResult.put(result)
                        channel.read(newResult)
                        result = newResult

                        if (result[result.position() - 1] != '\n'.code.toByte()) { // If the last character is not a '\n' then surely we didn't read the whole command
                            result.rewind()
                        } else { // Otherwise, check if the last line is an "OK" or an "ACK"; if it's not, wait for more data to arrive
                            var start = result.position() - 1
                            while (start != 0 && result[start - 1] != '\n'.code.toByte()) start--

                            val end = result.position() - 1
                            val line = ByteArray(end - start) { 0 }
                            result.position(start)
                            result.get(line, 0, end - start)
                            result.position(end + 1)

                            val lineString = line.toString(Charsets.UTF_8)
                            if (lineString == "OK" || lineString.startsWith("ACK")) {
                                if (idle) { // If we disabled pipe read, then enable it here
                                    pipeKey.interestOps(SelectionKey.OP_READ)
                                } else {
                                    if (!shouldDrain) {
                                        result.rewind()
                                        resultQueue.put(result)
                                        idle = true
                                    } else {
                                        shouldDrain = false
                                    }

                                    result = ByteBuffer.allocate(0)
                                    resultCount = 0

                                    key.interestOps(SelectionKey.OP_WRITE)
                                }
                            } else {
                                result.rewind()
                            }
                        }
                    }
                } else {
                    val sizeBuffer = ByteBuffer.allocate(4)
                    requestPipe.source().read(sizeBuffer)
                    sizeBuffer.rewind()
                    commandLength = sizeBuffer.int
                    if (commandLength == 0) break

                    request = ByteBuffer.allocate(commandLength)
                    requestPipe.source().read(request)
                    request.rewind()

                    idle = false
                    shouldDrain = true
                    socketKey.interestOps(SelectionKey.OP_WRITE)
                }
            }

            keys.clear()
        }
    }

    init {
        thread.start()
    }

    // NOTE: send() IS NOT THREAD SAFE
    fun send(command: String): ByteBuffer {
        requestPipe.sink()
            .write(ByteBuffer.allocate(4 + command.toByteArray().size).putInt(command.toByteArray().size).put(command.toByteArray()).rewind() as ByteBuffer)
        return resultQueue.take()
    }

    override fun close() {
        requestPipe.sink().write(ByteBuffer.allocate(4).putInt(0).rewind() as ByteBuffer)
        thread.join()

        requestPipe.sink().close()
        requestPipe.source().close()
    }
}