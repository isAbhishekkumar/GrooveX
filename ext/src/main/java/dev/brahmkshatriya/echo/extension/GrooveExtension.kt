package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.AlbumClient
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.HomeFeedClient
import dev.brahmkshatriya.echo.common.clients.QuickSearchClient
import dev.brahmkshatriya.echo.common.clients.SearchFeedClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.helpers.Page
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.common.models.NetworkRequest.Companion.toGetRequest
import dev.brahmkshatriya.echo.common.models.QuickSearchItem
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Streamable.Source.Http
import dev.brahmkshatriya.echo.common.models.Streamable.SourceType
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.groove.GrooveApi
import dev.brahmkshatriya.echo.extension.groove.GrooveAlbumPageResult
import dev.brahmkshatriya.echo.extension.groove.GrooveConverter
import dev.brahmkshatriya.echo.extension.groove.GrooveSongDetailsResult
import dev.brahmkshatriya.echo.extension.groove.GrooveQueries
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

class GrooveExtension : ExtensionClient, QuickSearchClient, SearchFeedClient, HomeFeedClient, AlbumClient, TrackClient {

	private var settings: Settings? = null
	private var converter: GrooveConverter = GrooveConverter(null)
	private val api by lazy { GrooveApi() }
	private val queries by lazy { GrooveQueries(api) }
	private val songCache = mutableMapOf<String, GrooveSongDetailsResult>()
	private val haryanviCategoryUrl = "https://pagalnew.com/category/haryanvi-mp3-tracks"
	private val bollywoodCategoryUrl = "https://pagalnew.com/category/bollywood-tracks"
	private val indipopCategoryUrl = "https://pagalnew.com/category/indipop-mp3-tracks"
	private val punjabiCategoryUrl = "https://pagalnew.com/category/punjabi-mp3-tracks"
	private val tamilCategoryUrl = "https://pagalnew.com/category/tamil-mp3-tracks"
	private val englishCategoryUrl = "https://pagalnew.com/category/english-mp3-tracks"
	private val djMixCategoryUrl = "https://pagalnew.com/category/dj-mix-mp3-songs"
	private val categoryCache = mutableMapOf<String, CategoryCacheEntry>()
	private val categoryConfigs = listOf(
		CategoryConfig(haryanviCategoryUrl, "Haryanvi", "groove_haryanvi_albums"),
		CategoryConfig(bollywoodCategoryUrl, "Bollywood", "groove_bollywood_albums"),
		CategoryConfig(indipopCategoryUrl, "Indipop", "groove_indipop_albums"),
		CategoryConfig(punjabiCategoryUrl, "Punjabi", "groove_punjabi_albums"),
		CategoryConfig(tamilCategoryUrl, "Tamil", "groove_tamil_albums"),
		CategoryConfig(englishCategoryUrl, "English", "groove_english_albums"),
		CategoryConfig(djMixCategoryUrl, "DJ Mix", "groove_dj_mix_albums")
	)

	private data class CategoryConfig(
		val url: String,
		val fallbackName: String,
		val albumShelfId: String
	)

	private data class CategoryCacheEntry(
		val timestamp: Long,
		val categoryName: String,
		val albums: List<Album>
	)

	override suspend fun getSettingItems(): List<Setting> = emptyList()

	override fun setSettings(settings: Settings) {
		this.settings = settings
		converter = GrooveConverter(settings)
	}

	override suspend fun quickSearch(query: String): List<QuickSearchItem> {
		if (query.isBlank()) return emptyList()
		return try {
			val result = queries.search(query)
			val tracks = result.songs.map { QuickSearchItem.Media(converter.toTrack(it), false) }
			val albums = result.albums.map { QuickSearchItem.Media(converter.toAlbum(it), false) }
			(tracks + albums).take(10)
		} catch (_: Exception) {
			emptyList()
		}
	}

	override suspend fun deleteQuickSearch(item: QuickSearchItem) {
		// Nothing to delete for remote search suggestions.
	}

	override suspend fun loadSearchFeed(query: String): Feed<Shelf> {
		if (query.isBlank()) return emptyList<Shelf>().toFeed()
		return try {
			val result = queries.search(query)
			val shelves = mutableListOf<Shelf>()

			if (result.songs.isNotEmpty()) {
				shelves.add(
					Shelf.Lists.Tracks(
						id = "groove_search_songs",
						title = "Songs",
						list = result.songs.map { converter.toTrack(it) }
					)
				)
			}

			if (result.albums.isNotEmpty()) {
				shelves.add(
					Shelf.Lists.Items(
						id = "groove_search_albums",
						title = "Albums",
						list = result.albums.map { converter.toAlbum(it) }
					)
				)
			}

			if (shelves.isEmpty()) {
				emptyList<Shelf>().toFeed()
			} else {
				shelves.toFeed()
			}
		} catch (_: Exception) {
			emptyList<Shelf>().toFeed()
		}
	}

