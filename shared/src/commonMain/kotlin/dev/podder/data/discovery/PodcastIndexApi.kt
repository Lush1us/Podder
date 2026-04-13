package dev.podder.data.discovery

import dev.podder.data.search.SearchResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

private const val BASE_URL = "https://api.podcastindex.org/api/1.0"
private const val USER_AGENT = "Podder/1.0"

class PodcastIndexApi(
    httpClient: HttpClient,
    apiKey: String,
    apiSecret: String,
) {
    private val httpClient = httpClient
    private val apiKey = apiKey.trim()
    private val apiSecret = apiSecret.trim()
    suspend fun trending(max: Int = 25, lang: String = "en"): List<SearchResult> {
        val response = httpClient.get("$BASE_URL/podcasts/trending") {
            addAuthHeaders()
            parameter("max", max)
            parameter("lang", lang)
        }
        val text = response.requireSuccess()
        return json.decodeFromString<PodcastIndexTrendingResponse>(text).feeds
            .mapNotNull { it.toSearchResultOrNull() }
    }

    suspend fun categories(): List<DiscoveryCategory> {
        val response = httpClient.get("$BASE_URL/categories/list") {
            addAuthHeaders()
        }
        val text = response.requireSuccess()
        return json.decodeFromString<PodcastIndexCategoriesResponse>(text).feeds
            .filter { it.id > 0 }
            .map { DiscoveryCategory(id = it.id, name = it.name) }
            .sortedBy { it.name }
    }

    suspend fun byCategory(categoryId: Int, max: Int = 40): List<SearchResult> {
        val response = httpClient.get("$BASE_URL/podcasts/trending") {
            addAuthHeaders()
            parameter("cat", categoryId)
            parameter("max", max)
        }
        val text = response.requireSuccess()
        return json.decodeFromString<PodcastIndexTrendingResponse>(text).feeds
            .mapNotNull { it.toSearchResultOrNull() }
    }

    suspend fun search(query: String, max: Int = 40): List<SearchResult> {
        val response = httpClient.get("$BASE_URL/search/byterm") {
            addAuthHeaders()
            parameter("q", query)
            parameter("max", max)
        }
        val text = response.requireSuccess()
        return json.decodeFromString<PodcastIndexSearchResponse>(text).feeds
            .mapNotNull { it.toSearchResultOrNull() }
    }

    private suspend fun HttpResponse.requireSuccess(): String {
        val body = bodyAsText()
        if (!status.isSuccess()) {
            throw RuntimeException("Podcast Index API ${status.value}: $body")
        }
        return body
    }

    private fun io.ktor.client.request.HttpRequestBuilder.addAuthHeaders() {
        val epoch = currentEpochSeconds().toString()
        val authHash = sha1Hex(apiKey + apiSecret + epoch)
        header("X-Auth-Key", apiKey)
        header("X-Auth-Date", epoch)
        header("Authorization", authHash)
        header("User-Agent", USER_AGENT)
    }
}

internal expect fun currentEpochSeconds(): Long
