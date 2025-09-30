package dev.brahmkshatriya.echo.extension.models

import kotlinx.serialization.Serializable

@Serializable
data class SongResponse(
    val success: Boolean,
    val song: SongDetails
)

@Serializable
data class SongDetails(
    val url: String,
    val title: String,
    val album: String,
    val singer: String,
    val cast: String?,
    val composer: String,
    val releaseDate: String?, // Made nullable to handle null values from API
    val lyrics: String?,
    val image: String,
    val streamUrls: List<StreamUrl>,
    val albumSongs: List<AlbumSongRef>
)

@Serializable
data class StreamUrl(
    val bitrate: String,
    val url: String,
    val type: String
)

@Serializable
data class AlbumSongRef(
    val title: String,
    val artist: String,
    val url: String
)