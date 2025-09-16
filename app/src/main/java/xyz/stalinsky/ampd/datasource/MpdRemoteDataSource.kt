package xyz.stalinsky.ampd.datasource

import io.ktor.network.sockets.SocketAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Buffer
import xyz.stalinsky.ampd.model.Album
import xyz.stalinsky.ampd.model.Artist
import xyz.stalinsky.ampd.model.Song
import xyz.stalinsky.ampd.model.Track
import xyz.stalinsky.ampd.service.MpdClient
import xyz.stalinsky.ampd.service.MpdFilter
import xyz.stalinsky.ampd.service.MpdGroupNode
import xyz.stalinsky.ampd.service.MpdRequest
import xyz.stalinsky.ampd.service.MpdResponse
import xyz.stalinsky.ampd.service.MpdTag
import java.io.EOFException
import java.nio.channels.NotYetConnectedException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MpdRemoteDataSource @Inject constructor(private val client: MpdClient) {
    private suspend fun request(req: MpdRequest): Result<MpdResponse> {
        return withContext(Dispatchers.IO) {
            try {
                Result.success(client.request(req))
            } catch(e: CancellationException) {
                ensureActive()
                Result.failure(e)
            } catch (e: Throwable) {
                Result.failure(e)
            }
        }
    }

    suspend fun fetchArtists(): Result<List<Artist>> {
        val res = request(MpdRequest.MpdListRequest(MpdTag.Artist,
                null,
                listOf(MpdTag.ArtistSort, MpdTag.MUSICBRAINZ_ARTISTID)))

        data class SortArtist(val id: String, val name: String, val sort: String)

        return res.map {
            val res = it as MpdResponse.MpdListResponse
            val out = mutableListOf<SortArtist>()
            res.data.mapTo(out) {
                val artistId = (it as MpdGroupNode.Node).data
                val artist = it.children[0] as MpdGroupNode.Node
                val sort = artist.data
                val name = (artist.children[0] as MpdGroupNode.Leaf).data
                SortArtist(artistId, name, sort)
            }
            out.sortBy {
                it.sort
            }
            out.map { Artist(it.id, it.name) }
        }
    }

    suspend fun fetchArtistSongs(id: String): Result<List<Song>> {
        val res = request(MpdRequest.MpdFindRequest(MpdFilter.Equal(MpdTag.MUSICBRAINZ_ARTISTID, id), null))

        return res.map {
            (it as MpdResponse.MpdFindResponse).data.map {
                Song(it.tags[MpdTag.MUSICBRAINZ_TRACKID] ?: "",
                        it.file,
                        it.tags[MpdTag.Title] ?: "",
                        it.tags[MpdTag.MUSICBRAINZ_ALBUMID] ?: "",
                        it.tags[MpdTag.Album] ?: "",
                        it.tags[MpdTag.MUSICBRAINZ_ARTISTID] ?: "",
                        it.tags[MpdTag.Artist] ?: "")
            }
        }
    }

    suspend fun fetchArtistNameById(id: String): Result<String> {
        val res = request(MpdRequest.MpdListRequest(MpdTag.Artist, MpdFilter.Equal(MpdTag.MUSICBRAINZ_ARTISTID, id)))

        return res.map {
            ((it as MpdResponse.MpdListResponse).data.first() as MpdGroupNode.Leaf).data
        }
    }

    suspend fun fetchAlbums(): Result<List<Album>> {
        data class SortAlbum(val id: String, val title: String, val sort: String, val artistId: String)

        val sort = mutableListOf<SortAlbum>()
        val albums = request(MpdRequest.MpdListRequest(MpdTag.Album,
                null,
                listOf(MpdTag.AlbumSort, MpdTag.MUSICBRAINZ_ALBUMID, MpdTag.MUSICBRAINZ_ALBUMARTISTID))).getOrElse {
            return Result.failure(it)
        } as MpdResponse.MpdListResponse

        albums.data.flatMapTo(sort) {
            val artistId = (it as MpdGroupNode.Node).data
            it.children.map {
                val albumId = (it as MpdGroupNode.Node).data
                val album = it.children[0] as MpdGroupNode.Node
                val sort = album.data
                val title = (album.children[0] as MpdGroupNode.Leaf).data
                SortAlbum(albumId, title, sort, artistId)
            }
        }

        sort.sortBy {
            it.sort
        }

        val artists = request(MpdRequest.MpdCommandListRequest(sort.map {
            MpdRequest.MpdListRequest(MpdTag.AlbumArtist,
                    MpdFilter.Equal(MpdTag.MUSICBRAINZ_ALBUMARTISTID, it.artistId))
        })).getOrElse {
            return Result.failure(it)
        } as MpdResponse.MpdCommandListResponse

        val out = mutableListOf<Album>()
        val tracks = request(MpdRequest.MpdCommandListRequest(sort.map {
            MpdRequest.MpdFindRequest(MpdFilter.Equal(MpdTag.MUSICBRAINZ_ALBUMID, it.id), null, Pair(0, 1))
        })).getOrElse {
            return Result.failure(it)
        } as MpdResponse.MpdCommandListResponse

        sort.zip(artists.data.map { (it as MpdResponse.MpdListResponse).data.first() })
                .zip(tracks.data.map { (it as MpdResponse.MpdFindResponse).data.first() })
                .mapTo(out) {
                    Album(it.first.first.id,
                            it.first.first.title,
                            it.first.first.artistId,
                            (it.first.second as MpdGroupNode.Leaf).data,
                            it.second.file)
                }

        return Result.success(out)
    }

    suspend fun fetchAlbumById(id: String): Result<Album> {
        val res = request(MpdRequest.MpdCommandListRequest(listOf(MpdRequest.MpdListRequest(MpdTag.Album,
                MpdFilter.Equal(MpdTag.MUSICBRAINZ_ALBUMID, id)),
                MpdRequest.MpdListRequest(MpdTag.MUSICBRAINZ_ALBUMARTISTID,
                        MpdFilter.Equal(MpdTag.MUSICBRAINZ_ALBUMID, id)),
                MpdRequest.MpdListRequest(MpdTag.AlbumArtist,
                        MpdFilter.Equal(MpdTag.MUSICBRAINZ_ALBUMID, id))))).getOrElse {
            return Result.failure(it)
        } as MpdResponse.MpdCommandListResponse

        val album = ((res.data[0] as MpdResponse.MpdListResponse).data.first() as MpdGroupNode.Leaf).data
        val artistId = ((res.data[1] as MpdResponse.MpdListResponse).data.first() as MpdGroupNode.Leaf).data
        val artist = ((res.data[2] as MpdResponse.MpdListResponse).data.first() as MpdGroupNode.Leaf).data

        val uri = request(MpdRequest.MpdFindRequest(MpdFilter.Equal(MpdTag.MUSICBRAINZ_ALBUMID, id),
                null,
                Pair(0, 1))).getOrElse {
            return Result.failure(it)
        } as MpdResponse.MpdFindResponse

        return Result.success(Album(id, album, artistId, artist, uri.data[0].file))
    }

    suspend fun fetchAlbumArt(uri: String, offset: Long, buf: Buffer): Result<Long> {
        val art = request(MpdRequest.MpdAlbumArtRequest(uri, offset))
        return art.map {
            buf.write((it as MpdResponse.MpdAlbumArtResponse).data)
            it.size
        }
    }

    suspend fun fetchAlbumTracks(id: String): Result<List<Track>> {
        val res = request(MpdRequest.MpdFindRequest(MpdFilter.Equal(MpdTag.MUSICBRAINZ_ALBUMID, id), null))

        return res.map {
            (it as MpdResponse.MpdFindResponse).data.map {
                Track(it.tags[MpdTag.MUSICBRAINZ_TRACKID] ?: "",
                        it.file,
                        it.tags[MpdTag.Title] ?: "",
                        it.tags[MpdTag.MUSICBRAINZ_ALBUMID] ?: "",
                        it.tags[MpdTag.Album] ?: "",
                        it.tags[MpdTag.MUSICBRAINZ_ARTISTID] ?: "",
                        it.tags[MpdTag.Artist] ?: "",
                        it.tags[MpdTag.Disc]?.toInt() ?: 0,
                        it.tags[MpdTag.Track]?.toInt() ?: 0)
            }
        }
    }

    suspend fun fetchGenres(): Result<List<String>> {
        val res = request(MpdRequest.MpdListRequest(MpdTag.Genre))

        return res.map {
            (it as MpdResponse.MpdListResponse).data.map {
                (it as MpdGroupNode.Leaf).data
            }
        }
    }

    suspend fun fetchGenreSongs(genre: String): Result<List<Song>> {
        val res = request(MpdRequest.MpdFindRequest(MpdFilter.Equal(MpdTag.Genre, genre), null))

        return res.map {
            (it as MpdResponse.MpdFindResponse).data.map {
                Song(it.tags[MpdTag.MUSICBRAINZ_TRACKID] ?: "",
                        it.file,
                        it.tags[MpdTag.Title] ?: "",
                        it.tags[MpdTag.MUSICBRAINZ_ALBUMID] ?: "",
                        it.tags[MpdTag.Album] ?: "",
                        it.tags[MpdTag.MUSICBRAINZ_ARTISTID] ?: "",
                        it.tags[MpdTag.Artist] ?: "")
            }
        }
    }

    private sealed interface Request {
        class Fetch(val req: MpdRequest, val res: SendChannel<MpdResponse?>) : Request
        class Subscribe(val req: MpdRequest, val res: SendChannel<Result<MpdResponse>?>) : Request
    }
}