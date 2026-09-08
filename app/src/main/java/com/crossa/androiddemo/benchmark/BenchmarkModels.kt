package com.crossa.androiddemo.benchmark

enum class BenchmarkImplementation {
    Crossa,
    Retrofit,
    Ktor
}

enum class BenchmarkMode {
    Cold,
    Warm
}

enum class BenchmarkEndpointKind {
    Remote,
    Controlled
}

data class BenchmarkConfiguration(
    val warmupIterations: Int = 2,
    val measuredIterations: Int = 8,
    val endpoint: String = "https://jsonplaceholder.typicode.com/posts",
    val endpointKind: BenchmarkEndpointKind = BenchmarkEndpointKind.Remote,
    val mode: BenchmarkMode = BenchmarkMode.Warm,
    val timeoutMillis: Long = 10_000L
)

data class BenchmarkResponse(
    val itemCount: Int,
    val materializationNanos: Long? = null
)

data class BenchmarkSample(
    val implementation: BenchmarkImplementation,
    val iteration: Int,
    val durationNanos: Long,
    val success: Boolean,
    val itemCount: Int,
    val materializationNanos: Long? = null,
    val error: String? = null
)

data class BenchmarkSummary(
    val implementation: BenchmarkImplementation,
    val sampleCount: Int,
    val successCount: Int,
    val failureCount: Int,
    val minNanos: Long,
    val maxNanos: Long,
    val meanNanos: Double,
    val medianNanos: Long,
    val p90Nanos: Long,
    val p95Nanos: Long,
    val standardDeviationNanos: Double,
    val materializationMedianNanos: Long? = null
)

data class CrossaSplitSummary(
    val nativeReady: BenchmarkSummary,
    val materialization: BenchmarkSummary?,
    val applicationReady: BenchmarkSummary
)

data class BenchmarkMetadata(
    val deviceModel: String,
    val androidVersion: String,
    val abi: String,
    val appVersion: String,
    val buildType: String,
    val warmupIterations: Int,
    val measuredIterations: Int,
    val endpoint: String,
    val endpointKind: BenchmarkEndpointKind,
    val mode: BenchmarkMode,
    val timestampMillis: Long
)

data class BenchmarkRunResult(
    val metadata: BenchmarkMetadata,
    val summaries: List<BenchmarkSummary>,
    val crossaSplit: CrossaSplitSummary?,
    val samples: List<BenchmarkSample>
)
