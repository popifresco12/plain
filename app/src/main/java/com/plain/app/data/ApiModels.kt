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
    @SerializedName("availability") val availability: String = "Todo el año",
    val category: String,
    val city: String,
    val emoji: String,
    val tags: List<String> = emptyList(),
    @SerializedName("is_default") val isDefault: Boolean,
    @SerializedName("is_sponsored") val isSponsored: Boolean = false,
    @SerializedName("business_id") val businessId: Int? = null,
    @SerializedName("budget_cents") val budgetCents: Int = 0,
    @SerializedName("spent_cents") val spentCents: Int = 0,
    @SerializedName("cost_per_like_cents") val costPerLikeCents: Int = 0,
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("likes_remaining") val likesRemaining: Int = 0,
    @SerializedName("available_from") val availableFrom: String? = null,
    @SerializedName("available_until") val availableUntil: String? = null,
    val recurring: String? = null,
    @SerializedName("is_available_now") val isAvailableNow: Boolean = true
)

data class PlanCreateRequest(
    val title: String,
    val description: String,
    val location: String,
    val price: String,
    @SerializedName("plan_type") val planType: String,
    val duration: String,
    @SerializedName("availability") val availability: String = "Todo el año",
    val category: String,
    val city: String,
    val emoji: String,
    @SerializedName("available_from") val availableFrom: String? = null,
    @SerializedName("available_until") val availableUntil: String? = null,
    val recurring: String? = null
)

// === Business ===

data class BusinessRegisterRequest(
    @SerializedName("company_name") val companyName: String,
    val email: String,
    val password: String
)

data class BusinessLoginRequest(
    val email: String,
    val password: String
)

data class BusinessResponse(
    val id: Int,
    @SerializedName("company_name") val companyName: String,
    val email: String,
    @SerializedName("balance_cents") val balanceCents: Int = 0,
    @SerializedName("created_at") val createdAt: String
)

data class BusinessTokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    val business: BusinessResponse
)

data class SponsoredPlanCreateRequest(
    val title: String,
    val description: String,
    val location: String,
    val price: String,
    @SerializedName("plan_type") val planType: String,
    val duration: String,
    val category: String,
    val city: String,
    val emoji: String,
    val tags: List<String>,
    @SerializedName("budget_cents") val budgetCents: Int,
    @SerializedName("cost_per_like_cents") val costPerLikeCents: Int
)

data class SponsoredPlanResponse(
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
    val tags: List<String>,
    @SerializedName("is_sponsored") val isSponsored: Boolean,
    @SerializedName("budget_cents") val budgetCents: Int,
    @SerializedName("spent_cents") val spentCents: Int,
    @SerializedName("cost_per_like_cents") val costPerLikeCents: Int,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("likes_remaining") val likesRemaining: Int,
    @SerializedName("created_at") val createdAt: String
)

data class BusinessStats(
    @SerializedName("total_plans") val totalPlans: Int,
    @SerializedName("active_plans") val activePlans: Int,
    @SerializedName("total_budget_cents") val totalBudgetCents: Int,
    @SerializedName("total_spent_cents") val totalSpentCents: Int,
    @SerializedName("total_likes") val totalLikes: Int,
    @SerializedName("balance_cents") val balanceCents: Int
)

data class BudgetTopUpRequest(
    @SerializedName("amount_cents") val amountCents: Int
)

data class BudgetTopUpResponse(
    val status: String,
    @SerializedName("new_balance_cents") val newBalanceCents: Int
)

// === Favorites ===

data class FavoritePlan(
    val id: Int,
    val title: String,
    val description: String,
    val location: String,
    val price: String,
    @SerializedName("plan_type") val planType: String,
    val duration: String,
    @SerializedName("availability") val availability: String = "Todo el año",
    val category: String,
    val city: String,
    val emoji: String,
    val tags: List<String> = emptyList(),
    @SerializedName("is_default") val isDefault: Boolean,
    @SerializedName("is_sponsored") val isSponsored: Boolean = false,
    @SerializedName("business_id") val businessId: Int? = null,
    @SerializedName("budget_cents") val budgetCents: Int = 0,
    @SerializedName("spent_cents") val spentCents: Int = 0,
    @SerializedName("cost_per_like_cents") val costPerLikeCents: Int = 0,
    @SerializedName("is_active") val isActive: Boolean = true
)

data class FavoriteResponse(
    val id: Int,
    @SerializedName("plan_id") val planId: Int,
    @SerializedName("created_at") val createdAt: String,
    val plan: FavoritePlan
)

data class FavoriteActionResponse(
    val status: String
)

// === Disliked Tags ===

data class DislikeTagsRequest(
    val tags: List<String>
)

data class DislikeTagsResponse(
    val status: String,
    val tags: List<String>
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

data class DeletePlanResponse(
    val status: String,
    @SerializedName("refunded_cents") val refundedCents: Int = 0
)

// === Trip groups (BlaBlaCar-style) ===

data class TripGroupCreateRequest(
    @SerializedName("plan_id") val planId: Int,
    val title: String,
    @SerializedName("meeting_point") val meetingPoint: String? = null,
    @SerializedName("meet_at") val meetAt: String? = null,
    val seats: Int = 4,
    val transport: String = "COCHE",
    val notes: String? = null
)

data class TripGroupMember(
    @SerializedName("user_id") val userId: Int,
    val username: String,
    @SerializedName("joined_at") val joinedAt: String
)

data class TripGroupResponse(
    val id: Int,
    @SerializedName("plan_id") val planId: Int,
    @SerializedName("plan_title") val planTitle: String = "",
    @SerializedName("owner_id") val ownerId: Int,
    @SerializedName("owner_username") val ownerUsername: String = "",
    val title: String,
    @SerializedName("meeting_point") val meetingPoint: String? = null,
    @SerializedName("meet_at") val meetAt: String? = null,
    val seats: Int = 4,
    val transport: String = "COCHE",
    val notes: String? = null,
    @SerializedName("created_at") val createdAt: String,
    val members: List<TripGroupMember> = emptyList(),
    @SerializedName("seats_taken") val seatsTaken: Int = 0
)