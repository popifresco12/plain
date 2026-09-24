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
    // 0.9.0: sesión renovable. Null si el backend es anterior (entonces el access dura 30 días)
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("expires_in") val expiresIn: Int? = null,
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
    @SerializedName("image_url") val imageUrl: String? = null,
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
    @SerializedName("is_available_now") val isAvailableNow: Boolean = true,
    /** km hasta la ciudad elegida (solo si se pidió con radio) */
    @SerializedName("distance_km") val distanceKm: Double? = null,
    /** «Para ti»: por qué se recomienda (null = sin historial suficiente) */
    val reason: String? = null
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
    @SerializedName("image_url") val imageUrl: String? = null,
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

data class CheckoutSessionResponse(
    val url: String,
    @SerializedName("session_id") val sessionId: String = ""
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
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("available_from") val availableFrom: String? = null,
    @SerializedName("available_until") val availableUntil: String? = null,
    val recurring: String? = null,
    @SerializedName("is_available_now") val isAvailableNow: Boolean = true,
    @SerializedName("distance_km") val distanceKm: Double? = null
)

data class FavoriteResponse(
    val id: Int,
    @SerializedName("plan_id") val planId: Int,
    @SerializedName("created_at") val createdAt: String,
    val plan: FavoritePlan
)

data class FavoriteActionResponse(
    val status: String,
    /** 0.9.0: cuántas personas más han marcado este plan (match) */
    val matches: Int = 0
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
    @SerializedName("seats_taken") val seatsTaken: Int = 0,
    @SerializedName("join_mode") val joinMode: String = "open",
    @SerializedName("my_status") val myStatus: String? = null
)
// --- Chat de quedadas ---

data class GroupMessageRequest(
    val text: String
)

data class GroupMessageResponse(
    val id: Int,
    @SerializedName("group_id") val groupId: Int,
    @SerializedName("user_id") val userId: Int,
    val username: String,
    val text: String,
    @SerializedName("created_at") val createdAt: String
)

data class BootstrapResult(
    val city: String,
    val created: Int = 0
)

// === Fotos de planes (búsqueda online, sin subida de ficheros) ===

data class ImageSearchResult(
    val title: String,
    val url: String,
    val thumb: String = "",
    val license: String = "",
    val attribution: String = ""
)

// === Perfil ===

data class ProfileUpdateRequest(
    val email: String? = null,
    val username: String? = null
)

data class ProfileResponse(
    val id: Int,
    val username: String,
    val email: String,
    @SerializedName("email_verified") val emailVerified: Boolean = false
)

data class MeStatus(
    @SerializedName("email_verified") val emailVerified: Boolean = false,
    @SerializedName("requires_verification") val requiresVerification: Boolean = false,
    @SerializedName("max_plans") val maxPlans: Int = 25
)

// === Recuperar contraseña ===

data class ForgotPasswordRequest(val email: String)

data class ForgotPasswordResponse(
    val status: String = "",
    val sent: Boolean = false,
    @SerializedName("dev_code") val devCode: String? = null,
    val warning: String? = null
)

data class ResetPasswordRequest(
    val email: String,
    val code: String,
    @SerializedName("new_password") val newPassword: String
)

// === Moderación ===

data class PlanReportRequest(
    val reason: String = "otro",
    val comment: String? = null
)

// === Analítica de producto ===

data class EventItem(
    @SerializedName("plan_id") val planId: Int? = null,
    val event: String,
    val city: String? = null
)

data class EventsRequest(val events: List<EventItem>)


// ===== 0.9.0: avisos, match y sesión =====

data class NotificationItem(
    val id: Int,
    val kind: String,
    val title: String,
    val body: String = "",
    val data: Map<String, Any?> = emptyMap(),
    @SerializedName("collapse_key") val collapseKey: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    val read: Boolean = false
) {
    /** Gson convierte los números de un Map a Double: se normalizan aquí. */
    fun intData(key: String): Int? = when (val v = data[key]) {
        is Number -> v.toInt()
        is String -> v.toIntOrNull()
        else -> null
    }
    fun strData(key: String): String? = data[key]?.toString()
}

data class NotificationsResponse(
    val items: List<NotificationItem> = emptyList(),
    val unread: Int = 0
)

data class MarkReadRequest(
    val ids: List<Int> = emptyList(),
    val all: Boolean = false,
    @SerializedName("collapse_key") val collapseKey: String? = null
)

data class MarkReadResponse(val updated: Int = 0, val unread: Int = 0)

data class MatchPerson(
    @SerializedName("user_id") val userId: Int,
    val username: String
)

data class MatchGroup(
    val id: Int,
    val title: String,
    val seats: Int = 4,
    @SerializedName("seats_taken") val seatsTaken: Int = 0,
    @SerializedName("join_mode") val joinMode: String = "open",
    @SerializedName("i_am_member") val iAmMember: Boolean = false
)

data class MatchItem(
    @SerializedName("plan_id") val planId: Int,
    val title: String,
    val city: String = "",
    val emoji: String = "📍",
    @SerializedName("image_url") val imageUrl: String? = null,
    val category: String = "",
    val people: List<MatchPerson> = emptyList(),
    @SerializedName("people_count") val peopleCount: Int = 0,
    val groups: List<MatchGroup> = emptyList()
)

data class MatchesResponse(val items: List<MatchItem> = emptyList())

data class RefreshRequest(@SerializedName("refresh_token") val refreshToken: String)

data class RefreshResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("expires_in") val expiresIn: Int? = null
)

data class LogoutRequest(@SerializedName("refresh_token") val refreshToken: String?)
