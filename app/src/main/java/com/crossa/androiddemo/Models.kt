package com.crossa.androiddemo

data class ApiPost(
    val userId: Int,
    val id: Int,
    val title: String,
    val body: String
) {
    fun toDomain(): Post = Post(userId, id, title, body)
}

data class Post(
    val userId: Int,
    val id: Int,
    val title: String,
    val body: String
)

data class ScenarioResult(
    val name: String,
    val requestUrl: String,
    val requestCount: Int,
    val successCount: Int,
    val totalMs: Long,
    val averageMs: Double,
    val minMs: Long,
    val maxMs: Long,
    val postCount: Int,
    val firstPost: Post?,
    val timingsMs: List<Long>,
    val requestHeaders: Map<String, String>,
    val responseInfo: String,
    val responsePreview: String,
    val error: String? = null
)

data class ScenarioCall(
    val statusCode: Int,
    val posts: List<Post>,
    val responseHeaderCount: Int,
    val responsePreview: String
)
