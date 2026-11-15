package com.iank.im.peek.data.remote.gemini

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface GeminiApiService {
    @POST
    suspend fun generateContent(
        @Url url: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body body: RequestBody
    ): Response<ResponseBody>

    @GET
    suspend fun listModels(
        @Url url: String,
        @Header("x-goog-api-key") apiKey: String
    ): Response<ResponseBody>

    @POST
    suspend fun startResumableUpload(
        @Url url: String,
        @Header("x-goog-api-key") apiKey: String,
        @Header("X-Goog-Upload-Protocol") protocol: String = "resumable",
        @Header("X-Goog-Upload-Command") command: String = "start",
        @Header("X-Goog-Upload-Header-Content-Length") contentLength: Long,
        @Header("X-Goog-Upload-Header-Content-Type") contentType: String,
        @Body body: RequestBody
    ): Response<ResponseBody>

    @POST
    suspend fun uploadResumableBytes(
        @Url uploadUrl: String,
        @Header("x-goog-api-key") apiKey: String,
        @Header("X-Goog-Upload-Offset") offset: Long = 0L,
        @Header("X-Goog-Upload-Command") command: String = "upload, finalize",
        @Body fileBody: RequestBody
    ): Response<ResponseBody>

    @GET
    suspend fun getFileMetadata(
        @Url url: String,
        @Header("x-goog-api-key") apiKey: String
    ): Response<ResponseBody>

    @DELETE
    suspend fun deleteFile(
        @Url url: String,
        @Header("x-goog-api-key") apiKey: String
    ): Response<ResponseBody>
}
