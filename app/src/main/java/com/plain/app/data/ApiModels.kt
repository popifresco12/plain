package com.plain.app.data

import com.google.gson.annotations.SerializedName

// === Auth ===

data class LoginRequest(val username: String, val password: String)
data class RegisterRequest(val username: String, val email: String, val password: String)

data class UserResponse(
    val id: Int,
    val username: String,
    val email: String,
    @SerializedName("created_at") val createdAt: String
)

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    val user: UserResponse
)

// === Plans ===

data class PlanResponse(
    val id: Int,
    val title: String,
    val description: String,
    val location: String,
    val price: String,
    @SerializedName("plan_type") val planType: String,
    val duration: String,
    val category: String,
    val city: String,
    val emoji: String,
    @SerializedName("is_default") val isDefault: Boolean
)

data class PlanCreateRequest(
    val title: String,
    val description: String,
    val location: String,
    val price: String,
    @SerializedName("plan_type") val planType: String,
    val duration: String,
    val category: String,
    val city: String,
    val emoji: String
)

// === Webhook ===

data class WebhookRequest(
    val url: String,
    @SerializedName("api_key") val apiKey: String? = null
)

data class WebhookResponse(
    val id: Int,
    val url: String?,
    @SerializedName("api_key") val apiKey: String?,
    val active: Boolean
)

data class WebhookTriggerResponse(
    val status: String,
    @SerializedName("webhook_url") val webhookUrl: String,
    @SerializedName("response_code") val responseCode: Int
)
