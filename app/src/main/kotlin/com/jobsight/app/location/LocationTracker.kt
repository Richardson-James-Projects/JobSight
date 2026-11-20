package com.jobsight.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.jobsight.app.data.Customer
import com.jobsight.app.data.CustomerLocation
import com.jobsight.app.data.CustomerMatch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class LocationTracker(context: Context) {
  private val fusedClient: FusedLocationProviderClient =
    LocationServices.getFusedLocationProviderClient(context.applicationContext)

  @SuppressLint("MissingPermission")
  suspend fun getInitialLocation(): Location? {
    return try {
      val tokenSource = CancellationTokenSource()
      fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token).await()
        ?: fusedClient.lastLocation.await()
    } catch (_: Exception) {
      null
    }
  }

  @SuppressLint("MissingPermission")
  fun observeLocationUpdates(intervalMs: Long = 4000L): Flow<Location> = callbackFlow {
    val request =
      LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
        .setMinUpdateDistanceMeters(2f)
        .setWaitForAccurateLocation(false)
        .build()

    val callback =
      object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
          result.lastLocation?.let { trySend(it) }
        }
      }

    fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())

    awaitClose { fusedClient.removeLocationUpdates(callback) }
  }

  companion object {
    /**
     * Uses Android's Location.distanceTo() against every location of every customer.
     * Returns the closest customer + matched location along with the distance in meters.
     */
    fun findClosestCustomer(
      deviceLocation: Location,
      customers: List<Customer>,
    ): CustomerMatch? {
      if (customers.isEmpty()) return null

      val candidates = mutableListOf<CustomerMatch>()
      for (customer in customers) {
        if (customer.locations.isNotEmpty()) {
          for (loc in customer.locations) {
            val dist = distanceBetween(deviceLocation, loc.latitude, loc.longitude)
            candidates.add(
              CustomerMatch(
                customer = customer,
                matchedLocation = loc,
                distanceMeters = dist,
              )
            )
          }
        } else if (customer.latitude != 0.0 || customer.longitude != 0.0) {
          val dist = distanceBetween(deviceLocation, customer.latitude, customer.longitude)
          val fallbackLoc =
            CustomerLocation(
              id = -customer.id,
              customerId = customer.id,
              label = "Primary Site",
              address = customer.address,
              latitude = customer.latitude,
              longitude = customer.longitude,
            )
          candidates.add(
            CustomerMatch(
              customer = customer,
              matchedLocation = fallbackLoc,
              distanceMeters = dist,
            )
          )
        }
      }

      return candidates.minByOrNull { it.distanceMeters }
    }

    fun distanceBetween(
      deviceLocation: Location,
      targetLatitude: Double,
      targetLongitude: Double,
    ): Float {
      val target =
        Location("customer_db").apply {
          latitude = targetLatitude
          longitude = targetLongitude
        }
      return deviceLocation.distanceTo(target)
    }
  }
}
