package com.iank.im.peek.data.remote.openai

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface OpenAiApiService {
    @POST
    suspend fun createChatCompletion(
        @Url url: String,
        @Header("Authorization") authHeader: String?,
        @Body body: RequestBody
    ): Response<ResponseBody>

    @GET
    suspend fun getModels(
        @Url url: String,
        @Header("Authorization") authHeader: String?
    ): Response<ResponseBody>
}
