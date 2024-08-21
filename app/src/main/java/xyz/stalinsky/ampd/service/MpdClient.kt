package xyz.stalinsky.ampd.service

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.SocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.tls.tls
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.Dispatchers
import java.io.EOFException
import java.util.ArrayDeque

enum class MpdTag {
    Artist, ArtistSort, Album, AlbumSort, AlbumArtist, AlbumArtistSort, Title, TitleSort, Track, Name, Genre, Mood, Date, OriginalDate, Composer, ComposerSort, Performer, Conductor, Work, Ensemble, Movement, MovementNumber, ShowMovement, Location, Grouping, Comment, Disc, Label, MUSICBRAINZ_ARTISTID, MUSICBRAINZ_ALBUMID, MUSICBRAINZ_ALBUMARTISTID, MUSICBRAINZ_TRACKID, MUSICBRAINZ_RELEASEGROUPID, MUSICBRAINZ_RELEASETRACKID, MUSICBRAINZ_WORKID, duration, Format;
}

enum class MpdErrCode(val value: Int) {
    NOT_LIST(1), ARG(2), PASSWORD(3), PERMISSION(4), UNKNOWN(5),

    NO_EXIST(50), PLAYLIST_MAX(51), SYSTEM(52), PLAYLIST_LOAD(53), UPDATE_ALREADY(53), PLAYER_SYNC(55), EXIST(56),
}

sealed interface MpdGroupNode {
    data class Node(val data: String, val children: List<MpdGroupNode>) : MpdGroupNode
    data class Leaf(val data: String) : MpdGroupNode
}

data class MpdSong(
        val file: String, val tags: Map<MpdTag, String>)

sealed interface MpdResponse {
    class MpdListResponse(val data: List<MpdGroupNode>) : MpdResponse
    class MpdFindResponse(val data: List<MpdSong>) : MpdResponse

    class MpdErrResponse(val code: MpdErrCode)
}

sealed interface MpdFilter {

    class Equal(private val tag: MpdTag, private val value: String) : MpdFilter {
        override fun toString() = "($tag == \\\"${value.replace("\"", "\\\\\\\"")}\\\")"
    }

    class And(private val fst: MpdFilter, private val snd: MpdFilter) : MpdFilter {
        override fun toString() = "($fst AND $snd)"
    }
}

sealed interface MpdRequest {
    class MpdListRequest(val type: MpdTag, val filter: MpdFilter?, private val groups_: List<MpdTag>) : MpdRequest {
        val groups = buildList {
            addAll(groups_)
            add(type)
        }

        override fun toString() = "list $type${if (filter != null) " \"$filter\"" else ""}${
            groups_.joinToString("") {
                " group $it"
            }
        }\n"
    }

    data class MpdFindRequest(private val filter: MpdFilter, private val sort: MpdTag?) : MpdRequest {
        override fun toString() = "find \"$filter\"${if (sort != null) " sort $sort" else ""}\n"
    }
}

class MpdClient private constructor(
        private val socket: Socket,
        private var source: ByteReadChannel,
        private var sink: ByteWriteChannel) {

    suspend fun request(req: MpdRequest): MpdResponse {
        sink.writeStringUtf8(req.toString())

        return when (req) {
            is MpdRequest.MpdFindRequest -> {
                var file: String

                val line = source.readUTF8Line() ?: throw EOFException()
                if (line == "OK") {
                    return MpdResponse.MpdFindResponse(listOf())
                } else {
                    val split = line.split(": ", limit = 2)
                    if (split.size != 2 || split.first() != "file") throw EOFException()

                    file = split.last()
                }

                val list = mutableListOf<MpdSong>()
                var map = mutableMapOf<MpdTag, String>()
                while (true) {
                    val line = source.readUTF8Line() ?: throw EOFException()

                    if (line == "OK") break

                    val split = line.split(": ", limit = 2)
                    if (split.size != 2) throw EOFException()

                    if (split.first() == "file") {
                        list.add(MpdSong(file, map))
                        map = mutableMapOf()
                        file = split.last()
                        continue
                    }

                    if (split.first() == "Time" || split.first() == "Range" || split.first() == "Last-Modified" || split.first() == "added") continue

                    val tag = MpdTag.valueOf(split.first())
                    map[tag] = split.last()
                }
                MpdResponse.MpdFindResponse(list)
            }

            is MpdRequest.MpdListRequest -> {
                val nodeStack = ArrayDeque<Pair<MpdTag, MutableList<MpdGroupNode>>>(req.groups.size)
                val stack = ArrayDeque<String>(req.groups.size)
                while (true) {
                    val line = source.readUTF8Line() ?: throw EOFException()

                    if (line == "OK") break

                    val split = line.split(": ", limit = 2)
                    if (split.size != 2) throw EOFException()

                    val key = MpdTag.valueOf(split.first())
                    val value = split.last()

                    if (nodeStack.find { it.first == key } != null) {
                        while (nodeStack.last().first != key) {
                            val node = nodeStack.removeLast()
                            val data = stack.removeLast()
                            nodeStack.last().second.add(MpdGroupNode.Node(data, node.second))
                        }
                    } else {
                        nodeStack.add(Pair(key, mutableListOf()))
                    }

                    if (nodeStack.size == req.groups.size) {
                        nodeStack.last().second.add(MpdGroupNode.Leaf(value))
                    } else {
                        stack.add(value)
                    }
                }

                MpdResponse.MpdListResponse(nodeStack.first.second)
            }
        }
    }

    fun close() {
        socket.dispose()
    }

    companion object {
        private suspend fun connect(addr: SocketAddress, tls: Boolean): Socket {
            val selectorManager = SelectorManager(Dispatchers.IO)
            var socket = aSocket(selectorManager).tcp().connect(addr)

            if (tls) {
                socket = socket.tls(Dispatchers.IO)
            }

            return socket
        }

        private suspend fun init(read: ByteReadChannel, write: ByteWriteChannel) { // Initial OK MPD
            read.readUTF8Line() ?: throw IllegalStateException()

            write.writeStringUtf8("password mypass\n")
            read.readUTF8Line() ?: throw IllegalStateException()
        }

        suspend operator fun invoke(addr: SocketAddress, tls: Boolean): MpdClient {
            val socket = connect(addr, tls)

            val source = socket.openReadChannel()
            val sink = socket.openWriteChannel(true)
            try {
                init(source, sink)
            } catch (e: Throwable) {
                socket.dispose()
                throw e
            }

            return MpdClient(socket, source, sink)
        }
    }
}