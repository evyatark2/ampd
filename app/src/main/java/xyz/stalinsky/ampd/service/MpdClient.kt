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

sealed interface MpdDatum {
    data class MpdKV(val key: String, val value: String) : MpdDatum

    data class MpdBinary(val data: Array<Byte>) : MpdDatum {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as MpdBinary

            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            return data.contentHashCode()
        }

    }
}

enum class MpdTag(private val str: String) {
    ARTIST("artist"),
    ARTIST_SORT("artistsort"),
    ALBUM("album"),
    ALBUM_SORT("albumsort"),
    ALBUM_ARTIST("albumartist"),
    ALBUM_ARTIST_SORT("albumartistsort"),
    TITLE("title"),
    TITLE_SORT("titlesort"),
    TRACK("track"),
    NAME("name"),
    GENERE("genre"),
    MOOD("mood"),
    DATE("date"),
    ORIGINAL_DATE("originaldate"),
    COMPOSER("composer"),
    COMPOSER_SORT("composersort"),
    PERFORMER("performer"),
    CONDUCTOR("conductor"),
    WORK("work"),
    ENSEMBLE("ensemble"),
    MOVEMENT("movement"),
    MOVEMENT_NUMBER("movementnumber"),
    LOCATION("location"),
    GROUPING("grouping"),
    COMMENT("comment"),
    DISC("disc"),
    LABEL("label"),
    MUSICBRAINZ_ARTISTID("MUSICBRAINZ_ARTISTID"),
    MUSICBRAINZ_ALBUMID("MUSICBRAINZ_ALBUMID"),
    MUSICBRAINZ_ALBUMARTISTID("MUSICBRAINZ_ALBUMARTISTID"),
    MUSICBRAINZ_TRACK_ID("musicbrainz_trackid"),
    MUSICBRAINZ_RELEASE_GROUP_ID("musicbrainz_releasegroupid"),
    MUSICBRAINZ_RELEASE_TRACK_ID("musicbrainz_releasetrackid"),
    MUSICBRAINZ_WORK_ID("musicbrainz_workid"),;

    override fun toString() = str
}

enum class MpdErrCode(val value: Int) {
    NOT_LIST(1),
    ARG(2),
    PASSWORD(3),
    PERMISSION(4),
    UNKNOWN(5),

    NO_EXIST(50),
    PLAYLIST_MAX(51),
    SYSTEM(52),
    PLAYLIST_LOAD(53),
    UPDATE_ALREADY(53),
    PLAYER_SYNC(55),
    EXIST(56),
}

sealed interface MpdResponse {
    class MpdListResponse(val data: List<MpdDatum.MpdKV>) : MpdResponse {

    }

    class MpdOkResponse(val data: List<MpdDatum>) : MpdResponse {

    }

    class ErrResponse(val code: MpdErrCode)
}

sealed interface MpdFilter {

    class Equal(private val tag: MpdTag, private val value: String) : MpdFilter {
        override fun toString() =
            "($tag == '$value')"
    }

    class And(private val fst: MpdFilter, private val snd: MpdFilter) : MpdFilter {
        override fun toString() =
            "($fst AND $snd)"
    }
}

sealed interface MpdRequest {
    data class MpdListRequest(private val type: MpdTag, private val filter: MpdFilter?, private val groups: List<MpdTag>) : MpdRequest {
        override fun toString() =
            "list $type${if (filter != null) " \"$filter\"" else ""}${groups.joinToString {
                " group $it"
            }}\n"
    }

    data class MpdFindRequest(private val filter: MpdFilter, private val sort: MpdTag?) : MpdRequest {
        override fun toString() =
            "find \"$filter\"${if (sort != null) " sort $sort" else ""}\n"
    }
}

class MpdClient private constructor(private val addr: SocketAddress, private val tls: Boolean, private val socket: Socket, private var source: ByteReadChannel, private var sink: ByteWriteChannel) {

    suspend fun request(req: MpdRequest): MpdResponse {
        sink.writeStringUtf8(req.toString())

        when (req) {
            is MpdRequest.MpdFindRequest,
            is MpdRequest.MpdListRequest -> {
                val data = mutableListOf<MpdDatum.MpdKV>()
                while (true) {
                    val line = source.readUTF8Line() ?: throw NullPointerException()

                    if (line.startsWith("OK"))
                        break

                    val split = line.split(": ", ignoreCase = false, limit = 2)
                    if (split.size != 2)
                        throw IllegalStateException()

                    data.add(MpdDatum.MpdKV(split.first(), split.last()))
                }

                return MpdResponse.MpdListResponse(data)
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

        private suspend fun init(read: ByteReadChannel, write: ByteWriteChannel) {
            // Initial OK MPD
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

            return MpdClient(addr, tls, socket, source, sink)
        }
    }
}