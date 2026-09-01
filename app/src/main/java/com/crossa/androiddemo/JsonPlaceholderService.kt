package com.crossa.androiddemo

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.HeaderMap

interface JsonPlaceholderService {
    @GET("posts")
    suspend fun fetchPosts(
        @HeaderMap headers: Map<String, String>
    ): Response<List<ApiPost>>
}