	override suspend fun loadHomeFeed(): Feed<Shelf> {
		return try {
			val shelves = mutableListOf<Shelf>()
			try {
				val response = queries.latestReleases()
				val latestSongs = response.latestReleaseSongs.mapNotNull {
					converter.toLatestReleaseTrack(it, response.timestamp)
				}
				if (latestSongs.isNotEmpty()) {
					shelves.add(
						Shelf.Lists.Tracks(
							id = "groove_latest_release_songs",
							title = "Latest Release Songs",
							list = latestSongs
						)
					)
				}

				val latestAlbums = response.recentBollywoodAlbums.mapNotNull {
					converter.toLatestReleaseAlbum(it, response.timestamp)
				}
				if (latestAlbums.isNotEmpty()) {
					shelves.add(
						Shelf.Lists.Items(
							id = "groove_recent_bollywood_albums",
							title = "Recent Bollywood Albums",
							list = latestAlbums
						)
					)
				}
			} catch (e: Exception) {
				println("DEBUG: Failed to load latest releases: ${e.message}")
			}

			categoryConfigs.forEachIndexed { index, config ->
				loadCategorySection(config, shelves)
				if (index < categoryConfigs.lastIndex) {
					delay(CATEGORY_LOAD_DELAY_MS)
				}
			}

			if (shelves.isNotEmpty()) {
				shelves.toFeed()
			} else {
				emptyList<Shelf>().toFeed()
			}
		} catch (_: Exception) {
			emptyList<Shelf>().toFeed()
		}
	}

	override suspend fun loadAlbum(album: Album): Album {
		val albumUrl = album.extras["url"] ?: return album
		return try {
			val response = queries.albumPage(albumUrl)
			val result = converter.toAlbumPage(response)
			val mergedExtras = album.extras + result.album.extras
			result.album.copy(extras = mergedExtras)
		} catch (_: Exception) {
			album
		}
	}

	override suspend fun loadTracks(album: Album): Feed<Track>? {
		val albumUrl = album.extras["url"] ?: return null
		return PagedData.Continuous { continuation ->
			val targetUrl = (continuation as? String)?.takeIf { it.isNotBlank() } ?: albumUrl
			val page = fetchAlbumTracksPage(targetUrl)
			if (page != null) {
				Page(page.tracks, page.nextPageUrl)
			} else {
				Page(emptyList<Track>(), null)
			}
		}.toFeed()
	}

	override suspend fun loadTrack(track: Track, isDownload: Boolean): Track {
		val sourceUrl = track.extras["url"] ?: return track
		return try {
			val details = fetchSongDetails(sourceUrl)
			val detailedTrack = details.track
			val mergedExtras = track.extras + detailedTrack.extras
			val mergedArtists = if (track.artists.isNotEmpty()) track.artists else detailedTrack.artists
			val mergedAlbum = track.album ?: detailedTrack.album

			detailedTrack.copy(
				id = track.id.ifEmpty { detailedTrack.id },
				title = track.title.ifBlank { detailedTrack.title },
				cover = detailedTrack.cover ?: track.cover,
				background = detailedTrack.background ?: track.background,
				artists = mergedArtists,
				album = mergedAlbum,
				subtitle = track.subtitle ?: detailedTrack.subtitle,
				extras = mergedExtras,
				streamables = detailedTrack.streamables,
				isPlayable = detailedTrack.isPlayable
			)
		} catch (_: Exception) {
			track
		}
	}

	override suspend fun loadStreamableMedia(streamable: Streamable, isDownload: Boolean): Streamable.Media {
		if (streamable.type != Streamable.MediaType.Server) {
			throw IllegalArgumentException("Unsupported streamable type: ${streamable.type}")
		}
		val directUrl = streamable.extras["directUrl"] ?: streamable.extras["source"]
			?: throw IllegalStateException("Missing direct stream URL")
		val quality = streamable.extras["quality"]?.toIntOrNull() ?: streamable.quality
		val title = streamable.title ?: if (quality > 0) "${quality}kbps" else "Stream"
		val httpSource = Http(
			request = directUrl.toGetRequest(),
			type = SourceType.Progressive,
			quality = quality,
			title = title
		)
		return Streamable.Media.Server(listOf(httpSource), false)
	}

