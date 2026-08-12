package com.example.smartplantcare.plantnetnetwork

import com.google.gson.annotations.SerializedName



data class PlantNetResponse(
    @SerializedName("query") val query: PlantNetQuery?,
    @SerializedName("predictedOrgans") val predictedOrgans: List<PredictedOrgan>?,
    @SerializedName("language") val language: String?,
    @SerializedName("preferedReferential") val preferredReferential: String?,
    @SerializedName("bestMatch") val bestMatch: String?,
    @SerializedName("results") val results: List<PlantNetResult>?,
    @SerializedName("version") val version: String?,
    @SerializedName("remainingIdentificationRequests") val remainingIdentificationRequests: Int?
)

data class PlantNetQuery(
    @SerializedName("project") val project: String?,
    @SerializedName("images") val images: List<String>?,
    @SerializedName("organs") val organs: List<String>?,
    @SerializedName("includeRelatedImages") val includeRelatedImages: Boolean?
)

data class PredictedOrgan(
    @SerializedName("image") val image: String?,
    @SerializedName("filename") val filename: String?,
    @SerializedName("organ") val organ: String?,
    @SerializedName("score") val score: Double?
)

data class PlantNetResult(
    @SerializedName("score") val score: Double,
    @SerializedName("species") val species: PlantNetSpecies,
    @SerializedName("images") val images: List<PlantNetImage>?
)

data class PlantNetSpecies(
    @SerializedName("scientificNameWithoutAuthor") val scientificNameWithoutAuthor: String?,
    @SerializedName("scientificNameAuthorship") val scientificNameAuthorship: String?,
    @SerializedName("scientificName") val scientificName: String?,
    @SerializedName("genus") val genus: PlantNetTaxon?,
    @SerializedName("family") val family: PlantNetTaxon?,
    @SerializedName("commonNames") val commonNames: List<String>?
)

data class PlantNetTaxon(
    @SerializedName("scientificNameWithoutAuthor") val scientificNameWithoutAuthor: String?,
    @SerializedName("scientificNameAuthorship") val scientificNameAuthorship: String?,
    @SerializedName("scientificName") val scientificName: String?
)

data class PlantNetImage(
    @SerializedName("organ") val organ: String?,
    @SerializedName("author") val author: String?,
    @SerializedName("license") val license: String?,
    @SerializedName("date") val date: PlantNetImageDate?,
    @SerializedName("citation") val citation: String?,
    @SerializedName("url") val url: PlantNetImageUrl?
)

data class PlantNetImageDate(
    @SerializedName("timestamp") val timestamp: Long?,
    @SerializedName("string") val string: String?
)

data class PlantNetImageUrl(
    @SerializedName("o") val original: String?,
    @SerializedName("m") val medium: String?,
    @SerializedName("s") val small: String?
)


data class PlantIdentificationSummary(
    val plantName: String,
    val scientificName: String,
    val species: String,
    val family: String,
    val description: String,

    val relatedImageUrls: List<String>,
    val confidenceScore: Double
)


sealed class PlantNetOutcome {
    data class Success(
        val topMatch: PlantIdentificationSummary,
        val allResults: List<PlantIdentificationSummary>,
        val remainingRequests: Int?
    ) : PlantNetOutcome()

    data class ApiError(val code: Int, val message: String) : PlantNetOutcome()
    data class NetworkError(val message: String) : PlantNetOutcome()
    data class NoResultsError(val message: String) : PlantNetOutcome()
    data class UnknownError(val message: String) : PlantNetOutcome()
}