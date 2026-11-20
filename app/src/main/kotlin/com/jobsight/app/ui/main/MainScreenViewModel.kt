package com.jobsight.app.ui.main

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jobsight.app.data.AnalysisJsonParser
import com.jobsight.app.data.CapturedPhotoRow
import com.jobsight.app.data.Customer
import com.jobsight.app.data.CustomerLocation
import com.jobsight.app.data.CustomerMatch
import com.jobsight.app.data.DataRepository
import com.jobsight.app.data.HvacPartAnalysis
import com.jobsight.app.data.SearchSite
import com.jobsight.app.location.LocationTracker
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FieldCameraUiState(
  val isLoadingCustomers: Boolean = true,
  val customers: List<Customer> = emptyList(),
  val currentLocation: Location? = null,
  val closestMatch: CustomerMatch? = null,
  val manualCustomerOverrideId: Long? = null,
  val manualLocationOverrideId: Long? = null,
  val isUploadingPhoto: Boolean = false,
  val lastCapturedPhoto: CapturedPhotoRow? = null,
  val isAwaitingAiAnalysis: Boolean = false,
  val detectedParts: List<HvacPartAnalysis> = emptyList(),
  val isBottomSheetVisible: Boolean = false,
  val isCustomerPickerVisible: Boolean = false,
  val isCustomerHubVisible: Boolean = false,
  val selectedHubCustomerId: Long? = null,
  val customerPhotos: List<CapturedPhotoRow> = emptyList(),
  val isLoadingCustomerPhotos: Boolean = false,
  val photoBitmapsCache: Map<String, ByteArray> = emptyMap(),
  val searchSites: List<SearchSite> = SearchSite.DEFAULT_SITES,
  val isSearchSitesSettingsVisible: Boolean = false,
  val isSettingsSheetVisible: Boolean = false,
  val geofenceRadiusMeters: Float = CustomerMatch.GEOFENCE_RADIUS_METERS,
  val isSavingCustomerOrLocation: Boolean = false,
  val statusBannerMessage: String? = null,
  val errorMessage: String? = null,
) {
  val isClosestWithinGeofence: Boolean
    get() = closestMatch != null && closestMatch.distanceMeters < geofenceRadiusMeters

  val autoDetectedCustomer: Customer?
    get() = closestMatch?.takeIf { isClosestWithinGeofence }?.customer

  val autoDetectedLocation: CustomerLocation?
    get() = closestMatch?.takeIf { isClosestWithinGeofence }?.matchedLocation

  val manualSelectedCustomer: Customer?
    get() = manualCustomerOverrideId?.let { id -> customers.find { it.id == id } }

  val manualSelectedLocation: CustomerLocation?
    get() {
      val manualCust = manualSelectedCustomer ?: return null
      return manualLocationOverrideId?.let { locId ->
        manualCust.locations.find { it.id == locId }
      } ?: manualCust.locations.firstOrNull()
    }

  val isManualSelection: Boolean
    get() = manualSelectedCustomer != null

  /** The customer that will be tagged when the shutter is pressed (manual override or GPS auto-detect). */
  val activeCustomer: Customer?
    get() = manualSelectedCustomer ?: autoDetectedCustomer

  /** The specific site location that will be saved with the photo in `captured_photos.customer_location_id`. */
  val activeCustomerLocation: CustomerLocation?
    get() = if (isManualSelection) manualSelectedLocation else autoDetectedLocation

  val selectedHubCustomer: Customer?
    get() =
      selectedHubCustomerId?.let { id -> customers.find { it.id == id } }
        ?: activeCustomer
        ?: customers.firstOrNull()
}

