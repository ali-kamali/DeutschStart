package com.deutschstart.app.data.remote

import com.deutschstart.app.data.model.ContentPackMetadata
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

interface ContentApiService {
    @GET("api/v1/packs/latest")
    suspend fun getLatestPackMetadata(): Response<ContentPackMetadata>

    @Streaming
    @GET
    suspend fun downloadPack(@Url url: String): Response<ResponseBody>
}
