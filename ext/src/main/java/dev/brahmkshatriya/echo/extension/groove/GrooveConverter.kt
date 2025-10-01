package dev.brahmkshatriya.echo.extension.groove

import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.models.AlbumDetails
import dev.brahmkshatriya.echo.extension.models.AlbumPageResponse
import dev.brahmkshatriya.echo.extension.models.AlbumSong
import dev.brahmkshatriya.echo.extension.models.AlbumSongPayload
import dev.brahmkshatriya.echo.extension.models.CategoryAlbumPayload
import dev.brahmkshatriya.echo.extension.models.CategorySongPayload
import dev.brahmkshatriya.echo.extension.models.DownloadLinksPayload
import dev.brahmkshatriya.echo.extension.models.RelatedSongPayload
import dev.brahmkshatriya.echo.extension.models.SearchAlbum
import dev.brahmkshatriya.echo.extension.models.SearchSong
import dev.brahmkshatriya.echo.extension.models.SongDetails
import dev.brahmkshatriya.echo.extension.models.SongDetailsResponse
import dev.brahmkshatriya.echo.extension.models.StreamUrl
import dev.brahmkshatriya.echo.extension.models.LatestReleaseAlbumPayload
import dev.brahmkshatriya.echo.extension.models.LatestReleaseSongPayload

class GrooveConverter(private val settings: Settings? = null) {

    fun toAlbumPage(response: AlbumPageResponse): GrooveAlbumPageResult {
        val albumInfo = response.album
        val coverUrl = sanitizeImageUrl(albumInfo?.coverImage)
        val albumArtists = parseArtists(albumInfo?.artists)
        val albumExtras = mutableMapOf(
            "slug" to extractSlug(response.url),
            "url" to response.url
        )
        albumInfo?.category?.let { albumExtras["category"] = it }
        albumInfo?.starcast?.let { albumExtras["starcast"] = it }
        albumInfo?.composers?.let { albumExtras["composers"] = it }
        albumInfo?.year?.let { albumExtras["year"] = it }

        val albumTitle = albumInfo?.name?.takeIf { it.isNotBlank() }
            ?: response.pageTitle?.takeIf { it.isNotBlank() }
            ?: extractSlug(response.url)

        val album = createAlbum(
            id = extractSlug(response.url),
            title = albumTitle,
            coverUrl = coverUrl,
            artists = albumArtists,
            subtitle = albumInfo?.category,
            extras = albumExtras
        )

        val tracks = response.songs.mapNotNull { payload ->
            toAlbumTrack(payload, album, coverUrl, response.url)
        }

        val nextPageUrl = determineNextPageUrl(response)

        return GrooveAlbumPageResult(
            album = album,
            tracks = tracks,
            nextPageUrl = nextPageUrl
        )
    }

    fun toTrack(song: SearchSong): Track {
        val coverUrl = sanitizeImageUrl(song.image)
        val artists = parseArtists(song.artist)
        val extras = mutableMapOf(
            "slug" to song.slug,
            "url" to song.url
        )
        song.category?.let { extras["category"] = it }
        song.album?.let { extras["album"] = it }

        val albumRef = song.album?.let {
            createAlbum(
                id = extractSlug(song.url),
                title = it,
                coverUrl = coverUrl,
                artists = artists,
                subtitle = song.category,
                extras = mutableMapOf(
                    "source" to "search",
                    "url" to song.url
                )
            )
        }

        return Track(
            id = song.slug,
            title = song.title,
            type = Track.Type.Song,
            cover = coverUrl?.toImageHolder(),
            artists = artists,
            album = albumRef,
            duration = null,
            playedDuration = null,
            plays = null,
            releaseDate = null,
            description = null,
            background = coverUrl?.toImageHolder(),
            genres = emptyList(),
            isrc = null,
            albumOrderNumber = null,
            albumDiscNumber = null,
            playlistAddedDate = null,
            isExplicit = false,
            subtitle = song.artist,
            extras = extras,
            isPlayable = Track.Playable.Yes,
            streamables = emptyList()
        )
    }

    fun toAlbum(album: SearchAlbum): Album {
        val coverUrl = sanitizeImageUrl(album.image)
        val extras = mutableMapOf(
            "slug" to album.slug,
            "url" to album.url
        )
        album.category?.let { extras["category"] = it }

        return createAlbum(
            id = album.slug,
            title = album.title,
            coverUrl = coverUrl,
            artists = emptyList(),
            subtitle = album.category,
            extras = extras
        )
    }