class MainScreenViewModel(
  private val dataRepository: DataRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(FieldCameraUiState())
  val uiState: StateFlow<FieldCameraUiState> = _uiState.asStateFlow()

  private var aiAnalysisSubscriptionJob: Job? = null

  init {
    refreshCustomers()
    refreshSearchSites()
  }

  fun refreshSearchSites() {
    viewModelScope.launch {
      try {
        val sites = dataRepository.getSearchSites()
        _uiState.update { it.copy(searchSites = sites) }
      } catch (_: Exception) {
        // Keep default search sites on transient error
      }
    }
  }

  fun openSearchSitesSettings() {
    _uiState.update {
      it.copy(
        isSearchSitesSettingsVisible = true,
        isSettingsSheetVisible = true,
      )
    }
    refreshSearchSites()
  }

  fun closeSearchSitesSettings() {
    _uiState.update { it.copy(isSearchSitesSettingsVisible = false) }
  }

  fun openSettingsSheet() {
    _uiState.update { it.copy(isSettingsSheetVisible = true) }
    refreshSearchSites()
  }

  fun closeSettingsSheet() {
    _uiState.update {
      it.copy(
        isSettingsSheetVisible = false,
        isSearchSitesSettingsVisible = false,
      )
    }
  }

  fun setGeofenceRadiusMeters(radiusMeters: Float) {
    _uiState.update {
      it.copy(
        geofenceRadiusMeters = radiusMeters,
        statusBannerMessage = "Auto-detect GPS radius set to ${radiusMeters.toInt()}m",
      )
    }
  }

  fun addSearchSite(name: String, urlOrDomain: String) {
    val cleanName = name.trim()
    val cleanUrl = urlOrDomain.trim()
    if (cleanName.isEmpty() || cleanUrl.isEmpty()) return

    viewModelScope.launch {
      try {
        dataRepository.addSearchSite(cleanName, cleanUrl)
        val updatedSites = dataRepository.getSearchSites()
        _uiState.update {
          it.copy(
            searchSites = updatedSites,
            statusBannerMessage = "Added search site '$cleanName'",
          )
        }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(errorMessage = "Could not add search site: ${e.message}")
        }
      }
    }
  }

  fun deleteSearchSite(siteId: Long, siteName: String) {
    viewModelScope.launch {
      try {
        dataRepository.deleteSearchSite(siteId)
        val updatedSites = dataRepository.getSearchSites()
        _uiState.update {
          it.copy(
            searchSites = updatedSites,
            statusBannerMessage = "Removed search site '$siteName'",
          )
        }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(errorMessage = "Could not remove search site: ${e.message}")
        }
      }
    }
  }

  fun refreshCustomers() {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoadingCustomers = true, errorMessage = null) }
      var lastException: Exception? = null
      for (attempt in 1..3) {
        try {
          val customers = dataRepository.getCustomers()
          _uiState.update { state ->
            val updatedMatch =
              state.currentLocation?.let { loc ->
                LocationTracker.findClosestCustomer(loc, customers)
              }
            val hubCustId =
              state.selectedHubCustomerId
                ?: updatedMatch?.takeIf { it.isWithinGeofence }?.customer?.id
                ?: customers.firstOrNull()?.id
            state.copy(
              isLoadingCustomers = false,
              customers = customers,
              closestMatch = updatedMatch,
              selectedHubCustomerId = hubCustId,
              errorMessage = null,
            )
          }
          return@launch
        } catch (e: Exception) {
          lastException = e
          if (attempt < 3) {
            delay(1500L)
          }
        }
      }

      _uiState.update {
        it.copy(
          isLoadingCustomers = false,
          errorMessage = "Could not load customers: ${lastException?.message}",
        )
      }
    }
  }

  fun onLocationUpdated(location: Location) {
    _uiState.update { state ->
      val match = LocationTracker.findClosestCustomer(location, state.customers)
      state.copy(
        currentLocation = location,
        closestMatch = match,
      )
    }
  }

  // --- Manual vs Auto Customer & Location Selection ---

  fun openCustomerPicker() {
    _uiState.update { it.copy(isCustomerPickerVisible = true) }
  }

  fun closeCustomerPicker() {
    _uiState.update { it.copy(isCustomerPickerVisible = false) }
  }

  fun selectCustomerManually(customerId: Long?, locationId: Long? = null) {
    _uiState.update { state ->
      val chosenCustomer = customerId?.let { id -> state.customers.find { it.id == id } }
      val chosenLocation =
        if (chosenCustomer != null) {
          locationId?.let { locId -> chosenCustomer.locations.find { it.id == locId } }
            ?: chosenCustomer.locations.firstOrNull()
        } else {
          null
        }

      state.copy(
        manualCustomerOverrideId = customerId,
        manualLocationOverrideId = chosenLocation?.id,
        isCustomerPickerVisible = false,
        statusBannerMessage =
          if (chosenCustomer != null) {
            val locSuffix = chosenLocation?.let { " • ${it.label}" }.orEmpty()
            "Selected: ${chosenCustomer.name}$locSuffix"
          } else {
            "Resumed GPS Auto-Detect (< 100m geofence)"
          },
      )
    }
  }

  // --- Customer Hub (Add Customers, Add Locations, View Photos by Customer) ---

  fun openCustomerHub(initialCustomerId: Long? = null) {
    val targetId =
      initialCustomerId
        ?: _uiState.value.activeCustomer?.id
        ?: _uiState.value.customers.firstOrNull()?.id
    _uiState.update {
      it.copy(
        isCustomerHubVisible = true,
        selectedHubCustomerId = targetId,
      )
    }
    if (targetId != null) {
      loadPhotosForCustomer(targetId)
    }
  }

  fun closeCustomerHub() {
    _uiState.update { it.copy(isCustomerHubVisible = false) }
  }

  fun selectHubCustomer(customerId: Long) {
    _uiState.update { it.copy(selectedHubCustomerId = customerId) }
    loadPhotosForCustomer(customerId)
  }

  fun loadPhotosForCustomer(customerId: Long) {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoadingCustomerPhotos = true) }
      try {
        val photos = dataRepository.getPhotosForCustomer(customerId)
        _uiState.update {
          it.copy(
            customerPhotos = photos,
            isLoadingCustomerPhotos = false,
          )
        }
        for (photo in photos) {
          if (!_uiState.value.photoBitmapsCache.containsKey(photo.imageUrl)) {
            launch {
              val bytes = dataRepository.downloadImageBytes(photo.imageUrl)
              if (bytes != null) {
                _uiState.update { state ->
                  state.copy(photoBitmapsCache = state.photoBitmapsCache + (photo.imageUrl to bytes))
                }
              }
            }
          }
        }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            isLoadingCustomerPhotos = false,
            errorMessage = "Could not load customer photos: ${e.message}",
          )
        }
      }
    }
  }

  fun createNewCustomer(
    customerName: String,
    initialLocationLabel: String,
    useCurrentGps: Boolean,
    addressInput: String,
  ) {
    val cleanName = customerName.trim()
    if (cleanName.isEmpty()) return

    viewModelScope.launch {
      _uiState.update { it.copy(isSavingCustomerOrLocation = true, errorMessage = null) }
      try {
        val createdCustomer = dataRepository.createCustomer(cleanName)

        if (useCurrentGps) {
          val loc = _uiState.value.currentLocation
          if (loc != null) {
            dataRepository.addCustomerLocation(
              customerId = createdCustomer.id,
              label = initialLocationLabel.ifBlank { "Primary Site" },
              address =
                addressInput.ifBlank {
                  "GPS (${"%.5f".format(loc.latitude)}, ${"%.5f".format(loc.longitude)})"
                },
              latitude = loc.latitude,
              longitude = loc.longitude,
            )
          }
        } else if (addressInput.isNotBlank()) {
          val geocoded = dataRepository.geocodeAddress(addressInput)
          dataRepository.addCustomerLocation(
            customerId = createdCustomer.id,
            label = initialLocationLabel.ifBlank { "Primary Site" },
            address = geocoded.formattedAddress,
            latitude = geocoded.latitude,
            longitude = geocoded.longitude,
          )
        }

        val refreshedCustomers = dataRepository.getCustomers()
        val createdWithLocations =
          refreshedCustomers.find { it.id == createdCustomer.id } ?: createdCustomer
        _uiState.update { state ->
          val updatedMatch =
            state.currentLocation?.let { loc ->
              LocationTracker.findClosestCustomer(loc, refreshedCustomers)
            }
          val isAutoDetectedNow =
            updatedMatch != null &&
              updatedMatch.distanceMeters < state.geofenceRadiusMeters &&
              updatedMatch.customer.id == createdCustomer.id

          state.copy(
            isSavingCustomerOrLocation = false,
            customers = refreshedCustomers,
            closestMatch = updatedMatch,
            manualCustomerOverrideId =
              if (isAutoDetectedNow) null else createdCustomer.id,
            manualLocationOverrideId =
              if (isAutoDetectedNow) null else createdWithLocations.locations.firstOrNull()?.id,
            selectedHubCustomerId = createdCustomer.id,
            statusBannerMessage = "Added & selected customer '${createdCustomer.name}'",
          )
        }
        loadPhotosForCustomer(createdCustomer.id)
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            isSavingCustomerOrLocation = false,
            errorMessage = "Could not add customer: ${e.message}",
          )
        }
      }
    }
  }

  fun addLocationToCustomer(
    customerId: Long,
    label: String,
    useCurrentGps: Boolean,
    addressInput: String,
  ) {
    viewModelScope.launch {
      _uiState.update { it.copy(isSavingCustomerOrLocation = true, errorMessage = null) }
      try {
        val cleanLabel = label.trim().ifEmpty { "Job Site" }
        val addedLocation =
          if (useCurrentGps) {
            val loc =
              _uiState.value.currentLocation
                ?: throw IllegalStateException("Current GPS location is not available yet")
            val addrText =
              addressInput.trim().ifEmpty {
                "GPS (${"%.5f".format(loc.latitude)}, ${"%.5f".format(loc.longitude)})"
              }
            dataRepository.addCustomerLocation(
              customerId = customerId,
              label = cleanLabel,
              address = addrText,
              latitude = loc.latitude,
              longitude = loc.longitude,
            )
          } else {
            val geocoded = dataRepository.geocodeAddress(addressInput)
            dataRepository.addCustomerLocation(
              customerId = customerId,
              label = cleanLabel,
              address = geocoded.formattedAddress,
              latitude = geocoded.latitude,
              longitude = geocoded.longitude,
            )
          }

        val refreshedCustomers = dataRepository.getCustomers()
        _uiState.update { state ->
          val updatedMatch =
            state.currentLocation?.let { loc ->
              LocationTracker.findClosestCustomer(loc, refreshedCustomers)
            }
          state.copy(
            isSavingCustomerOrLocation = false,
            customers = refreshedCustomers,
            closestMatch = updatedMatch,
            statusBannerMessage =
              "Added location '${addedLocation.label}' (${addedLocation.address})",
          )
        }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            isSavingCustomerOrLocation = false,
            errorMessage = "Could not add location: ${e.message}",
          )
        }
      }
    }
  }

  fun deleteCustomer(customerId: Long) {
    val customerName =
      _uiState.value.customers.find { it.id == customerId }?.name ?: "Customer"

    viewModelScope.launch {
      _uiState.update { it.copy(isSavingCustomerOrLocation = true, errorMessage = null) }
      try {
        dataRepository.deleteCustomer(customerId)
        val refreshedCustomers = dataRepository.getCustomers()
        val nextHubCustomerId = refreshedCustomers.firstOrNull()?.id

        _uiState.update { state ->
          val updatedMatch =
            state.currentLocation?.let { loc ->
              LocationTracker.findClosestCustomer(loc, refreshedCustomers)
            }
          val clearedManualCustomer =
            state.manualCustomerOverrideId?.takeIf { it != customerId }
          val clearedManualLocation =
            if (clearedManualCustomer == null) null else state.manualLocationOverrideId

          state.copy(
            isSavingCustomerOrLocation = false,
            customers = refreshedCustomers,
            closestMatch = updatedMatch,
            manualCustomerOverrideId = clearedManualCustomer,
            manualLocationOverrideId = clearedManualLocation,
            selectedHubCustomerId = nextHubCustomerId,
            customerPhotos = if (nextHubCustomerId == null) emptyList() else state.customerPhotos,
            statusBannerMessage = "Removed customer '$customerName' and all its locations",
          )
        }

        if (nextHubCustomerId != null) {
          loadPhotosForCustomer(nextHubCustomerId)
        }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            isSavingCustomerOrLocation = false,
            errorMessage = "Could not remove customer: ${e.message}",
          )
        }
      }
    }
  }

  fun deleteLocationFromCustomer(locationId: Long, locationLabel: String) {
    viewModelScope.launch {
      _uiState.update { it.copy(isSavingCustomerOrLocation = true, errorMessage = null) }
      try {
        dataRepository.deleteCustomerLocation(locationId)
        val refreshedCustomers = dataRepository.getCustomers()

        _uiState.update { state ->
          val updatedMatch =
            state.currentLocation?.let { loc ->
              LocationTracker.findClosestCustomer(loc, refreshedCustomers)
            }
          val clearedManualLocation =
            state.manualLocationOverrideId?.takeIf { it != locationId }

          state.copy(
            isSavingCustomerOrLocation = false,
            customers = refreshedCustomers,
            closestMatch = updatedMatch,
            manualLocationOverrideId = clearedManualLocation,
            statusBannerMessage = "Removed location '$locationLabel'",
          )
        }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            isSavingCustomerOrLocation = false,
            errorMessage = "Could not remove location: ${e.message}",
          )
        }
      }
    }
  }

  // --- Camera Capture & Upload (saves both customer_id and customer_location_id) ---

  fun onPhotoCapturedToCache(cacheFile: File) {
    val activeCust = _uiState.value.activeCustomer
    val activeLoc = _uiState.value.activeCustomerLocation
    val taggedCustomerName = activeCust?.name ?: "Untagged Site"
    val taggedLocationLabel = activeLoc?.label?.let { " ($it)" }.orEmpty()

    viewModelScope.launch {
      _uiState.update {
        it.copy(
          isUploadingPhoto = true,
          errorMessage = null,
          statusBannerMessage =
            "Uploading photo for $taggedCustomerName$taggedLocationLabel & flushing cache...",
        )
      }

      try {
        val insertedRow =
          dataRepository.uploadPhotoAndFlushCache(
            cacheFile = cacheFile,
            customerId = activeCust?.id,
            customerLocationId = activeLoc?.id?.takeIf { it > 0 },
          )

        _uiState.update {
          it.copy(
            isUploadingPhoto = false,
            lastCapturedPhoto = insertedRow,
            isAwaitingAiAnalysis = true,
            detectedParts = emptyList(),
            statusBannerMessage =
              "Saved Photo #${insertedRow.id} to $taggedCustomerName$taggedLocationLabel. Waiting for AI...",
          )
        }

        if (activeCust != null && _uiState.value.selectedHubCustomerId == activeCust.id) {
          loadPhotosForCustomer(activeCust.id)
        }

        subscribeToPhotoAiAnalysis(insertedRow.id)
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            isUploadingPhoto = false,
            errorMessage = "Upload failed: ${e.message}",
            statusBannerMessage = null,
          )
        }
      }
    }
  }

  private fun subscribeToPhotoAiAnalysis(photoId: Long) {
    aiAnalysisSubscriptionJob?.cancel()
    aiAnalysisSubscriptionJob =
      viewModelScope.launch {
        dataRepository
          .observeAiAnalysis(photoId)
          .catch { e ->
            _uiState.update {
              it.copy(errorMessage = "Realtime listener warning: ${e.message}")
            }
          }
          .collect { parts ->
            if (parts.isNotEmpty()) {
              _uiState.update {
                it.copy(
                  isAwaitingAiAnalysis = false,
                  detectedParts = parts,
                  isBottomSheetVisible = true,
                  statusBannerMessage = "AI Vision identified ${parts.size} HVAC component(s)",
                )
              }
            }
          }
      }
  }

  fun triggerDemoAiVisionUpdate(targetPhotoId: Long? = null) {
    val photoId = targetPhotoId ?: _uiState.value.lastCapturedPhoto?.id ?: return
    viewModelScope.launch {
      try {
        val parts = dataRepository.populateDemoAiAnalysis(photoId)
        _uiState.update {
          it.copy(
            isAwaitingAiAnalysis = false,
            detectedParts = parts,
            isBottomSheetVisible = true,
            statusBannerMessage = "AI Vision identified ${parts.size} HVAC component(s)",
          )
        }
        _uiState.value.selectedHubCustomer?.id?.let { loadPhotosForCustomer(it) }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(errorMessage = "Could not populate demo AI result: ${e.message}")
        }
      }
    }
  }

  fun showPartsForPhoto(photo: CapturedPhotoRow) {
    val parts = AnalysisJsonParser.parseParts(photo.aiAnalysisJson)
    if (parts.isNotEmpty()) {
      _uiState.update {
        it.copy(
          detectedParts = parts,
          isBottomSheetVisible = true,
        )
      }
    }
  }

  fun dismissBottomSheet() {
    _uiState.update { it.copy(isBottomSheetVisible = false) }
  }

  fun reopenBottomSheet() {
    if (_uiState.value.detectedParts.isNotEmpty()) {
      _uiState.update { it.copy(isBottomSheetVisible = true) }
    }
  }

  fun clearError() {
    _uiState.update { it.copy(errorMessage = null) }
    if (_uiState.value.customers.isEmpty()) {
      refreshCustomers()
    }
  }
}
