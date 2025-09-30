package dev.brahmkshatriya.echo.extension.models

import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse(
    val success: Boolean,
    val query: String,
    val albumPage: Int,
    val results: SearchResults
)

@Serializable
data class SearchResults(
    val query: String,
    val albums: List<SearchAlbum>,
    val songs: List<SearchSong>,
    val pagination: SearchPagination
)

@Serializable
data class SearchAlbum(
    val slug: String?,
    val title: String?,
    val url: String?,
    val image: String?,
    val category: String?
)

@Serializable
data class SearchSong(
    val slug: String?,
    val title: String?,
    val artist: String?,
    val album: String?,
    val url: String?,
    val image: String?,
    val category: String?
)

@Serializable
data class SearchPagination(
    val albums: AlbumPagination
)

@Serializable
data class AlbumPagination(
    val currentPage: Int,
    val hasNext: Boolean
)