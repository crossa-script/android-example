package com.crossa.androiddemo.benchmark

import kotlin.math.sqrt

object BenchmarkStatistics {
    fun summarize(
        implementation: BenchmarkImplementation,
        samples: List<BenchmarkSample>,
        durationSelector: (BenchmarkSample) -> Long? = { sample ->
            sample.durationNanos.takeIf { sample.success }
        }
    ): BenchmarkSummary {
        val successful = samples.mapNotNull(durationSelector)
        val sorted = successful.sorted()
        val mean = if (sorted.isEmpty()) 0.0 else sorted.average()
        val variance = if (sorted.size < 2) {
            0.0
        } else {
            sorted.sumOf { value ->
                val delta = value - mean
                delta * delta
            } / (sorted.size - 1)
        }
        return BenchmarkSummary(
            implementation = implementation,
            sampleCount = samples.size,
            successCount = samples.count { it.success },
            failureCount = samples.count { !it.success },
            minNanos = sorted.firstOrNull() ?: 0L,
            maxNanos = sorted.lastOrNull() ?: 0L,
            meanNanos = mean,
            medianNanos = percentile(sorted, 0.50),
            p90Nanos = percentile(sorted, 0.90),
            p95Nanos = percentile(sorted, 0.95),
            standardDeviationNanos = sqrt(variance)
        )
    }

    private fun percentile(sorted: List<Long>, quantile: Double): Long {
        if (sorted.isEmpty()) return 0L
        val rank = ((sorted.size - 1) * quantile).toInt().coerceIn(0, sorted.lastIndex)
        return sorted[rank]
    }
}
