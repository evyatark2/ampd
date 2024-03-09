package xyz.stalinsky.ampd.datasource

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.stalinsky.ampd.service.MpdClient
import xyz.stalinsky.ampd.service.MpdConnection
import xyz.stalinsky.ampd.service.MpdFilter
import xyz.stalinsky.ampd.service.MpdRequest
import xyz.stalinsky.ampd.service.MpdResponse
import xyz.stalinsky.ampd.service.MpdTag

class MpdRemoteDataSource(host: String, port: Int, scope: CoroutineScope) {
    public var artistsInvalid = true
    public var albumsInvalid = true
    val client = MpdClient(host, port)

    /*init {
        scope.launch {
            val conn = client.connect()
        }
    }*/

    suspend fun connect() =
        client.connect()

    suspend fun fetchArtistIds(conn: MpdConnection): List<String>? {
        val res = conn.request(
            MpdRequest.MpdListRequest(MpdTag.MUSICBRAINZ_ARTISTID, null, listOf())
        )

        return if (res == null) {
            null
        } else {
            val list = mutableListOf<String>()
            val res = res as MpdResponse.MpdListResponse
            for (kv in res.data) {
                list.add(kv.value)
            }
            list
        }
    }

    suspend fun fetchArtistNameById(conn: MpdConnection, id: String): String? {
        val res = conn.request(
            MpdRequest.MpdListRequest(MpdTag.ARTIST, MpdFilter.Equal(MpdTag.MUSICBRAINZ_ARTISTID, id), listOf())
        )

        return if (res == null) {
            null
        } else {
            (res as MpdResponse.MpdListResponse).data.first().value
        }
    }

    suspend fun fetchAlbumIds(conn: MpdConnection): List<String>? {
        val res = conn.request(
            MpdRequest.MpdListRequest(MpdTag.MUSICBRAINZ_ALBUMID, null, listOf())
        )

        return if (res == null) {
            null
        } else {
            val list = mutableListOf<String>()
            val res = res as MpdResponse.MpdListResponse
            for (kv in res.data) {
                list.add(kv.value)
            }
            list
        }
    }

    suspend fun fetchAlbumTitleById(conn: MpdConnection, id: String): String? {
        val res = conn.request(
            MpdRequest.MpdListRequest(MpdTag.ALBUM, MpdFilter.Equal(MpdTag.MUSICBRAINZ_ALBUMID, id), listOf())
        )

        return if (res == null) {
            null
        } else {
            (res as MpdResponse.MpdListResponse).data.first().value
        }
    }

    suspend fun fetchAlbumArtistId(conn: MpdConnection, id: String): String? {
        val res = conn.request(
            MpdRequest.MpdListRequest(MpdTag.MUSICBRAINZ_ARTISTID, MpdFilter.Equal(MpdTag.MUSICBRAINZ_ALBUMID, id), listOf())
        )

        return if (res == null) {
            null
        } else {
            (res as MpdResponse.MpdListResponse).data.first().value
        }
    }

    suspend fun fetchalbumTrackIds(conn: MpdConnection, id: String): List<String>? {
        val res = conn.request(
            MpdRequest.MpdListRequest(MpdTag.MUSICBRAINZ_ALBUMID0w)
        )
    }
}