    fun toTrack(details: SongDetails): Track {
        val coverUrl = sanitizeImageUrl(details.image)
        val slug = extractSlug(details.url)
        val artists = parseArtists(details.singer)
        val extras = mutableMapOf(
            "slug" to slug,
            "url" to details.url
        )
        details.album?.let { extras["album"] = it }
        details.singer?.let { extras["singer"] = it }
        details.cast?.let { extras["cast"] = it }
        details.composer?.let { extras["composer"] = it }
        details.releaseDate?.let { extras["releaseDate"] = it }

        val albumRef = details.album?.let {
            createAlbum(
                id = slug,
                title = it,
                coverUrl = coverUrl,
                artists = artists,
                subtitle = it,
                extras = mutableMapOf(
                    "url" to details.url
                )
            )
        }

        val streamables = details.streamUrls.map { createStreamable(it, coverUrl) }

        return Track(
            id = slug,
            title = details.title,
            type = Track.Type.Song,
            cover = coverUrl?.toImageHolder(),
            artists = artists,
            album = albumRef,
            duration = null,
            playedDuration = null,
            plays = null,
            releaseDate = null,
            description = details.lyrics,
            background = coverUrl?.toImageHolder(),
            genres = emptyList(),
            isrc = null,
            albumOrderNumber = null,
            albumDiscNumber = null,
            playlistAddedDate = null,
            isExplicit = false,
            subtitle = details.singer,
            extras = extras,
            isPlayable = Track.Playable.Yes,
            streamables = streamables
        )
    }

    fun toSongDetails(response: SongDetailsResponse): GrooveSongDetailsResult {
        val songPayload = response.song
            ?: throw IllegalStateException("Song data missing for ${response.url}")

        val coverUrl = sanitizeImageUrl(songPayload.coverImage)
        val slug = extractSlug(response.url)
        val artists = parseArtists(songPayload.singers)
        val streamUrls = collectStreamUrls(songPayload.downloads)
        val streamables = streamUrls.map { createStreamable(it, coverUrl) }

        val extras = mutableMapOf(
            "slug" to slug,
            "url" to response.url
        )
        songPayload.category?.let { extras["category"] = it }
        songPayload.composer?.let { extras["composer"] = it }
        songPayload.leadStars?.takeIf { it.isNotBlank() }?.let { extras["leadStars"] = it }
        songPayload.singers?.let { extras["singer"] = it }
        coverUrl?.let { extras["coverUrl"] = it }
        response.pageTitle?.let { extras["pageTitle"] = it }

        val title = songPayload.name?.takeIf { it.isNotBlank() }
            ?: response.pageTitle?.takeIf { it.isNotBlank() }
            ?: slug

        val track = Track(
            id = slug,
            title = title,
            type = Track.Type.Song,
            cover = coverUrl?.toImageHolder(),
            artists = artists,
            album = null,
            duration = null,
            playedDuration = null,
            plays = null,
            releaseDate = null,
            description = null,
            background = coverUrl?.toImageHolder(),
            genres = emptyList(),
            isrc = null,
            albumOrderNumber = null,
            albumDiscNumber = null,
            playlistAddedDate = null,
            isExplicit = false,
            subtitle = songPayload.singers,
            extras = extras,
            isPlayable = Track.Playable.Yes,
            streamables = streamables
        )

        val related = response.relatedSongs.mapNotNull { toRelatedTrack(it) }

        return GrooveSongDetailsResult(track, related)
    }

    fun toTracks(details: AlbumDetails): List<Track> {
        val albumCover = sanitizeImageUrl(details.image)
        val albumArtists = details.artists.mapNotNull { createArtist(it) }
        val albumExtras = mutableMapOf(
            "slug" to extractSlug(details.url),
            "url" to details.url
        )
        details.year?.let { albumExtras["year"] = it }
        details.description?.let { albumExtras["description"] = it }

        val album = createAlbum(
            id = extractSlug(details.url),
            title = details.title,
            coverUrl = albumCover,
            artists = albumArtists,
            subtitle = details.year,
            extras = albumExtras
        )

        return details.songs.map { song ->
            val coverUrl = sanitizeImageUrl(song.image) ?: albumCover
            val artists = parseArtists(song.artist).ifEmpty { albumArtists }
            val extras = mutableMapOf(
                "slug" to song.slug,
                "url" to song.url,
                "albumUrl" to details.url,
                "albumTitle" to details.title
            )

            Track(
                id = song.slug,
                title = song.title,
                type = Track.Type.Song,
                cover = coverUrl?.toImageHolder(),
                artists = artists,
                album = album,
                duration = null,
                playedDuration = null,
                plays = null,
                releaseDate = null,
                description = null,
                background = coverUrl?.toImageHolder(),
                genres = emptyList(),
                isrc = null,
                albumOrderNumber = null,
                albumDiscNumber = null,
                playlistAddedDate = null,
                isExplicit = false,
                subtitle = song.artist,
                extras = extras,
                isPlayable = Track.Playable.Yes,
                streamables = emptyList()
            )
        }
    }

