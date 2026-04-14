package uk.storitad.capture.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Instant
import uk.storitad.capture.metadata.GeoFix
import kotlin.coroutines.resume

class LocationProvider(private val context: Context) {

    sealed interface Outcome {
        data class Ok(val fix: GeoFix) : Outcome
        data object NoPermission : Outcome
        data object LocationOff : Outcome
        data object NoProvider : Outcome
        data object Timeout : Outcome
    }

    fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    suspend fun currentFix(timeoutMs: Long = 10_000L): Outcome {
        if (!hasPermission()) return Outcome.NoPermission
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return Outcome.NoProvider
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !lm.isLocationEnabled) {
            return Outcome.LocationOff
        }

        val providers = pickProviders(lm)
        if (providers.isEmpty()) return Outcome.NoProvider

        // 1) Try a fresh fix from each provider in order with the given timeout.
        for (provider in providers) {
            val fresh = withTimeoutOrNull(timeoutMs) { fetchCurrent(lm, provider) }
            if (fresh != null) return Outcome.Ok(fresh.toFix())
        }

        // 2) Fall back to last-known across providers.
        @Suppress("DEPRECATION")
        val cached = providers
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (cached != null) return Outcome.Ok(cached.toFix())

        return Outcome.Timeout
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchCurrent(lm: LocationManager, provider: String): Location? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                lm.getCurrentLocation(provider, signal, context.mainExecutor) { loc ->
                    cont.resume(loc)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            lm.getLastKnownLocation(provider)
        }
    }

    private fun pickProviders(lm: LocationManager): List<String> {
        val enabled = lm.getProviders(true)
        val preferred = listOfNotNull(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) LocationManager.FUSED_PROVIDER else null,
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        )
        return preferred.filter { it in enabled }
    }

    private fun Location.toFix(): GeoFix = GeoFix(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        capturedAt = Instant.fromEpochMilliseconds(time)
    )
}
