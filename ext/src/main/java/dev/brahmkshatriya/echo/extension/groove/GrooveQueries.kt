package dev.brahmkshatriya.echo.extension.groove

class GrooveQueries(
    private val api: GrooveApi
) {
    suspend fun search(query: String) = api.search(query)
    
    suspend fun getAlbum(slug: String) = api.getAlbum(slug)
    
    suspend fun getSong(slug: String) = api.getSong(slug)
}