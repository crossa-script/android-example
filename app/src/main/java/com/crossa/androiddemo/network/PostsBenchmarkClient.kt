package com.crossa.androiddemo.network

import com.crossa.androiddemo.benchmark.BenchmarkImplementation
import com.crossa.androiddemo.benchmark.BenchmarkResponse

interface PostsBenchmarkClient {
    val implementation: BenchmarkImplementation

    suspend fun fetchPosts(): BenchmarkResponse

    fun close()
}
