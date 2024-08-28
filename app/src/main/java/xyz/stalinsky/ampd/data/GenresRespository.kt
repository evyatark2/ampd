package xyz.stalinsky.ampd.data

import xyz.stalinsky.ampd.datasource.MpdRemoteDataSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GenresRespository @Inject constructor(private val mpd: MpdRemoteDataSource) {
    suspend fun getAllGenres() = mpd.fetchGenres()
}
