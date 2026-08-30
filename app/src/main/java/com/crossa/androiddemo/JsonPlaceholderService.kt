package com.crossa.androiddemo

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

interface JsonPlaceholderService {
    @GET("posts")
    suspend fun fetchPosts(
        @Header("X-Request-Source") source: String = "retrofit-okhttp"
    ): Response<List<ApiPost>>
}
