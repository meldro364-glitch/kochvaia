package com.kochvaia.app.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @POST("auth/google")
    suspend fun signInWithGoogle(@Body body: GoogleAuthRequest): AuthResponse

    @POST("auth/qr/create")
    suspend fun createQr(@Body body: QrCreateRequest): QrCreateResponse

    @POST("auth/qr/redeem")
    suspend fun redeemQr(@Body body: QrRedeemRequest): QrRedeemResponse

    @POST("auth/logout")
    suspend fun logout()

    @GET("me")
    suspend fun me(): MeResponse

    @GET("kids")
    suspend fun listKids(): KidsListResponse

    @POST("kids")
    suspend fun createKid(@Body body: CreateKidRequest): KidDto

    @PATCH("kids/{id}")
    suspend fun patchKid(@Path("id") id: String, @Body body: PatchKidRequest)

    @DELETE("kids/{id}")
    suspend fun deleteKid(@Path("id") id: String)

    @POST("kids/{id}/stars")
    suspend fun awardStar(@Path("id") id: String, @Body body: AwardStarRequest): AwardStarResponse

    @DELETE("kids/{id}/stars/{date}")
    suspend fun undoStar(@Path("id") id: String, @Path("date") date: String)

    @POST("kids/{id}/deductions")
    suspend fun deduct(@Path("id") id: String, @Body body: DeductRequest): DeductResponse

    @GET("kids/{id}/days")
    suspend fun days(
        @Path("id") id: String,
        @Query("from") from: String,
        @Query("to") to: String,
    ): DaysResponse

    @GET("kids/{id}/summary")
    suspend fun summary(@Path("id") id: String): SummaryResponse

    @POST("kids/{id}/seen")
    suspend fun seen(@Path("id") id: String): SeenResponse
}
