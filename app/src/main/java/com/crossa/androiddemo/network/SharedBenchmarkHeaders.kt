package com.crossa.androiddemo.network

object SharedBenchmarkHeaders {
    val values: Map<String, String> = mapOf(
        "Accept" to "application/json",
        "Cache-Control" to "no-cache, no-store, max-age=0",
        "Pragma" to "no-cache",
        "Expires" to "0",
        "X-Crossa-Benchmark" to "posts"
    )
}
