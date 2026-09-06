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
