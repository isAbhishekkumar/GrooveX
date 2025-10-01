package dev.brahmkshatriya.echo.extension.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchResponsePayload(
    @SerialName("searchQuery") val searchQuery: String? = null,
    val timestamp: String? = null,
    val albums: List<SearchAlbumPayload> = emptyList(),
    val songs: List<SearchSongPayload> = emptyList(),
    @SerialName("totalResults") val totalResults: Int? = null
)

@Serializable
data class SearchAlbumPayload(
    val title: String? = null,
    val url: String? = null,
    val category: String? = null,
    @SerialName("coverImage") val coverImage: String? = null
)

@Serializable
data class SearchSongPayload(
    val title: String? = null,
    val album: String? = null,
    val url: String? = null,
    val category: String? = null,
    @SerialName("coverImage") val coverImage: String? = null
)

@Serializable
data class LatestReleaseResponse(
    val url: String? = null,
    val timestamp: String? = null,
    val title: String? = null,
    val latestReleaseSongs: List<LatestReleaseSongPayload> = emptyList(),
    val recentBollywoodAlbums: List<LatestReleaseAlbumPayload> = emptyList()
)

@Serializable
data class LatestReleaseSongPayload(
    val title: String? = null,
    val url: String? = null,
    val artists: String? = null,
    val singers: String? = null,
    val category: String? = null,
    @SerialName("coverImage") val coverImage: String? = null
)

@Serializable
data class LatestReleaseAlbumPayload(
    val title: String? = null,
    val url: String? = null,
    val artists: String? = null,
    @SerialName("coverImage") val coverImage: String? = null
)

@Serializable
data class CategoryFeedResponse(
    val url: String? = null,
    @SerialName("sourceUrl") val sourceUrl: String? = null,
    val timestamp: String? = null,
    val title: String? = null,
    val category: String? = null,
    val songs: List<CategorySongPayload> = emptyList(),
    val albums: List<CategoryAlbumPayload> = emptyList()
)

@Serializable
data class CategorySongPayload(
    val title: String? = null,
    val url: String? = null,
    val artists: String? = null,
    @SerialName("coverImage") val coverImage: String? = null
)

@Serializable
data class CategoryAlbumPayload(
    val title: String? = null,
    val url: String? = null,
    val artists: String? = null,
    @SerialName("coverImage") val coverImage: String? = null
)

@Serializable
data class SongDetailsResponse(
    val url: String,
    val timestamp: String? = null,
    @SerialName("pageTitle") val pageTitle: String? = null,
    val song: SongPayload? = null,
    @SerialName("relatedSongs") val relatedSongs: List<RelatedSongPayload> = emptyList()
)

@Serializable
data class SongPayload(
    val name: String? = null,
    @SerialName("singers") val singers: String? = null,
    @SerialName("leadStars") val leadStars: String? = null,
    val composer: String? = null,
    val category: String? = null,
    @SerialName("coverImage") val coverImage: String? = null,
    val downloads: DownloadLinksPayload? = null
)

@Serializable
data class DownloadLinksPayload(
    @SerialName("url128kbps") val url128: String? = null,
    @SerialName("url320kbps") val url320: String? = null
)

@Serializable
data class RelatedSongPayload(
    val title: String? = null,
    val url: String? = null,
    @SerialName("coverImage") val coverImage: String? = null
)

@Serializable
data class AlbumPageResponse(
    val url: String,
    val timestamp: String? = null,
    @SerialName("pageTitle") val pageTitle: String? = null,
    val album: AlbumPageInfo? = null,
    val songs: List<AlbumSongPayload> = emptyList(),
    @SerialName("totalSongsOnPage") val totalSongsOnPage: Int? = null,
    @SerialName("hasPagination") val hasPagination: Boolean? = null,
    @SerialName("paginationPages") val paginationPages: List<AlbumPaginationPayload> = emptyList()
)

@Serializable
data class AlbumPageInfo(
    val name: String? = null,
    @SerialName("coverImage") val coverImage: String? = null,
    val artists: String? = null,
    val starcast: String? = null,
    val composers: String? = null,
    val year: String? = null,
    val category: String? = null
)

@Serializable
data class AlbumSongPayload(
    val title: String? = null,
    val artists: String? = null,
    val url: String? = null
)

@Serializable
data class AlbumPaginationPayload(
    val page: Int? = null,
    val url: String? = null
)

data class SearchAlbum(
    val slug: String,
    val title: String,
    val url: String,
    val category: String?,
    val image: String?
)

data class SearchSong(
    val slug: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val url: String,
    val image: String?,
    val category: String?
)

data class SongDetails(
    val url: String,
    val title: String,
    val album: String?,
    val singer: String?,
    val cast: String?,
    val composer: String?,
    val releaseDate: String?,
    val lyrics: String?,
    val image: String?,
    val streamUrls: List<StreamUrl> = emptyList(),
    val albumSongs: List<AlbumSong> = emptyList()
)

data class StreamUrl(
    val quality: String,
    val url: String,
    val type: String
)

data class AlbumDetails(
    val url: String,
    val title: String,
    val artists: List<String> = emptyList(),
    val starcast: List<String> = emptyList(),
    val composers: List<String> = emptyList(),
    val year: String? = null,
    val description: String? = null,
    val image: String? = null,
    val songs: List<AlbumSong> = emptyList()
)

data class AlbumSong(
    val slug: String,
    val title: String,
    val artist: String?,
    val url: String,
    val image: String?
)
