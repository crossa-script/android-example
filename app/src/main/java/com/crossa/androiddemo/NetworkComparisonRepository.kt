package com.crossa.androiddemo

import com.crossa.generated.api.posts
import com.crossa.generated.model.Post as CrossaPost
import com.crossa.generated.runtime.CrossaConfigurationOverrides
import com.crossa.generated.runtime.CrossaRuntime
import com.crossa.generated.runtime.CrossaState
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CancellationException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class NetworkComparisonRepository(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val requestDelayMs: Long = 750L
) {
    private val apiUrl = "https://jsonplaceholder.typicode.com/posts"

    private val retrofitHeaders = mapOf(
        "Accept" to "application/json",
        "X-Demo-Client" to "retrofit-okhttp",
        "X-Request-Source" to "retrofit-okhttp"
    )

    private val ktorHeaders = mapOf(
        "Accept" to "application/json",
        "X-Demo-Client" to "ktor-client",
        "X-Request-Source" to "ktor"
    )

    private val crossaHeaders = mapOf(
        "Accept" to "application/json",
        "X-Crossa-Scenario" to "cli"
    )

    private val crossaApi = posts()

    init {
        CrossaRuntime.configure(CrossaConfigurationOverrides(

        ))
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(Interceptor { chain ->
            val request = chain.request()
                .newBuilder()
                .header("Accept", "application/json")
                .header("X-Demo-Client", "retrofit-okhttp")
                .build()
            chain.proceed(request)
        })
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val retrofitService = Retrofit.Builder()
        .baseUrl("https://jsonplaceholder.typicode.com/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(JsonPlaceholderService::class.java)

    private val ktorClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 10_000
        }
        defaultRequest {
            header("Accept", "application/json")
            header("X-Demo-Client", "ktor-client")
        }
    }

    suspend fun runAll(): List<ScenarioResult> = listOf(
        runRetrofitScenario(),
        runKtorScenario(),
        runCrossaScenario()
    )

    fun close() {
        ktorClient.close()
        CrossaRuntime.close()
    }

    private suspend fun runRetrofitScenario(): ScenarioResult = runMeasuredScenario(
        name = "Retrofit + OkHttp",
        requestHeaders = retrofitHeaders
    ) {
        val response = retrofitService.fetchPosts()
        val posts = response.body().orEmpty().map(ApiPost::toDomain)
        if (!response.isSuccessful) {
            error("HTTP ${response.code()}")
        }
        ScenarioCall(
            statusCode = response.code(),
            posts = posts,
            responseHeaderCount = response.headers().size,
            responsePreview = posts.preview()
        )
    }

    private suspend fun runKtorScenario(): ScenarioResult = runMeasuredScenario(
        name = "Ktor Client",
        requestHeaders = ktorHeaders
    ) {
        val response = ktorClient.get(apiUrl) {
            header("X-Request-Source", "ktor")
        }
        val raw = response.bodyAsText()
        val posts = raw.toPosts()
        ScenarioCall(
            statusCode = response.status.value,
            posts = posts,
            responseHeaderCount = response.headers.names().size,
            responsePreview = posts.preview()
        )
    }

    private suspend fun runCrossaScenario(): ScenarioResult = runMeasuredScenario(
        name = "Crossa AAR @AsyncAfter",
        requestHeaders = crossaHeaders
    ) {
        val posts = fetchCrossaPosts()
        ScenarioCall(
            statusCode = 200,
            posts = posts,
            responseHeaderCount = 0,
            responsePreview = posts.preview()
        )
    }

    private suspend fun runMeasuredScenario(
        name: String,
        requestHeaders: Map<String, String>,
        block: suspend () -> ScenarioCall
    ): ScenarioResult = withContext(ioDispatcher) {
        val timings = mutableListOf<Long>()
        var successCount = 0
        var lastCall: ScenarioCall? = null
        var errorMessage: String? = null

        repeat(5) { index ->
            currentCoroutineContext().ensureActive()
            val start = System.nanoTime()
            try {
                val call = block()
                lastCall = call
                successCount += 1
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                errorMessage = error.message ?: error::class.java.simpleName
            } finally {
                timings += (System.nanoTime() - start) / 1_000_000
            }
            if (index < 4) {
                delay(requestDelayMs)
            }
        }

        val posts = lastCall?.posts.orEmpty()
        ScenarioResult(
            name = name,
            requestUrl = apiUrl,
            requestCount = 5,
            successCount = successCount,
            totalMs = timings.sum(),
            averageMs = timings.averageOrZero(),
            minMs = timings.minOrNull() ?: 0,
            maxMs = timings.maxOrNull() ?: 0,
            postCount = posts.size,
            firstPost = posts.firstOrNull(),
            timingsMs = timings,
            requestHeaders = requestHeaders,
            responseInfo = lastCall?.let {
                "HTTP ${it.statusCode}, response headers: ${it.responseHeaderCount}"
            } ?: "No response",
            responsePreview = lastCall?.responsePreview.orEmpty(),
            error = errorMessage
        )
    }

    private fun String.toPosts(): List<Post> {
        val array = JSONArray(this)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
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

    private suspend fun fetchCrossaPosts(): List<Post> = suspendCancellableCoroutine { continuation ->
        crossaApi.fetchPosts { state ->
            if (!continuation.isActive) {
                return@fetchPosts
            }
            when (state) {
                is CrossaState.Success -> {
                    val values = state.data
                    try {
                        continuation.resume(values.map { it.toDomain() })
                    } finally {
                        (values as? AutoCloseable)?.close()
                    }
                }
                is CrossaState.Failed -> {
                    continuation.resumeWithException(IllegalStateException(state.error.message))
                }
                CrossaState.Cancelled -> {
                    continuation.cancel(CancellationException("Crossa request cancelled"))
                }
            }
        }
    }

    private fun CrossaPost.toDomain(): Post = Post(
        userId = userId,
        id = id,
        title = title,
        body = body
    )

    private fun List<Post>.preview(): String = take(3).joinToString(separator = "\n") {
        "#${it.id} user=${it.userId} ${it.title}"
    }

    private fun List<Long>.averageOrZero(): Double {
        if (isEmpty()) return 0.0
        return String.format(Locale.US, "%.2f", average()).toDouble()
    }
}
