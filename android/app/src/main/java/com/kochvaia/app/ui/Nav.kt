package com.kochvaia.app.ui

/**
 * Centralized route table for the NavHost. Keep route strings here so callers
 * don't drift apart from the destination declarations.
 */
object Routes {
    const val MODE_PICKER = "mode_picker"

    const val PARENT_SIGN_IN = "parent_sign_in"
    const val PARENT_DASHBOARD = "parent_dashboard"
    const val PARENT_KID_DETAIL = "parent_kid_detail/{kidId}"
    fun parentKidDetail(kidId: String) = "parent_kid_detail/$kidId"
    const val PARENT_SHARE_QR = "parent_share_qr?kidId={kidId}"
    fun parentShareQr(kidId: String? = null): String =
        if (kidId == null) "parent_share_qr?kidId=" else "parent_share_qr?kidId=$kidId"

    const val KID_PAIRING = "kid_pairing"
    const val KID_HOME = "kid_home"
    const val KID_SIBLING = "kid_sibling/{kidId}"
    fun kidSibling(kidId: String) = "kid_sibling/$kidId"

    const val JOIN_FROM_DEEP_LINK = "join?code={code}"
    fun joinFromDeepLink(code: String) = "join?code=$code"
}
