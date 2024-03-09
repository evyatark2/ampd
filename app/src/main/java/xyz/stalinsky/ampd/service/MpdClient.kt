package xyz.stalinsky.ampd.service

import android.util.Log
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
import kotlinx.coroutines.currentCoroutineContext

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

class MpdClient private constructor(private val addr: SocketAddress, private val socket: Socket, private var read: ByteReadChannel, private var write: ByteWriteChannel) {

    suspend fun request(req: MpdRequest): MpdResponse? {
        try {
            try {
                write.writeStringUtf8(req.toString())
            } catch (e: Exception) {
                socket.dispose()
                val socket = connect(addr) ?: return null

                read = socket.openReadChannel()
                write = socket.openWriteChannel(true)
                if (!init(read, write)) {
                    socket.dispose()
                    return null
                }
            }

            when (req) {
                is MpdRequest.MpdFindRequest,
                is MpdRequest.MpdListRequest -> {
                    val data = mutableListOf<MpdDatum.MpdKV>()
                    while (true) {
                        val line = read.readUTF8Line() ?: return null

                        if (line.startsWith("OK"))
                            break

                        val split = line.split(": ", ignoreCase = false, limit = 2)
                        if (split.size != 2)
                            return null

                        data.add(MpdDatum.MpdKV(split.first(), split.last()))
                    }

                    return MpdResponse.MpdListResponse(data)
                }
            }
        } catch (e: Exception) {
            Log.e("asdf", e.toString())
            return null
        }
    }

    public fun close() {
        socket.dispose()
    }

    companion object {
        private val selectorManager = SelectorManager(Dispatchers.IO)

        private suspend fun connect(addr: SocketAddress): Socket? {
            Log.d("asdf", "connection to $addr")
            return try {
                aSocket(selectorManager).tcp().connect(addr).tls(currentCoroutineContext())
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        private suspend fun init(read: ByteReadChannel, write: ByteWriteChannel): Boolean {
            // Initial OK MPD
            read.readUTF8Line() ?: return false

            write.writeStringUtf8("password mypass\n")
            read.readUTF8Line() ?: return false
            return true
        }

        suspend operator fun invoke(addr: SocketAddress): MpdClient? {
            val socket = connect(addr) ?: return null

            val read = socket.openReadChannel()
            val write = socket.openWriteChannel(true)
            if (!init(read, write)) {
                socket.dispose()
                return null
            }

            return MpdClient(addr, socket, read, write)
        }
    }
}