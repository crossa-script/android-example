package com.crossa.androiddemo.network

import com.crossa.androiddemo.Post
import com.crossa.androiddemo.benchmark.BenchmarkImplementation
import com.crossa.androiddemo.benchmark.BenchmarkResponse
import com.crossa.generated.api.PostsRepository
import com.crossa.generated.runtime.CrossaConfigurationOverrides
import com.crossa.generated.runtime.CrossaNativeList
import com.crossa.generated.runtime.CrossaRuntime
import com.crossa.generated.runtime.CrossaState
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.crossa.generated.model.Post as CrossaPost

class CrossaPostsClient : PostsBenchmarkClient {
    override val implementation: BenchmarkImplementation = BenchmarkImplementation.Crossa

    init {
        CrossaRuntime.configure(CrossaConfigurationOverrides())
    }

    override suspend fun fetchPosts(): BenchmarkResponse {
        val values = awaitPosts()
        return try {
            val nativeReadyCount = values.size
            val materializationStart = android.os.SystemClock.elapsedRealtimeNanos()
            values.forEach { post ->
                Post(post.userId, post.id, post.title, post.body)
            }
            val materializationNanos = android.os.SystemClock.elapsedRealtimeNanos() - materializationStart
            BenchmarkResponse(
                itemCount = nativeReadyCount,
                materializationNanos = materializationNanos
            )
        } finally {
            (values as? AutoCloseable)?.close()
        }
    }

    private suspend fun awaitPosts(): CrossaNativeList<CrossaPost> =
        suspendCancellableCoroutine { continuation ->
            val operation = PostsRepository().getPosts { state ->
                if (!continuation.isActive) {
                    (state as? CrossaState.Success)?.data?.let { (it as? AutoCloseable)?.close() }
                    return@getPosts
                }
                when (state) {
                    is CrossaState.Success -> continuation.resume(state.data)
                    is CrossaState.Failed -> continuation.resumeWithException(state.error)
                    CrossaState.Cancelled -> continuation.cancel()
                }
            }
            continuation.invokeOnCancellation { operation.cancel() }
        }

    override fun close() {
        CrossaRuntime.close()
    }
}
