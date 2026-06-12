package com.plain.app.data

import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // Auth
    @POST("api/register")
    suspend fun register(@Body body: RegisterRequest): Response<TokenResponse>

    @POST("api/login")
    suspend fun login(@Body body: LoginRequest): Response<TokenResponse>

    @GET("api/me")
    suspend fun getMe(): Response<UserResponse>

    // Plans
    @GET("api/plans")
    suspend fun getPlans(
        @Query("city") city: String? = null,
        @Query("plan_type") planType: String? = null,
        @Query("category") category: String? = null
    ): Response<List<PlanResponse>>

    @POST("api/plans")
    suspend fun createPlan(@Body body: PlanCreateRequest): Response<PlanResponse>

    // Favorites
    @GET("api/favorites")
    suspend fun getFavorites(): Response<List<FavoriteResponse>>

    @POST("api/favorites/{planId}")
    suspend fun addFavorite(@Path("planId") planId: Int): Response<FavoriteActionResponse>

    @DELETE("api/favorites/{planId}")
    suspend fun removeFavorite(@Path("planId") planId: Int): Response<FavoriteActionResponse>

    // Disliked Tags
    @POST("api/dislike-tags")
    suspend fun dislikeTags(@Body body: DislikeTagsRequest): Response<DislikeTagsResponse>

    // Webhook
    @GET("api/webhook")
    suspend fun getWebhook(): Response<WebhookResponse>

    @POST("api/webhook")
    suspend fun saveWebhook(@Body body: WebhookRequest): Response<WebhookResponse>

    @POST("api/webhook/trigger/{planId}")
    suspend fun triggerWebhook(@Path("planId") planId: Int): Response<WebhookTriggerResponse>
}
