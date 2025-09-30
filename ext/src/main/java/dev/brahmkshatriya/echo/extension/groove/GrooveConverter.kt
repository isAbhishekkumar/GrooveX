package dev.brahmkshatriya.echo.extension.groove

import dev.brahmkshatriya.echo.common.models.*
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.NetworkRequest.Companion.toGetRequest
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.models.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class GrooveConverter(private val settings: Settings) {
    
    // Utility function to fix relative image URLs
    private fun fixImageUrl(url: String?): String? {
        if (url == null) return null
        // Replace relative paths with absolute paths
        return url.replace("../", "https://pagalnew.com/")
    }
    
    private fun String?.toFixedImageHolder(): ImageHolder? {
        return fixImageUrl(this)?.toImageHolder()
    }
    
    fun toTrack(searchSong: SearchSong): Track {
        // Extract slug from URL if available
        val slug = searchSong.slug ?: searchSong.url?.let { url ->
            url.substringAfterLast("/").substringBeforeLast(".")
        } ?: searchSong.title ?: ""
        
        // Build extras map with only non-null values
        val extras = mutableMapOf<String, String>().apply {
            put("slug", slug)
            searchSong.url?.let { put("url", it) }
        }
        
        return Track(
            id = searchSong.url ?: searchSong.title ?: "",
            title = searchSong.title ?: "",
            type = Track.Type.Song,
            cover = searchSong.image.toFixedImageHolder(),
            artists = listOf(Artist(
                id = "", 
                name = searchSong.artist ?: "",
                cover = null,
                bio = null,
                background = null,
                banners = emptyList(),
                subtitle = null,
                extras = emptyMap()
            )),
            album = null,
            duration = null,
            playedDuration = null,
            plays = null,
            releaseDate = null,
            description = null,
            background = searchSong.image.toFixedImageHolder(),
            genres = emptyList(),
            isrc = null,
            albumOrderNumber = null,
            albumDiscNumber = null,
            playlistAddedDate = null,
            isExplicit = false,
            subtitle = searchSong.album,
            extras = extras,
            isPlayable = Track.Playable.Yes,
            streamables = emptyList()
        )
    }
    
    fun toTrack(songDetails: SongDetails): Track {
        // Create streamables for both 128kbps and 320kbps if available
        val streamables = mutableListOf<Streamable>()
        
        // Find 320kbps stream URL
        val stream320 = songDetails.streamUrls?.find { it.bitrate == "320kbps" }
        if (stream320 != null) {
            val extras = mutableMapOf<String, String>().apply {
                stream320.url?.let { put("streamUrl", it) }
                put("quality", "320")
                songDetails.image?.let { put("coverUrl", fixImageUrl(it) ?: "") }
            }
            
            streamables.add(
                Streamable.server(
                    id = "server_320_${songDetails.url?.hashCode() ?: songDetails.title?.hashCode() ?: 0}",
                    quality = 320,
                    title = "320kbps",
                    extras = extras
                )
            )
        }
        
        // Find 128kbps stream URL
        val stream128 = songDetails.streamUrls?.find { it.bitrate == "128kbps" }
        if (stream128 != null) {
            val extras = mutableMapOf<String, String>().apply {
                stream128.url?.let { put("streamUrl", it) }
                put("quality", "128")
                songDetails.image?.let { put("coverUrl", fixImageUrl(it) ?: "") }
            }
            
            streamables.add(
                Streamable.server(
                    id = "server_128_${songDetails.url?.hashCode() ?: songDetails.title?.hashCode() ?: 0}",
                    quality = 128,
                    title = "128kbps",
                    extras = extras
                )
            )
        }
        
        // If neither is available, use the first available stream
        if (streamables.isEmpty() && !songDetails.streamUrls.isNullOrEmpty()) {
            val firstStream = songDetails.streamUrls.first()
            val quality = if (firstStream.bitrate?.contains("320") == true) 320 else 
                         if (firstStream.bitrate?.contains("128") == true) 128 else 128
            
            val extras = mutableMapOf<String, String>().apply {
                firstStream.url?.let { put("streamUrl", it) }
                put("quality", quality.toString())
                songDetails.image?.let { put("coverUrl", fixImageUrl(it) ?: "") }
                firstStream.bitrate?.let { put("title", it) }
            }
            
            streamables.add(
                Streamable.server(
                    id = "server_${quality}_${songDetails.url?.hashCode() ?: songDetails.title?.hashCode() ?: 0}",
                    quality = quality,
                    title = firstStream.bitrate ?: "",
                    extras = extras
                )
            )
        }
        
        // Parse release date if available
        val releaseDate: Date? = try {
            if (!songDetails.releaseDate.isNullOrEmpty()) {
                // Try different date formats
                val formats = listOf(
                    "yyyy-MM-dd",
                    "dd MMM yyyy",
                    "MMM dd, yyyy",
                    "yyyy"
                )
                
                var parsedCalendar: Calendar? = null
                for (format in formats) {
                    try {
                        val sdf = SimpleDateFormat(format, Locale.getDefault())
                        val parsedDate = sdf.parse(songDetails.releaseDate)
                        parsedCalendar = Calendar.getInstance()
                        parsedCalendar.time = parsedDate
                        break
                    } catch (e: Exception) {
                        // Continue trying other formats
                    }
                }
                
                // Convert Calendar to Echo framework Date
                parsedCalendar?.let { cal ->
                    Date(
                        year = cal.get(Calendar.YEAR),
                        month = cal.get(Calendar.MONTH) + 1, // Calendar months are 0-based
                        day = cal.get(Calendar.DAY_OF_MONTH)
                    )
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
        
        // Build extras map with only non-null values
        val extras = mutableMapOf<String, String>().apply {
            // Use URL as slug if available, otherwise use title
            (songDetails.url ?: songDetails.title)?.let { put("slug", it) }
            songDetails.title?.let { put("title", it) }
            songDetails.url?.let { put("url", it) }
        }
        
        return Track(
            id = songDetails.url ?: songDetails.title ?: "",
            title = songDetails.title ?: "",
            type = Track.Type.Song,
            cover = songDetails.image.toFixedImageHolder(),
            artists = listOf(Artist(
                id = "", 
                name = songDetails.singer ?: "",
                cover = null,
                bio = null,
                background = null,
                banners = emptyList(),
                subtitle = null,
                extras = emptyMap()
            )),
            album = Album(
                id = "", 
                title = songDetails.album ?: "",
                type = null,
                cover = songDetails.image.toFixedImageHolder(),
                artists = emptyList(),
                trackCount = null,
                duration = null,
                releaseDate = null,
                description = null,
                background = songDetails.image.toFixedImageHolder(),
                label = null,
                isExplicit = false,
                subtitle = null,
                extras = emptyMap()
            ),
            duration = null, // We don't have duration info in the API
            playedDuration = null,
            plays = null,
            releaseDate = releaseDate,
            description = null,
            background = songDetails.image.toFixedImageHolder(),
            genres = emptyList(),
            isrc = null,
            albumOrderNumber = null,
            albumDiscNumber = null,
            playlistAddedDate = null,
            isExplicit = false,
            subtitle = songDetails.album,
            extras = extras,
            isPlayable = Track.Playable.Yes,
            streamables = streamables
        )
    }
    
    fun toAlbum(searchAlbum: SearchAlbum): Album {
        // Extract slug from URL if available
        val slug = searchAlbum.slug ?: searchAlbum.url?.let { url ->
            url.substringAfterLast("/").substringBeforeLast(".")
        } ?: searchAlbum.title ?: ""
        
        // Build extras map with only non-null values
        val extras = mutableMapOf<String, String>().apply {
            put("slug", slug)
            searchAlbum.url?.let { put("url", it) }
        }
        
        return Album(
            id = searchAlbum.url ?: searchAlbum.title ?: "",
            title = searchAlbum.title ?: "",
            type = null,
            cover = searchAlbum.image.toFixedImageHolder(),
            artists = emptyList(),
            trackCount = null,
            duration = null,
            releaseDate = null,
            description = null,
            background = searchAlbum.image.toFixedImageHolder(),
            label = null,
            isExplicit = false,
            subtitle = searchAlbum.category,
            extras = extras
        )
    }
    
    fun toAlbum(albumDetails: AlbumDetails): Album {
        // Extract slug from URL if available
        val slug = albumDetails.url?.let { url ->
            url.substringAfterLast("/").substringBeforeLast(".")
        } ?: albumDetails.title ?: ""
        
        // Build extras map with only non-null values
        val extras = mutableMapOf<String, String>().apply {
            put("slug", slug)
            albumDetails.url?.let { put("url", it) }
        }
        
        return Album(
            id = albumDetails.url ?: albumDetails.title ?: "",
            title = albumDetails.title ?: "",
            type = null,
            cover = albumDetails.image.toFixedImageHolder(),
            artists = (albumDetails.artists ?: emptyList()).map { artist ->
                Artist(
                    id = "", 
                    name = artist,
                    cover = null,
                    bio = null,
                    background = null,
                    banners = emptyList(),
                    subtitle = null,
                    extras = emptyMap()
                )
            },
            trackCount = (albumDetails.songs?.size ?: 0).toLong(),
            duration = null,
            releaseDate = null,
            description = albumDetails.description,
            background = albumDetails.image.toFixedImageHolder(),
            label = null,
            isExplicit = false,
            subtitle = albumDetails.year,
            extras = extras
        )
    }
    
    fun toTracks(albumDetails: AlbumDetails): List<Track> {
        return (albumDetails.songs ?: emptyList()).map { song ->
            // Extract slug from URL if available
            val slug = song.slug ?: song.url?.let { url ->
                url.substringAfterLast("/").substringBeforeLast(".")
            } ?: song.title ?: ""
            
            // Build extras map with only non-null values
            val extras = mutableMapOf<String, String>().apply {
                put("slug", slug)
                song.url?.let { put("url", it) }
            }
            
            Track(
                id = song.url ?: song.slug ?: song.title ?: "",
                title = song.title ?: "",
                type = Track.Type.Song,
                cover = song.image.toFixedImageHolder(),
                artists = listOf(Artist(
                    id = "", 
                    name = song.artist ?: "",
                    cover = null,
                    bio = null,
                    background = null,
                    banners = emptyList(),
                    subtitle = null,
                    extras = emptyMap()
                )),
                album = null,
                duration = null,
                playedDuration = null,
                plays = null,
                releaseDate = null,
                description = null,
                background = song.image.toFixedImageHolder(),
                genres = emptyList(),
                isrc = null,
                albumOrderNumber = null,
                albumDiscNumber = null,
                playlistAddedDate = null,
                isExplicit = false,
                subtitle = albumDetails.title,
                extras = extras,
                isPlayable = Track.Playable.Yes,
                streamables = emptyList() // Album tracks don't have stream URLs in this API
            )
        }
    }
}