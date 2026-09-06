package com.crossa.androiddemo.network

import com.crossa.androiddemo.Post
import com.crossa.androiddemo.benchmark.BenchmarkConfiguration
import com.crossa.androiddemo.benchmark.BenchmarkImplementation
import com.crossa.androiddemo.benchmark.BenchmarkResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import org.json.JSONArray

class KtorPostsClient(
    private val configuration: BenchmarkConfiguration
) : PostsBenchmarkClient {
    override val implementation: BenchmarkImplementation = BenchmarkImplementation.Ktor

    private val client: HttpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = configuration.timeoutMillis
            connectTimeoutMillis = configuration.timeoutMillis
            socketTimeoutMillis = configuration.timeoutMillis
        }
    }

    override suspend fun fetchPosts(): BenchmarkResponse {
        val response = client.get(configuration.endpoint) {
            SharedBenchmarkHeaders.values.forEach { (key, value) -> header(key, value) }
        }
        val posts = JSONArray(response.bodyAsText()).toPosts()
        return BenchmarkResponse(itemCount = posts.size)
    }

    override fun close() {
        client.close()
    }

    private fun JSONArray.toPosts(): List<Post> = buildList {
        for (index in 0 until length()) {
            val item = getJSONObject(index)
            add(
                Post(
                    userId = item.getInt("userId"),
                    id = item.getInt("id"),
                    title = item.getString("title"),
                    body = item.getString("body")
                )
            )
        }
    }
}
