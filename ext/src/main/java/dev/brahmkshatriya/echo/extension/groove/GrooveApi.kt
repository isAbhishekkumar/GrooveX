package dev.brahmkshatriya.echo.extension.groove

import dev.brahmkshatriya.echo.common.helpers.ContinuationCallback.Companion.await
import dev.brahmkshatriya.echo.extension.models.AlbumResponse
import dev.brahmkshatriya.echo.extension.models.SearchResponse
import dev.brahmkshatriya.echo.extension.models.SongResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import okhttp3.OkHttpClient
import okhttp3.Request

class GrooveApi {
    
    val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true 
    }
    
    val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            val builder = request.newBuilder()
            builder.addHeader("Accept", "application/json")
            builder.addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36")
            chain.proceed(builder.build())
        }
        .build()
    
    data class Response<T>(
        val json: T,
        val raw: String
    )
    
    suspend fun search(query: String): Response<SearchResponse> {
        val url = "https://testingapipagalworld.indexer.workers.dev/api/search?q=$query"
        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).await()
        val raw = response.body.string()
        return Response(json.decodeFromString(raw), raw)
    }
    
    suspend fun getAlbum(slug: String): Response<AlbumResponse> {
        val url = "https://testingapipagalworld.indexer.workers.dev/api/album?slug=$slug"
        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).await()
        val raw = response.body.string()
        return Response(json.decodeFromString(raw), raw)
    }
    
    suspend fun getSong(slug: String): Response<SongResponse> {
        val url = "https://testingapipagalworld.indexer.workers.dev/api/song?slug=$slug"
        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).await()
        val raw = response.body.string()
        return Response(json.decodeFromString(raw), raw)
    }
}