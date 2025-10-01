package dev.brahmkshatriya.echo.extension.groove

import dev.brahmkshatriya.echo.extension.models.AlbumPageResponse
import dev.brahmkshatriya.echo.extension.models.CategoryFeedResponse
import dev.brahmkshatriya.echo.extension.models.LatestReleaseResponse
import dev.brahmkshatriya.echo.extension.models.SearchResponsePayload
import dev.brahmkshatriya.echo.extension.models.SongDetailsResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class GrooveApi(
    private val searchBaseUrl: String = SEARCH_BASE_URL,
    private val albumBaseUrl: String = ALBUM_BASE_URL,
    private val songBaseUrl: String = SONG_BASE_URL,
    private val latestReleaseBaseUrl: String = LATEST_RELEASE_BASE_URL,
    private val categoryFeedBaseUrl: String = CATEGORY_FEED_BASE_URL,
    private val json: Json = jsonParser
)
{
    suspend fun search(query: String): SearchResponsePayload = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext SearchResponsePayload()

        val encodedQuery = encode(query)
        val requestUrl = URL("$searchBaseUrl?q=$encodedQuery")
        val response = execute(requestUrl)
        return@withContext json.decodeFromString(SearchResponsePayload.serializer(), response)
    }

    suspend fun album(url: String): AlbumPageResponse = withContext(Dispatchers.IO) {
        if (url.isBlank()) throw IllegalArgumentException("Album url cannot be blank")

        val encodedUrl = encode(url)
        val requestUrl = URL("$albumBaseUrl?url=$encodedUrl")
        val response = execute(requestUrl)
        return@withContext json.decodeFromString(AlbumPageResponse.serializer(), response)
    }

    suspend fun song(url: String): SongDetailsResponse = withContext(Dispatchers.IO) {
        if (url.isBlank()) throw IllegalArgumentException("Song url cannot be blank")

        val encodedUrl = encode(url)
        val requestUrl = URL("$songBaseUrl?url=$encodedUrl")
        val response = execute(requestUrl)
        return@withContext json.decodeFromString(SongDetailsResponse.serializer(), response)
    }

    suspend fun latestReleases(): LatestReleaseResponse = withContext(Dispatchers.IO) {
        val requestUrl = URL(latestReleaseBaseUrl)
        val response = execute(requestUrl)
        return@withContext json.decodeFromString(LatestReleaseResponse.serializer(), response)
    }

    suspend fun categoryFeed(categoryUrl: String): CategoryFeedResponse = withContext(Dispatchers.IO) {
        if (categoryUrl.isBlank()) throw IllegalArgumentException("Category url cannot be blank")

        val encodedUrl = encode(categoryUrl)
        val requestUrl = URL("$categoryFeedBaseUrl?url=$encodedUrl")
        val response = execute(requestUrl)
        return@withContext json.decodeFromString(CategoryFeedResponse.serializer(), response)
    }

    companion object {
        private const val SEARCH_BASE_URL = "https://searchsongandalbum.dehavarmanenterprises.workers.dev/"
        private const val ALBUM_BASE_URL = "https://albumopener.dehavarmanenterprises.workers.dev/"
        private const val SONG_BASE_URL = "https://songsdeatils.dehavarmanenterprises.workers.dev/"
        private const val LATEST_RELEASE_BASE_URL = "https://recentalbumandlatestrelease.dehavarmanenterprises.workers.dev/"
    private const val CATEGORY_FEED_BASE_URL = "https://categoryapi.dehavarmanenterprises.workers.dev/extract"
        private const val DEFAULT_TIMEOUT = 15_000

        private val jsonParser = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    private fun execute(url: URL): String {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = DEFAULT_TIMEOUT
            readTimeout = DEFAULT_TIMEOUT
            setRequestProperty("Accept", "application/json")
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36"
            )
        }

        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: throw IOException("Unexpected HTTP $status")
            }

            val body = stream.bufferedReader().use { it.readText() }
            if (status !in 200..299) {
                throw IOException("Groove request failed with HTTP $status: $body")
            }
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