    fun toLatestReleaseTrack(
        payload: LatestReleaseSongPayload,
        timestamp: String?
    ): Track? {
        val songUrl = payload.url?.takeIf { it.isNotBlank() } ?: return null
        val slug = extractSlug(songUrl)
        val title = payload.title?.takeIf { it.isNotBlank() }?.trim() ?: slug
        val coverUrl = sanitizeImageUrl(payload.coverImage)

        val creditParts = listOfNotNull(payload.singers, payload.artists)
            .flatMap { it.split(',') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val uniqueCredits = creditParts.distinct().joinToString(", ")
        val artistSource = uniqueCredits.ifEmpty { payload.singers ?: payload.artists }
        val artists = parseArtists(artistSource)
        val subtitle = when {
            uniqueCredits.isNotEmpty() -> uniqueCredits
            !payload.category.isNullOrBlank() -> payload.category.trim()
            else -> null
        }

        val extras = mutableMapOf(
            "slug" to slug,
            "url" to songUrl,
            "source" to "latest_release_song"
        )
        payload.category?.takeIf { it.isNotBlank() }?.let { extras["category"] = it }
        payload.singers?.takeIf { it.isNotBlank() }?.let { extras["singers"] = it }
        payload.artists?.takeIf { it.isNotBlank() }?.let { extras["artists"] = it }
        timestamp?.let { extras["timestamp"] = it }

        val trackId = stableIdFromUrl("category_track", songUrl)

        return Track(
            id = trackId,
            title = title,
            type = Track.Type.Song,
            cover = coverUrl?.toImageHolder(),
            artists = artists,
            album = null,
            duration = null,
            playedDuration = null,
            plays = null,
            releaseDate = null,
            description = null,
            background = coverUrl?.toImageHolder(),
            genres = emptyList(),
            isrc = null,
            albumOrderNumber = null,
            albumDiscNumber = null,
            playlistAddedDate = null,
            isExplicit = false,
            subtitle = subtitle,
            extras = extras,
            isPlayable = Track.Playable.Yes,
            streamables = emptyList()
        )
    }

    fun toLatestReleaseAlbum(
        payload: LatestReleaseAlbumPayload,
        timestamp: String?
    ): Album? {
        val albumUrl = payload.url?.takeIf { it.isNotBlank() } ?: return null
        val slug = extractSlug(albumUrl)
        val title = payload.title?.takeIf { it.isNotBlank() }?.trim() ?: slug
        val coverUrl = sanitizeImageUrl(payload.coverImage)
        val credits = payload.artists?.takeIf { it.isNotBlank() }?.trim()
        val artists = parseArtists(credits)

        val extras = mutableMapOf(
            "slug" to slug,
            "url" to albumUrl,
            "source" to "latest_release_album"
        )
        credits?.let { extras["artists"] = it }
        timestamp?.let { extras["timestamp"] = it }

        return createAlbum(
            id = slug,
            title = title,
            coverUrl = coverUrl,
            artists = artists,
            subtitle = credits,
            extras = extras
        )
    }

    fun toCategoryTrack(
        payload: CategorySongPayload,
        timestamp: String?,
        categoryName: String?
    ): Track? {
        val songUrl = payload.url?.takeIf { it.isNotBlank() } ?: return null
        val slug = extractSlug(songUrl)
        val title = payload.title?.takeIf { it.isNotBlank() }?.trim() ?: slug
        val coverUrl = sanitizeImageUrl(payload.coverImage)
        val normalizedArtists = payload.artists
            ?.replace(" - ", ", ")
            ?.replace(" & ", ", ")
            ?.replace(" x ", ", ")
        val artists = parseArtists(normalizedArtists)

        val extras = mutableMapOf(
            "slug" to slug,
            "url" to songUrl,
            "source" to "category_song"
        )
        payload.artists?.takeIf { it.isNotBlank() }?.let { extras["artists"] = it }
        categoryName?.let { extras["category"] = it }
        timestamp?.let { extras["timestamp"] = it }

        val trackId = stableIdFromUrl("category_track", songUrl)

        return Track(
            id = trackId,
            title = title,
            type = Track.Type.Song,
            cover = coverUrl?.toImageHolder(),
            artists = artists,
            album = null,
            duration = null,
            playedDuration = null,
            plays = null,
            releaseDate = null,
            description = null,
            background = coverUrl?.toImageHolder(),
            genres = emptyList(),
            isrc = null,
            albumOrderNumber = null,
            albumDiscNumber = null,
            playlistAddedDate = null,
            isExplicit = false,
            subtitle = payload.artists,
            extras = extras,
            isPlayable = Track.Playable.Yes,
            streamables = emptyList()
        )
    }

    fun toCategoryAlbum(
        payload: CategoryAlbumPayload,
        timestamp: String?,
        categoryName: String?
    ): Album? {
        val albumUrl = payload.url?.takeIf { it.isNotBlank() } ?: return null
        val slug = extractSlug(albumUrl)
        val title = payload.title?.takeIf { it.isNotBlank() }?.trim() ?: slug
        val coverUrl = sanitizeImageUrl(payload.coverImage)
        val normalizedArtists = payload.artists
            ?.replace(" - ", ", ")
            ?.replace(" & ", ", ")
            ?.replace(" x ", ", ")
        val artists = parseArtists(normalizedArtists)

        val extras = mutableMapOf(
            "slug" to slug,
            "url" to albumUrl,
            "source" to "category_album"
        )
        payload.artists?.takeIf { it.isNotBlank() }?.let { extras["artists"] = it }
        categoryName?.let { extras["category"] = it }
        timestamp?.let { extras["timestamp"] = it }

        val albumId = stableIdFromUrl("category_album", albumUrl)

        return createAlbum(
            id = albumId,
            title = title,
            coverUrl = coverUrl,
            artists = artists,
            subtitle = payload.artists,
            extras = extras
        )
    }

    private fun sanitizeImageUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var result = raw.trim()
        while (result.contains("../")) {
            result = result.replace("../", "/")
        }
        return result
    }