	override suspend fun loadFeed(track: Track): Feed<Shelf> {
		val sourceUrl = track.extras["url"] ?: return emptyList<Shelf>().toFeed()
		val details = try {
			songCache[sourceUrl] ?: fetchSongDetails(sourceUrl)
		} catch (_: Exception) {
			return emptyList<Shelf>().toFeed()
		}
		if (details.relatedTracks.isEmpty()) return emptyList<Shelf>().toFeed()
		val shelf = Shelf.Lists.Tracks(
			id = "groove_related_tracks",
			title = "Related Songs",
			list = details.relatedTracks
		)
		return listOf(shelf).toFeed()
	}

	override suspend fun loadFeed(album: Album): Feed<Shelf>? = null

	private suspend fun loadCategorySection(config: CategoryConfig, shelves: MutableList<Shelf>) {
		val now = System.currentTimeMillis()
		val freshCache = categoryCache[config.url]?.takeIf { now - it.timestamp <= CATEGORY_CACHE_TTL_MS }
		if (freshCache != null) {
			addCategoryAlbumShelf(config, freshCache.categoryName, freshCache.albums, shelves)
			return
		}

		val staleCache = categoryCache[config.url]

		runCatching { queries.categoryFeed(config.url) }
			.onSuccess { response ->
				val categoryName = response.category?.takeIf { it.isNotBlank() }
					?: response.title?.takeIf { it.isNotBlank() }
					?: config.fallbackName
				val albums = response.albums.mapNotNull {
					converter.toCategoryAlbum(it, response.timestamp, categoryName)
				}
				if (albums.isNotEmpty()) {
					val fetchedAt = System.currentTimeMillis()
					val entry = CategoryCacheEntry(
						timestamp = fetchedAt,
						categoryName = categoryName,
						albums = albums
					)
					categoryCache[config.url] = entry
					addCategoryAlbumShelf(config, categoryName, albums, shelves)
				} else if (staleCache != null) {
					addCategoryAlbumShelf(config, staleCache.categoryName, staleCache.albums, shelves)
				}
			}
			.onFailure { error ->
				if (error is CancellationException) throw error
				if (staleCache != null) {
					println("WARN: Falling back to cached ${config.fallbackName} feed: ${error.message}")
					addCategoryAlbumShelf(config, staleCache.categoryName, staleCache.albums, shelves)
				} else {
					println("WARN: Failed to load ${config.fallbackName} feed: ${error.message}")
				}
			}
	}

	private fun addCategoryAlbumShelf(
		config: CategoryConfig,
		categoryName: String,
		albums: List<Album>,
		shelves: MutableList<Shelf>
	) {
		albums.takeIf { it.isNotEmpty() }?.let { list ->
			shelves.add(
				Shelf.Lists.Items(
					id = config.albumShelfId,
					title = "$categoryName Albums",
					list = list
				)
			)
		}
	}

	private suspend fun fetchSongDetails(url: String): GrooveSongDetailsResult {
		return songCache[url] ?: run {
			val response = retryWithDelay(SONG_DETAILS_RETRY_COUNT, SONG_DETAILS_RETRY_DELAY_MS) {
				queries.songDetails(url)
			}
			val result = converter.toSongDetails(response)
			songCache[url] = result
			result
		}
	}

	private suspend fun fetchAlbumTracksPage(startUrl: String): GrooveAlbumPageResult? {
		var currentUrl: String? = startUrl
		val visited = mutableSetOf<String>()
		var lastResult: GrooveAlbumPageResult? = null
		repeat(ALBUM_PAGE_MAX_HOPS) {
			val url = currentUrl?.takeIf { it.isNotBlank() } ?: return lastResult
			if (!visited.add(url)) return lastResult
			val page = runCatching { queries.albumPage(url) }
				.onFailure { error ->
					if (error is CancellationException) throw error
				}
				.getOrElse { return lastResult }
				.let { converter.toAlbumPage(it) }
			lastResult = page
			if (page.tracks.isNotEmpty()) return page
				currentUrl = page.nextPageUrl?.takeIf { it.isNotBlank() }
		}
		return lastResult
	}

	private suspend fun <T> retryWithDelay(
		attempts: Int,
		delayMillis: Long,
		block: suspend () -> T
	): T {
		var lastError: Throwable? = null
		repeat(attempts.coerceAtLeast(1)) { attemptIndex ->
			try {
				return block()
			} catch (error: Throwable) {
				if (error is CancellationException) throw error
				lastError = error
				if (attemptIndex < attempts - 1) {
					delay(delayMillis)
				}
			}
		}
		throw lastError ?: IllegalStateException("retryWithDelay failed without error")
	}

	companion object {
		private const val CATEGORY_LOAD_DELAY_MS = 250L
		private const val ALBUM_PAGE_MAX_HOPS = 5
		private const val SONG_DETAILS_RETRY_COUNT = 2
		private const val SONG_DETAILS_RETRY_DELAY_MS = 300L
		private const val CATEGORY_CACHE_TTL_MS = 60_000L
	}
}

