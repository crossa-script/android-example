package com.crossa.androiddemo.benchmark

import android.os.Build
import android.os.SystemClock
import com.crossa.androiddemo.BuildConfig
import com.crossa.androiddemo.network.CrossaPostsClient
import com.crossa.androiddemo.network.KtorPostsClient
import com.crossa.androiddemo.network.PostsBenchmarkClient
import com.crossa.androiddemo.network.RetrofitPostsClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class BenchmarkRunner(
    private val configuration: BenchmarkConfiguration = BenchmarkConfiguration()
) {
    suspend fun run(): BenchmarkRunResult {
        val clients = createClients()
        try {
            if (configuration.mode == BenchmarkMode.Cold) {
                return runCold(clients)
            }
            warmup(clients)
            val samples = measure(clients)
            return result(samples)
        } finally {
            clients.forEach { it.close() }
        }
    }

    private fun createClients(): List<PostsBenchmarkClient> = listOf(
        CrossaPostsClient(),
        RetrofitPostsClient(configuration),
        KtorPostsClient(configuration)
    )

    private suspend fun warmup(clients: List<PostsBenchmarkClient>) {
        repeat(configuration.warmupIterations) {
            clients.forEach { client ->
                currentCoroutineContext().ensureActive()
                runCatching { client.fetchPosts() }
            }
        }
    }

    private suspend fun measure(clients: List<PostsBenchmarkClient>): List<BenchmarkSample> {
        val samples = mutableListOf<BenchmarkSample>()
        repeat(configuration.measuredIterations) { iteration ->
            rotatingClientOrder(clients, iteration).forEach { client ->
                samples += sample(client, iteration)
            }
        }
        return samples
    }

    private suspend fun runCold(template: List<PostsBenchmarkClient>): BenchmarkRunResult {
        template.forEach { it.close() }
        val samples = mutableListOf<BenchmarkSample>()
        repeat(configuration.measuredIterations) { iteration ->
            rotatingImplementationOrder(
                listOf(
                    BenchmarkImplementation.Crossa,
                    BenchmarkImplementation.Retrofit,
                    BenchmarkImplementation.Ktor
                ),
                iteration
            ).forEach { implementation ->
                val client = client(implementation)
                try {
                    samples += sample(client, iteration)
                } finally {
                    client.close()
                }
            }
        }
        return result(samples)
    }

    private fun client(implementation: BenchmarkImplementation): PostsBenchmarkClient =
        when (implementation) {
            BenchmarkImplementation.Crossa -> CrossaPostsClient()
            BenchmarkImplementation.Retrofit -> RetrofitPostsClient(configuration)
            BenchmarkImplementation.Ktor -> KtorPostsClient(configuration)
        }

    private suspend fun sample(
        client: PostsBenchmarkClient,
        iteration: Int
    ): BenchmarkSample {
        currentCoroutineContext().ensureActive()
        val start = SystemClock.elapsedRealtimeNanos()
        return try {
            val response = client.fetchPosts()
            val duration = SystemClock.elapsedRealtimeNanos() - start
            BenchmarkSample(
                implementation = client.implementation,
                iteration = iteration,
                durationNanos = duration,
                success = true,
                itemCount = response.itemCount,
                materializationNanos = response.materializationNanos
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            BenchmarkSample(
                implementation = client.implementation,
                iteration = iteration,
                durationNanos = SystemClock.elapsedRealtimeNanos() - start,
                success = false,
                itemCount = 0,
                error = error.message ?: error::class.java.simpleName
            )
        }
    }

    private fun rotatingClientOrder(
        clients: List<PostsBenchmarkClient>,
        iteration: Int
    ): List<PostsBenchmarkClient> {
        if (clients.isEmpty()) return clients
        val offset = iteration % clients.size
        return clients.drop(offset) + clients.take(offset)
    }

    private fun rotatingImplementationOrder(
        implementations: List<BenchmarkImplementation>,
        iteration: Int
    ): List<BenchmarkImplementation> {
        val offset = iteration % implementations.size
        return implementations.drop(offset) + implementations.take(offset)
    }

    private fun result(samples: List<BenchmarkSample>): BenchmarkRunResult {
        val summaries = BenchmarkImplementation.entries.map { implementation ->
            BenchmarkStatistics.summarize(
                implementation,
                samples.filter { it.implementation == implementation }
            )
        }
        val crossaSamples = samples.filter { it.implementation == BenchmarkImplementation.Crossa }
        val nativeReady = BenchmarkStatistics.summarize(BenchmarkImplementation.Crossa, crossaSamples) { sample ->
            if (!sample.success) {
                null
            } else {
                sample.durationNanos - (sample.materializationNanos ?: 0L)
            }
        }
        val materialization = BenchmarkStatistics.summarize(BenchmarkImplementation.Crossa, crossaSamples) { sample ->
            sample.materializationNanos?.takeIf { sample.success }
        }
        return BenchmarkRunResult(
            metadata = metadata(),
            summaries = summaries,
            crossaSplit = CrossaSplitSummary(
                nativeReady = nativeReady,
                materialization = materialization.takeIf { it.successCount > 0 },
                applicationReady = summaries.first { it.implementation == BenchmarkImplementation.Crossa }
            ),
            samples = samples
        )
    }

    private fun metadata(): BenchmarkMetadata = BenchmarkMetadata(
        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
        androidVersion = Build.VERSION.RELEASE ?: "unknown",
        abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
        appVersion = BuildConfig.VERSION_NAME,
        buildType = BuildConfig.BENCHMARK_BUILD,
        crossaArtifact = BuildConfig.CROSSA_ARTIFACT,
        crossaArtifactVersion = BuildConfig.CROSSA_ARTIFACT_VERSION,
        crossaArtifactSha256 = BuildConfig.CROSSA_ARTIFACT_SHA256,
        crossaSourceCommit = BuildConfig.CROSSA_SOURCE_COMMIT,
        warmupIterations = configuration.warmupIterations,
        measuredIterations = configuration.measuredIterations,
        endpoint = configuration.endpoint,
        endpointKind = configuration.endpointKind,
        mode = configuration.mode,
        timestampMillis = System.currentTimeMillis()
    )
}