    private fun parseArtists(raw: String?): List<Artist> {
        if (raw.isNullOrBlank()) return emptyList()
        val normalizedRaw = raw
            .replace(" - ", ", ")
            .replace(" & ", ", ")
            .replace(" feat. ", ", ")
            .replace(" feat ", ", ")
            .replace(" x ", ", ")
        return normalizedRaw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { createArtist(it) }
    }

    private fun createArtist(name: String): Artist? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val slug = trimmed.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        val id = slug.ifEmpty { trimmed }
        return Artist(
            id = id,
            name = trimmed,
            cover = null,
            bio = null,
            background = null,
            banners = emptyList(),
            subtitle = null,
            extras = emptyMap()
        )
    }

    private fun createAlbum(
        id: String,
        title: String,
        coverUrl: String?,
        artists: List<Artist>,
        subtitle: String?,
        extras: MutableMap<String, String>
    ): Album {
        return Album(
            id = id,
            title = title,
            type = null,
            cover = coverUrl?.toImageHolder(),
            artists = artists,
            trackCount = null,
            duration = null,
            releaseDate = null,
            description = null,
            background = coverUrl?.toImageHolder(),
            label = null,
            isExplicit = false,
            subtitle = subtitle,
            extras = extras
        )
    }

    private fun createStreamable(url: StreamUrl, coverUrl: String?): Streamable {
        val qualityValue = url.quality.filter { it.isDigit() }.toIntOrNull() ?: 0
        val extras = mutableMapOf(
            "directUrl" to url.url,
            "source" to url.url,
            "type" to url.type,
            "quality" to qualityValue.toString()
        )
        coverUrl?.let { extras["coverUrl"] = it }

        return Streamable.server(
            id = "server_${url.quality}_${url.url.hashCode()}",
            quality = qualityValue,
            title = url.quality,
            extras = extras
        )
    }

    private fun extractSlug(url: String): String {
        val segment = url.substringAfterLast('/')
        return segment.substringBefore('.').ifEmpty { url }
    }

    private fun stableIdFromUrl(prefix: String, url: String): String {
        val normalized = url.trim().lowercase()
        val hash = normalized.hashCode().toUInt().toString(36)
        return "${prefix}_$hash"
    }

    private fun toAlbumTrack(
        payload: AlbumSongPayload,
        album: Album,
        albumCoverUrl: String?,
        albumUrl: String
    ): Track? {
        val trackUrl = payload.url?.takeIf { it.isNotBlank() } ?: return null
        val slug = extractSlug(trackUrl)
        val rawTitle = payload.title?.takeIf { it.isNotBlank() } ?: slug
        val cleanedTitle = rawTitle.substringBefore(',').trim().ifEmpty { rawTitle.trim() }

        val trackArtists = parseArtists(payload.artists).ifEmpty { album.artists }
        val coverHolder = albumCoverUrl?.toImageHolder() ?: album.cover

        val extras = mutableMapOf(
            "slug" to slug,
            "url" to trackUrl,
            "albumUrl" to albumUrl,
            "albumTitle" to album.title
        )
        payload.artists?.let { extras["artists"] = it }

        return Track(
            id = slug,
            title = cleanedTitle,
            type = Track.Type.Song,
            cover = coverHolder,
            artists = trackArtists,
            album = album,
            duration = null,
            playedDuration = null,
            plays = null,
            releaseDate = null,
            description = null,
            background = coverHolder,
            genres = emptyList(),
            isrc = null,
            albumOrderNumber = null,
            albumDiscNumber = null,
            playlistAddedDate = null,
            isExplicit = false,
            subtitle = payload.artists,
            extras = extras,
            isPlayable = Track.Playable.Yes,
            streamables = emptyList()
        )
    }

    private fun collectStreamUrls(downloads: DownloadLinksPayload?): List<StreamUrl> {
        if (downloads == null) return emptyList()
        val entries = listOf(
            "320kbps" to downloads.url320,
            "128kbps" to downloads.url128
        )
        return entries.mapNotNull { (quality, link) ->
            val direct = link?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            StreamUrl(quality = quality, url = direct, type = "progressive")
        }
    }

    private fun toRelatedTrack(payload: RelatedSongPayload): Track? {
        val trackUrl = payload.url?.takeIf { it.isNotBlank() } ?: return null
        val slug = extractSlug(trackUrl)
        val rawTitle = payload.title?.takeIf { it.isNotBlank() } ?: slug
        val (title, artistName) = splitTitleAndArtist(rawTitle)
        val coverUrl = sanitizeImageUrl(payload.coverImage)
        val artists = parseArtists(artistName)

        val extras = mutableMapOf(
            "slug" to slug,
            "url" to trackUrl
        )
        payload.title?.let { extras["originalTitle"] = it }

        return Track(
            id = slug,
            title = title,
            type = Track.Type.Song,
            cover = coverUrl?.toImageHolder(),
            artists = artists,
            album = null,
            duration = null,
            playedDuration = null,
            plays = null,
            releaseDate = null,
            description = null,
            background = coverUrl?.toImageHolder(),
            genres = emptyList(),
            isrc = null,
            albumOrderNumber = null,
            albumDiscNumber = null,
            playlistAddedDate = null,
            isExplicit = false,
            subtitle = artistName,
            extras = extras,
            isPlayable = Track.Playable.Yes,
            streamables = emptyList()
        )
    }

    private fun splitTitleAndArtist(raw: String): Pair<String, String?> {
        val parts = raw.split(" - ", limit = 2)
        val title = parts.first().trim().ifEmpty { raw.trim() }
        val artist = parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
        return title to artist
    }

    private fun determineNextPageUrl(response: AlbumPageResponse): String? {
        val currentPage = extractPageNumber(response.url)
        return response.paginationPages
            .mapNotNull { entry ->
                val pageNumber = entry.page
                val pageUrl = entry.url
                if (pageNumber == null || pageUrl.isNullOrBlank()) null
                else pageNumber to pageUrl
            }
            .filter { (pageNumber, _) -> pageNumber != 2025 }
            .filter { (pageNumber, _) -> pageNumber > currentPage }
            .sortedBy { it.first }
            .firstOrNull()
            ?.second
    }

    private fun extractPageNumber(url: String): Int {
        val trimmed = url.trimEnd('/')
        val lastSegment = trimmed.substringAfterLast('/')
        return lastSegment.toIntOrNull() ?: 1
    }
}

data class GrooveAlbumPageResult(
    val album: Album,
    val tracks: List<Track>,
    val nextPageUrl: String?
)

data class GrooveSongDetailsResult(
    val track: Track,
    val relatedTracks: List<Track>
)
