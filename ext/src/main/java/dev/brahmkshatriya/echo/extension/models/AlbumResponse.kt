package dev.brahmkshatriya.echo.extension.models

import kotlinx.serialization.Serializable

@Serializable
data class AlbumResponse(
    val success: Boolean,
    val album: AlbumDetails
)

@Serializable
data class AlbumDetails(
    val url: String?,
    val title: String?,
    val artists: List<String>?,
    val starcast: List<String>?,
    val composers: List<String>?,
    val year: String?,
    val description: String?,
    val image: String?,
    val songs: List<AlbumSong>?
)

@Serializable
data class AlbumSong(
    val slug: String?,
    val title: String?,
    val artist: String?,
    val url: String?,
    val image: String?
)