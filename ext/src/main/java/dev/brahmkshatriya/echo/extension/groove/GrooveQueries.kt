package dev.brahmkshatriya.echo.extension.groove

import dev.brahmkshatriya.echo.extension.models.LatestReleaseResponse
import dev.brahmkshatriya.echo.extension.models.CategoryFeedResponse
import dev.brahmkshatriya.echo.extension.models.SearchAlbum
import dev.brahmkshatriya.echo.extension.models.SearchAlbumPayload
import dev.brahmkshatriya.echo.extension.models.AlbumPageResponse
import dev.brahmkshatriya.echo.extension.models.SearchResponsePayload
import dev.brahmkshatriya.echo.extension.models.SearchSong
import dev.brahmkshatriya.echo.extension.models.SearchSongPayload
import dev.brahmkshatriya.echo.extension.models.SongDetailsResponse

class GrooveQueries(private val api: GrooveApi) {
    suspend fun search(query: String): GrooveSearchResult {
        val payload = api.search(query)
        return payload.toResult()
    }

    suspend fun albumPage(url: String): AlbumPageResponse = api.album(url)

    suspend fun songDetails(url: String): SongDetailsResponse = api.song(url)

    suspend fun latestReleases(): LatestReleaseResponse = api.latestReleases()

    suspend fun categoryFeed(categoryUrl: String): CategoryFeedResponse =
        api.categoryFeed(categoryUrl)

    private fun SearchResponsePayload.toResult(): GrooveSearchResult {
        val albums = albums.mapNotNull { it.toDomainAlbum() }
        val songs = songs.mapNotNull { it.toDomainSong() }
        return GrooveSearchResult(albums = albums, songs = songs)
    }

    private fun SearchAlbumPayload.toDomainAlbum(): SearchAlbum? {
        val safeUrl = url?.takeIf { it.isNotBlank() } ?: return null
        val titleValue = title?.takeIf { it.isNotBlank() } ?: return null
        return SearchAlbum(
            slug = extractSlug(safeUrl),
            title = titleValue.trim(),
            url = safeUrl,
            category = category?.takeIf { it.isNotBlank() }?.trim(),
            image = coverImage?.takeIf { it.isNotBlank() }?.trim()
        )
    }

    private fun SearchSongPayload.toDomainSong(): SearchSong? {
        val safeUrl = url?.takeIf { it.isNotBlank() } ?: return null
        val titleValue = title?.takeIf { it.isNotBlank() } ?: return null
        val artistPart = titleValue.split(" - ", limit = 2).getOrNull(1)?.trim()
        val mainTitle = titleValue.split(" - ", limit = 2).first().trim()
        return SearchSong(
            slug = extractSlug(safeUrl),
            title = mainTitle,
            artist = artistPart?.takeIf { it.isNotBlank() },
            album = album?.takeIf { it.isNotBlank() }?.trim(),
            url = safeUrl,
            image = coverImage?.takeIf { it.isNotBlank() }?.trim(),
            category = category?.takeIf { it.isNotBlank() }?.trim()
        )
    }

    private fun extractSlug(url: String): String {
        val trimmed = url.substringAfterLast('/')
        return trimmed.substringBefore('.').ifEmpty { url }
    }
}

data class GrooveSearchResult(
    val albums: List<SearchAlbum>,
    val songs: List<SearchSong>
)
