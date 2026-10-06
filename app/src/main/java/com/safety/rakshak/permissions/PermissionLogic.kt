package com.safety.rakshak.permissions

/** What the UI should offer for a permission. */
enum class GrantState {
    GRANTED,

    /** Not granted, and the system dialog can still be shown. */
    CAN_ASK,

    /** Denied with "don't ask again" (or denied twice): only system Settings can change it. */
    NEEDS_SETTINGS,
}

/**
 * Android gives no direct "permanently denied" signal. After the app has asked at
 * least once, `shouldShowRequestPermissionRationale == false` for a denied
 * permission means the system will no longer show the dialog.
 * Before the first request it is also false, hence [askedBefore].
 */
fun grantState(granted: Boolean, askedBefore: Boolean, shouldShowRationale: Boolean): GrantState = when {
    granted -> GrantState.GRANTED
    askedBefore && !shouldShowRationale -> GrantState.NEEDS_SETTINGS
    else -> GrantState.CAN_ASK
}

enum class LocationPrecision { NONE, APPROXIMATE, PRECISE }

fun locationPrecision(fineGranted: Boolean, coarseGranted: Boolean): LocationPrecision = when {
    fineGranted -> LocationPrecision.PRECISE
    coarseGranted -> LocationPrecision.APPROXIMATE
    else -> LocationPrecision.NONE
}
