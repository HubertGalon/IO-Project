package com.groupswipe.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class LatLng(val lat: Double, val lng: Double) {
    fun toQueryString(): String = "$lat,$lng"
}

@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationPreferences: LocationPreferences
) {
    companion object {
        val DEFAULT_LOCATION = LatLng(lat = 52.2297, lng = 21.0122)
    }

    suspend fun getCurrentLocation(): LatLng {
        // Jeśli użytkownik wybrał konkretne miasto – używamy jego współrzędnych (bez GPS).
        locationPreferences.selectedCity()?.let { city ->
            return LatLng(city.lat, city.lng)
        }
        // Dodajemy timeout 5 sekund na całą operację lokalizacji
        return withTimeoutOrNull(5000) {
            val hasFineLocation = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            val hasCoarseLocation = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasFineLocation && !hasCoarseLocation) {
                return@withTimeoutOrNull DEFAULT_LOCATION
            }

            val fusedClient = LocationServices.getFusedLocationProviderClient(context)

            suspendCancellableCoroutine { continuation ->
                val cancellationSource = CancellationTokenSource()
                continuation.invokeOnCancellation { cancellationSource.cancel() }

                fusedClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cancellationSource.token
                ).addOnSuccessListener { location ->
                    if (location != null) {
                        continuation.resume(LatLng(location.latitude, location.longitude))
                    } else {
                        fusedClient.lastLocation.addOnSuccessListener { lastLocation ->
                            continuation.resume(
                                if (lastLocation != null) LatLng(lastLocation.latitude, lastLocation.longitude)
                                else DEFAULT_LOCATION
                            )
                        }.addOnFailureListener { continuation.resume(DEFAULT_LOCATION) }
                    }
                }.addOnFailureListener { continuation.resume(DEFAULT_LOCATION) }
            }
        } ?: DEFAULT_LOCATION
    }
}
