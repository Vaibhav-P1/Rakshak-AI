package com.safety.rakshak.sos.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import com.safety.rakshak.sos.GeoFix
import com.safety.rakshak.sos.LocationAccess
import com.safety.rakshak.sos.LocationSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** [LocationSource] backed by FusedLocationProviderClient. Accepts approximate-only permission. */
class FusedLocationSource(private val context: Context) : LocationSource {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    override fun access(): LocationAccess = when {
        granted(Manifest.permission.ACCESS_FINE_LOCATION) -> LocationAccess.PRECISE
        granted(Manifest.permission.ACCESS_COARSE_LOCATION) -> LocationAccess.APPROXIMATE
        else -> LocationAccess.NONE
    }

    // Permission is checked through access() just before each call.
    @SuppressLint("MissingPermission")
    override suspend fun lastKnown(): GeoFix? {
        if (access() == LocationAccess.NONE) return null
        return try {
            client.lastLocation.awaitOrNull(cancellation = null)?.toGeoFix()
        } catch (e: SecurityException) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun current(timeoutMillis: Long): GeoFix? {
        val access = access()
        if (access == LocationAccess.NONE || !isLocationEnabled()) return null

        val request = CurrentLocationRequest.Builder()
            .setPriority(
                if (access == LocationAccess.PRECISE) Priority.PRIORITY_HIGH_ACCURACY
                else Priority.PRIORITY_BALANCED_POWER_ACCURACY
            )
            .setDurationMillis(timeoutMillis)
            .setMaxUpdateAgeMillis(MAX_REUSED_FIX_AGE_MS)
            .build()
        val cancellation = CancellationTokenSource()
        return try {
            client.getCurrentLocation(request, cancellation.token)
                .awaitOrNull(cancellation)
                ?.toGeoFix()
        } catch (e: SecurityException) {
            null
        }
    }

    private fun isLocationEnabled(): Boolean {
        val manager = context.getSystemService(LocationManager::class.java) ?: return false
        return LocationManagerCompat.isLocationEnabled(manager)
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun Location.toGeoFix(): GeoFix {
        val ageNanos = SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos
        return GeoFix(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = if (hasAccuracy()) accuracy else null,
            fixTimeMillis = time,
            ageMillis = (ageNanos / 1_000_000L).coerceAtLeast(0L),
        )
    }

    private suspend fun <T> Task<T>.awaitOrNull(cancellation: CancellationTokenSource?): T? =
        suspendCancellableCoroutine { continuation ->
            addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "Location request failed: ${task.exception?.javaClass?.simpleName}")
                }
                continuation.resume(if (task.isSuccessful) task.result else null)
            }
            continuation.invokeOnCancellation { cancellation?.cancel() }
        }

    companion object {
        private const val TAG = "FusedLocationSource"

        /** The fused provider may answer with a cached fix no older than this. */
        private const val MAX_REUSED_FIX_AGE_MS = 30_000L
    }
}
