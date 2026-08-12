package com.example.smartplantcare.plantnetnetwork


import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface PlantNetApiService {

    @Multipart
    @POST("v2/identify/{project}")
    suspend fun identifyPlant(
        @Path("project") project: String = "all",
        @Query("api-key") apiKey: String,
        @Query("include-related-images") includeRelatedImages: Boolean = true,
        @Query("lang") language: String = "en",
        @Part images: List<MultipartBody.Part>,
        @Part organs: List<MultipartBody.Part>
    ): Response<PlantNetResponse>
}