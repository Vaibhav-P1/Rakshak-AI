package com.safety.rakshak.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LocationHelper(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    suspend fun getCurrentLocation(): Location? = suspendCancellableCoroutine { continuation ->
        if (!hasLocationPermission()) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        try {
            // Step 1: Flush and get fresh GPS location immediately
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                1000L
            )
                .setMaxUpdates(1)
                .setMinUpdateIntervalMillis(0)      // No minimum interval — get it NOW
                .setMaxUpdateDelayMillis(0)         // No batching delay
                .setWaitForAccurateLocation(false)  // Don't wait for perfect accuracy
                .build()

            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    fusedLocationClient.removeLocationUpdates(this)
                    val location = result.lastLocation
                    if (continuation.isActive) continuation.resume(location)
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )

            // Step 2: Also try lastLocation in parallel as a fast fallback
            fusedLocationClient.lastLocation
                .addOnSuccessListener { lastLocation ->
                    if (lastLocation != null && continuation.isActive) {
                        // Got cached location — use it immediately, cancel the update request
                        fusedLocationClient.removeLocationUpdates(locationCallback)
                        continuation.resume(lastLocation)
                    }
                    // If null, the requestLocationUpdates callback above will handle it
                }

            continuation.invokeOnCancellation {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }

        } catch (e: SecurityException) {
            if (continuation.isActive) continuation.resume(null)
        } catch (e: Exception) {
            if (continuation.isActive) continuation.resume(null)
        }
    }

    fun getGoogleMapsUrl(latitude: Double, longitude: Double): String {
        return "https://maps.google.com/?q=$latitude,$longitude"
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}