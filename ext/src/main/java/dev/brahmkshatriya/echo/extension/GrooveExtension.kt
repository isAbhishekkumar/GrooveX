package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.*
import dev.brahmkshatriya.echo.common.helpers.Page
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.*
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeedData
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.NetworkRequest.Companion.toGetRequest
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.SettingSwitch
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.groove.GrooveApi
import dev.brahmkshatriya.echo.extension.groove.GrooveConverter
import dev.brahmkshatriya.echo.extension.groove.GrooveQueries
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class GrooveExtension : ExtensionClient, 
    QuickSearchClient, HomeFeedClient, LibraryFeedClient, SearchFeedClient,
    TrackClient, AlbumClient, PlaylistClient {

    override suspend fun getSettingItems(): List<Setting> {
        return listOf(
            SettingSwitch(
                "show_cover_art_background",
                "Show Cover Art Background",
                "Enable cover art as background by default during playback",
                true
            )
        )
    }

    private lateinit var setting: Settings
    override fun setSettings(settings: Settings) {
        setting = settings
    }

    // API components - using lazy initialization to avoid creating instances until needed
    private val api by lazy { GrooveApi() }
    private val queries by lazy { GrooveQueries(api) }
    private val converter by lazy { GrooveConverter(setting) }

    // Helper function to ensure settings are initialized before accessing lazy properties
    private fun ensureInitialized() {
        if (!::setting.isInitialized) {
            throw IllegalStateException("Settings not initialized. setSettings must be called before using the extension.")
        }
    }

    override suspend fun quickSearch(query: String): List<QuickSearchItem> {
        if (query.isBlank()) return emptyList()
        
        return try {
            ensureInitialized()
            val response = queries.search(query)
            if (!response.json.success) return emptyList()
            
            val items = mutableListOf<QuickSearchItem>()
            
            // Add songs to quick search
            response.json.results.songs.take(5).forEach { song ->
                items.add(QuickSearchItem.Media(converter.toTrack(song), false))
            }
            
            // Add albums to quick search
            response.json.results.albums.take(5).forEach { album ->
                items.add(QuickSearchItem.Media(converter.toAlbum(album), false))
            }
            
            items
        } catch (e: Exception) {
            println("DEBUG: Quick search failed: ${e.message}")
            emptyList()
        }
    }

    override suspend fun deleteQuickSearch(item: QuickSearchItem) {
        // Not implemented for now
    }

    override suspend fun loadSearchFeed(query: String): Feed<Shelf> {
        if (query.isBlank()) {
            return emptyList<Shelf>().toFeed()
        }
        
        return try {
            ensureInitialized()
            val response = queries.search(query)
            if (!response.json.success) return emptyList<Shelf>().toFeed()
            
            val shelves = mutableListOf<Shelf>()
            
            // Process songs
            if (response.json.results.songs.isNotEmpty()) {
                val trackList = response.json.results.songs.map { converter.toTrack(it) }
                shelves.add(Shelf.Lists.Tracks(
                    id = "search_songs",
                    title = "Songs",
                    list = trackList
                ))
            }
            
            // Process albums
            if (response.json.results.albums.isNotEmpty()) {
                val albumList = response.json.results.albums.map { converter.toAlbum(it) }
                shelves.add(Shelf.Lists.Items(
                    id = "search_albums",
                    title = "Albums",
                    list = albumList
                ))
            }
            
            // Add tabs for better navigation
            val tabs = listOf(
                Tab("all", "All"),
                Tab("songs", "Songs"),
                Tab("albums", "Albums")
            )
            
            Feed(tabs) { tab ->
                when (tab?.id) {
                    "songs" -> PagedData.Continuous { continuation ->
                        val page = (continuation as? String)?.toIntOrNull() ?: 0
                        try {
                            // For now, we're just returning the same data since we don't have pagination
                            Page(
                                listOf(Shelf.Lists.Tracks(
                                    id = "search_songs_tab",
                                    title = "Songs",
                                    list = response.json.results.songs.map { converter.toTrack(it) }
                                )),
                                null // No more pages for now
                            )
                        } catch (e: Exception) {
                            println("DEBUG: Songs tab pagination failed: ${e.message}")
                            Page(emptyList<Shelf>(), null)
                        }
                    }.toFeedData()
                    "albums" -> PagedData.Continuous { continuation ->
                        val page = (continuation as? String)?.toIntOrNull() ?: 0
                        try {
                            // For now, we're just returning the same data since we don't have pagination
                            Page(
                                listOf(Shelf.Lists.Items(
                                    id = "search_albums_tab",
                                    title = "Albums",
                                    list = response.json.results.albums.map { converter.toAlbum(it) }
                                )),
                                null // No more pages for now
                            )
                        } catch (e: Exception) {
                            println("DEBUG: Albums tab pagination failed: ${e.message}")
                            Page(emptyList<Shelf>(), null)
                        }
                    }.toFeedData()
                    else -> PagedData.Single { shelves }.toFeedData()
                }
            }
        } catch (e: Exception) {
            println("DEBUG: Search feed failed: ${e.message}")
            emptyList<Shelf>().toFeed()
        }
    }

    override suspend fun loadHomeFeed(): Feed<Shelf> {
        println("DEBUG: loadHomeFeed() called")
        
        return try {
            ensureInitialized()
            
            val shelves = mutableListOf<Shelf>()
            
            // Try to get some featured content
            try {
                val response = queries.search("featured")
                if (response.json.success) {
                    // Add featured songs
                    if (response.json.results.songs.isNotEmpty()) {
                        val trackList = response.json.results.songs.take(10).map { converter.toTrack(it) }
                        shelves.add(Shelf.Lists.Tracks(
                            id = "featured_songs",
                            title = "Featured Songs",
                            list = trackList
                        ))
                    }
                    
                    // Add featured albums
                    if (response.json.results.albums.isNotEmpty()) {
                        val albumList = response.json.results.albums.take(10).map { converter.toAlbum(it) }
                        shelves.add(Shelf.Lists.Items(
                            id = "featured_albums",
                            title = "Featured Albums",
                            list = albumList
                        ))
                    }
                }
            } catch (e: Exception) {
                println("DEBUG: Error loading featured content: ${e.message}")
            }
            
            // Try to get trending content
            try {
                val response = queries.search("trending")
                if (response.json.success) {
                    // Add trending songs
                    if (response.json.results.songs.isNotEmpty()) {
                        val trackList = response.json.results.songs.take(10).map { converter.toTrack(it) }
                        shelves.add(Shelf.Lists.Tracks(
                            id = "trending_songs",
                            title = "Trending Songs",
                            list = trackList
                        ))
                    }
                }
            } catch (e: Exception) {
                println("DEBUG: Error loading trending content: ${e.message}")
            }
            
            println("DEBUG: Created home feed with ${shelves.size} shelves")
            
            if (shelves.isNotEmpty()) {
                shelves.toFeed()
            } else {
                // Fallback to empty shelf
                val emptyShelf = Shelf.Lists.Items(
                    id = "no_content",
                    title = "No Content Available",
                    list = emptyList<EchoMediaItem>()
                )
                listOf(emptyShelf).toFeed()
            }
        } catch (e: Exception) {
            println("DEBUG: Unexpected error in loadHomeFeed: ${e.message}")
            e.printStackTrace()
            
            // Return error shelf for debugging
            val errorShelf = Shelf.Lists.Items(
                id = "home_error",
                title = "Error: ${e.javaClass.simpleName}",
                list = emptyList<EchoMediaItem>()
            )
            listOf(errorShelf).toFeed()
        }
    }

    override suspend fun loadLibraryFeed(): Feed<Shelf> {
        println("DEBUG: loadLibraryFeed() called")
        
        return try {
            ensureInitialized()
            
            val shelves = mutableListOf<Shelf>()
            
            // Add library categories
            val favoritesCategory = Shelf.Category(
                id = "favorites",
                title = "Favorites",
                subtitle = "Your favorite music",
                feed = null,
                extras = mapOf("type" to "favorites")
            )
            shelves.add(favoritesCategory)
            
            val recentlyPlayedCategory = Shelf.Category(
                id = "recently_played",
                title = "Recently Played",
                subtitle = "Your recently played tracks",
                feed = null,
                extras = mapOf("type" to "recently_played")
            )
            shelves.add(recentlyPlayedCategory)
            
            val playlistsCategory = Shelf.Category(
                id = "playlists",
                title = "My Playlists",
                subtitle = "Your created playlists",
                feed = null,
                extras = mapOf("type" to "playlists")
            )
            shelves.add(playlistsCategory)
            
            println("DEBUG: Created library feed with ${shelves.size} shelves")
            shelves.toFeed()
        } catch (e: Exception) {
            println("DEBUG: Error in loadLibraryFeed: ${e.message}")
            e.printStackTrace()
            
            val errorShelf = Shelf.Category(
                id = "library_error",
                title = "Library",
                subtitle = "Unable to load library content",
                feed = null
            )
            listOf(errorShelf).toFeed()
        }
    }

    override suspend fun loadTrack(track: Track, isDownload: Boolean): Track {
        return try {
            ensureInitialized()
            println("DEBUG: Loading track with ID: ${track.id}")
            
            // Use the slug from track extras to fetch full song details
            // This ensures we're using the correct identifier for the API call
            val slug = track.extras["slug"] ?: throw Exception("Track slug not found")
            val response = queries.getSong(slug)
            if (!response.json.success) throw Exception("Failed to fetch song details")
            
            converter.toTrack(response.json.song)
        } catch (e: Exception) {
            println("DEBUG: Failed to load track ${track.id}: ${e.message}")
            throw Exception("Failed to load track: ${e.message}")
        }
    }

    override suspend fun loadStreamableMedia(
        streamable: Streamable, 
        isDownload: Boolean
    ): Streamable.Media {
        return when (streamable.type) {
            Streamable.MediaType.Server -> {
                println("DEBUG: Loading streamable media")
                
                // Extract stream URL from extras
                val streamUrl = streamable.extras["streamUrl"] 
                    ?: throw Exception("No stream URL found")
                
                val quality = streamable.extras["quality"]?.toIntOrNull() ?: 128
                
                val httpSource = Streamable.Source.Http(
                    request = streamUrl.toGetRequest(),
                    type = Streamable.SourceType.Progressive,
                    quality = quality,
                    title = "${quality}kbps"
                )
                
                Streamable.Media.Server(listOf(httpSource), false)
            }
            Streamable.MediaType.Background -> {
                throw Exception("Background streamables not supported for audio content")
            }
            Streamable.MediaType.Subtitle -> {
                throw Exception("Subtitles not supported")
            }
        }
    }

    override suspend fun loadAlbum(album: Album): Album {
        return try {
            ensureInitialized()
            println("DEBUG: Loading album with ID: ${album.id}")
            
            val slug = album.extras["slug"] ?: throw Exception("Album slug not found")
            val response = queries.getAlbum(slug)
            if (!response.json.success) throw Exception("Failed to fetch album details")
            
            converter.toAlbum(response.json.album)
        } catch (e: Exception) {
            println("DEBUG: Failed to load album ${album.id}: ${e.message}")
            throw Exception("Failed to load album: ${e.message}")
        }
    }

    override suspend fun loadTracks(album: Album): Feed<Track>? {
        return try {
            ensureInitialized()
            println("DEBUG: Loading tracks for album: ${album.id}")
            
            val slug = album.extras["slug"] ?: throw Exception("Album slug not found")
            val response = queries.getAlbum(slug)
            if (!response.json.success) throw Exception("Failed to fetch album details")
            
            // For album tracks, we don't have stream URLs in the album response
            // When a user clicks on a track, it will call loadTrack which gets the full details
            val tracks = converter.toTracks(response.json.album)
            tracks.toFeed() as Feed<Track>
        } catch (e: Exception) {
            println("DEBUG: Failed to load tracks for album ${album.id}: ${e.message}")
            null
        }
    }

    override suspend fun loadFeed(album: Album): Feed<Shelf>? {
        return null
    }

    override suspend fun loadFeed(track: Track): Feed<Shelf> {
        return emptyList<Shelf>().toFeed()
    }

    // PlaylistClient implementation
    override suspend fun loadPlaylist(playlist: Playlist): Playlist {
        return try {
            ensureInitialized()
            println("DEBUG: Loading playlist with ID: ${playlist.id}")
            
            // For now, we'll just return the playlist as-is since we don't have playlist support yet
            playlist
        } catch (e: Exception) {
            println("DEBUG: Failed to load playlist ${playlist.id}: ${e.message}")
            throw Exception("Failed to load playlist: ${e.message}")
        }
    }

    override suspend fun loadTracks(playlist: Playlist): Feed<Track> {
        return try {
            ensureInitialized()
            println("DEBUG: Loading tracks for playlist: ${playlist.id}")
            
            // For now, we'll return an empty feed since we don't have playlist support yet
            emptyList<Track>().toFeed() as Feed<Track>
        } catch (e: Exception) {
            println("DEBUG: Failed to load tracks for playlist ${playlist.id}: ${e.message}")
            emptyList<Track>().toFeed() as Feed<Track>
        }
    }

    override suspend fun loadFeed(playlist: Playlist): Feed<Shelf>? {
        return try {
            ensureInitialized()
            println("DEBUG: Loading feed for playlist: ${playlist.id}")
            
            // For now, we'll return null since we don't have playlist feed support yet
            null
        } catch (e: Exception) {
            println("DEBUG: Failed to load feed for playlist ${playlist.id}: ${e.message}")
            null
        }
    }
}