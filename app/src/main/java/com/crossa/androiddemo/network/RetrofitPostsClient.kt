package com.crossa.androiddemo.network

import com.crossa.androiddemo.JsonPlaceholderService
import com.crossa.androiddemo.benchmark.BenchmarkConfiguration
import com.crossa.androiddemo.benchmark.BenchmarkImplementation
import com.crossa.androiddemo.benchmark.BenchmarkResponse
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class RetrofitPostsClient(
    configuration: BenchmarkConfiguration
) : PostsBenchmarkClient {
    override val implementation: BenchmarkImplementation = BenchmarkImplementation.Retrofit

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(configuration.timeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(configuration.timeoutMillis, TimeUnit.MILLISECONDS)
        .callTimeout(configuration.timeoutMillis, TimeUnit.MILLISECONDS)
        .build()

    private val service: JsonPlaceholderService = Retrofit.Builder()
        .baseUrl(baseUrl(configuration.endpoint))
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(JsonPlaceholderService::class.java)

    override suspend fun fetchPosts(): BenchmarkResponse {
        val response = service.fetchPosts(SharedBenchmarkHeaders.values)
        if (!response.isSuccessful) {
            error("HTTP ${response.code()}")
        }
        val posts = response.body().orEmpty()
        posts.forEach { post ->
            post.toDomain()
        }
        return BenchmarkResponse(itemCount = posts.size)
    }

    override fun close() {
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }

    private fun baseUrl(endpoint: String): String {
        val url = endpoint.removeSuffix("/")
        val index = url.lastIndexOf('/')
        return if (index > "https://".length) url.substring(0, index + 1) else "$url/"
    }
}
