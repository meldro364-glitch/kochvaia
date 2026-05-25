package com.kochvaia.app.data.remote

import com.squareup.moshi.JsonClass

// Wire DTOs mirror the backend response shapes exactly. We keep them as
// data classes (not domain models) so renames on either side surface as
// compile errors here.

@JsonClass(generateAdapter = true)
data class GoogleAuthRequest(
    val idToken: String,
    val inviteCode: String? = null,
    val familyTz: String? = null,
    val familyName: String? = null,
    val displayName: String? = null,
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    val sessionToken: String,
    val expiresAt: Long,
    val role: String,
    val parentId: String? = null,
    val kidId: String? = null,
    val familyId: String,
)

@JsonClass(generateAdapter = true)
data class QrCreateRequest(
    val kind: String,            // "parent" | "kid"
    val kidId: String? = null,
)

@JsonClass(generateAdapter = true)
data class QrCreateResponse(
    val code: String,
    val kind: String,
    val kidId: String? = null,
    val expiresAt: Long,
)

@JsonClass(generateAdapter = true)
data class QrRedeemRequest(
    val code: String,
    val deviceLabel: String? = null,
)

@JsonClass(generateAdapter = true)
data class QrRedeemResponse(
    val role: String,
    val sessionToken: String? = null,
    val expiresAt: Long? = null,
    val kidId: String? = null,
    val familyId: String? = null,
    val requiresGoogleSignIn: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class MeResponse(
    val role: String,
    val family: FamilyDto,
    val parent: ParentDto? = null,
    val kid: KidDto? = null,
)

@JsonClass(generateAdapter = true)
data class FamilyDto(val id: String, val name: String, val tz: String)

@JsonClass(generateAdapter = true)
data class ParentDto(
    val id: String,
    val email: String?,
    val display_name: String,
)

@JsonClass(generateAdapter = true)
data class KidDto(
    val id: String,
    val displayName: String,
    val avatarEmoji: String,
    val avatarColor: String,
    val createdAt: Long? = null,
)

@JsonClass(generateAdapter = true)
data class KidsListResponse(val kids: List<KidDto>)

@JsonClass(generateAdapter = true)
data class CreateKidRequest(
    val displayName: String,
    val avatarEmoji: String? = null,
    val avatarColor: String? = null,
)

@JsonClass(generateAdapter = true)
data class PatchKidRequest(
    val displayName: String? = null,
    val avatarEmoji: String? = null,
    val avatarColor: String? = null,
)

@JsonClass(generateAdapter = true)
data class AwardStarRequest(val date: String)

@JsonClass(generateAdapter = true)
data class AwardStarResponse(
    val id: String,
    val kidId: String,
    val date: String,
    val awardedAt: Long,
)

@JsonClass(generateAdapter = true)
data class DeductRequest(val count: Int, val reason: String? = null)

@JsonClass(generateAdapter = true)
data class DeductResponse(
    val id: String,
    val kidId: String,
    val count: Int,
    val reason: String?,
    val performedAt: Long,
)

@JsonClass(generateAdapter = true)
data class DayDto(val date: String, val status: String)  // "none" | "given" | "used"

@JsonClass(generateAdapter = true)
data class DaysResponse(
    val kidId: String,
    val from: String,
    val to: String,
    val days: List<DayDto>,
)

@JsonClass(generateAdapter = true)
data class SummaryResponse(
    val kidId: String,
    val availableStars: Int,
    val totalEarned: Int,
    val totalUsed: Int,
)

@JsonClass(generateAdapter = true)
data class SeenStar(val id: String, val date: String, val awardedAt: Long)

@JsonClass(generateAdapter = true)
data class SeenDeduction(
    val id: String,
    val count: Int,
    val performedAt: Long,
    val reason: String? = null,
)

@JsonClass(generateAdapter = true)
data class SeenResponse(
    val newStars: List<SeenStar>,
    val newDeductions: List<SeenDeduction>,
    val firstVisit: Boolean,
)

@JsonClass(generateAdapter = true)
data class ErrorBody(val error: String)
