package com.jobsight.app.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.jobsight.app.data.AnalysisJsonParser
import com.jobsight.app.data.CapturedPhotoRow
import com.jobsight.app.data.Customer
import com.jobsight.app.data.DefaultDataRepository
import com.jobsight.app.data.HvacPartAnalysis
import com.jobsight.app.data.SearchSite
import com.jobsight.app.location.LocationTracker
import java.io.File
import kotlin.math.roundToInt

@Composable
fun MainScreen(
  onItemClick: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(DefaultDataRepository()) },
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  var hasLocationPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
          PackageManager.PERMISSION_GRANTED
    )
  }

  val permissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
      hasCameraPermission = result[Manifest.permission.CAMERA] == true || hasCameraPermission
      hasLocationPermission =
        result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
          result[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
          hasLocationPermission
    }

  LaunchedEffect(Unit) {
    if (!hasCameraPermission || !hasLocationPermission) {
      permissionLauncher.launch(
        arrayOf(
          Manifest.permission.CAMERA,
          Manifest.permission.ACCESS_FINE_LOCATION,
          Manifest.permission.ACCESS_COARSE_LOCATION,
        )
      )
    }
  }

  LaunchedEffect(hasLocationPermission) {
    if (hasLocationPermission) {
      val tracker = LocationTracker(context)
      tracker.getInitialLocation()?.let { viewModel.onLocationUpdated(it) }
      tracker.observeLocationUpdates().collect { location ->
        viewModel.onLocationUpdated(location)
      }
    }
  }

  FieldCameraContent(
    uiState = uiState,
    hasCameraPermission = hasCameraPermission,
    hasLocationPermission = hasLocationPermission,
    onRequestPermissions = {
      permissionLauncher.launch(
        arrayOf(
          Manifest.permission.CAMERA,
          Manifest.permission.ACCESS_FINE_LOCATION,
          Manifest.permission.ACCESS_COARSE_LOCATION,
        )
      )
    },
    onPhotoCapturedToCache = viewModel::onPhotoCapturedToCache,
    onOpenCustomerPicker = viewModel::openCustomerPicker,
    onCloseCustomerPicker = viewModel::closeCustomerPicker,
    onSelectCustomerManually = viewModel::selectCustomerManually,
    onOpenCustomerHub = viewModel::openCustomerHub,
    onCloseCustomerHub = viewModel::closeCustomerHub,
    onSelectHubCustomer = viewModel::selectHubCustomer,
    onCreateCustomer = viewModel::createNewCustomer,
    onDeleteCustomer = viewModel::deleteCustomer,
    onAddLocationToCustomer = viewModel::addLocationToCustomer,
    onDeleteLocationFromCustomer = viewModel::deleteLocationFromCustomer,
    onOpenSearchSitesSettings = viewModel::openSearchSitesSettings,
    onCloseSearchSitesSettings = viewModel::closeSearchSitesSettings,
    onAddSearchSite = viewModel::addSearchSite,
    onDeleteSearchSite = viewModel::deleteSearchSite,
    onTriggerDemoAi = viewModel::triggerDemoAiVisionUpdate,
    onShowPartsForPhoto = viewModel::showPartsForPhoto,
    onDismissBottomSheet = viewModel::dismissBottomSheet,
    onReopenBottomSheet = viewModel::reopenBottomSheet,
    onDismissError = viewModel::clearError,
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FieldCameraContent(
  uiState: FieldCameraUiState,
  hasCameraPermission: Boolean,
  hasLocationPermission: Boolean,
  onRequestPermissions: () -> Unit,
  onPhotoCapturedToCache: (File) -> Unit,
  onOpenCustomerPicker: () -> Unit,
  onCloseCustomerPicker: () -> Unit,
  onSelectCustomerManually: (Long?, Long?) -> Unit,
  onOpenCustomerHub: (Long?) -> Unit,
  onCloseCustomerHub: () -> Unit,
  onSelectHubCustomer: (Long) -> Unit,
  onCreateCustomer: (String, String, Boolean, String) -> Unit,
  onDeleteCustomer: (Long) -> Unit,
  onAddLocationToCustomer: (Long, String, Boolean, String) -> Unit,
  onDeleteLocationFromCustomer: (Long, String) -> Unit,
  onOpenSearchSitesSettings: () -> Unit,
  onCloseSearchSitesSettings: () -> Unit,
  onAddSearchSite: (String, String) -> Unit,
  onDeleteSearchSite: (Long, String) -> Unit,
  onTriggerDemoAi: (Long?) -> Unit,
  onShowPartsForPhoto: (CapturedPhotoRow) -> Unit,
  onDismissBottomSheet: () -> Unit,
  onReopenBottomSheet: () -> Unit,
  onDismissError: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val imageCapture = remember {
    ImageCapture.Builder()
      .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
      .build()
  }
  var showDirectAddCustomerDialog by remember { mutableStateOf(false) }

  Box(modifier = modifier.fillMaxSize().background(Color(0xFF0B0F14))) {
    // 1. Full-screen distortion-free CameraX PreviewView
    if (hasCameraPermission) {
      CameraXPreview(
        imageCapture = imageCapture,
        modifier = Modifier.fillMaxSize(),
      )
    } else {
      PermissionPromptOverlay(
        hasCameraPermission = hasCameraPermission,
        hasLocationPermission = hasLocationPermission,
        onRequestPermissions = onRequestPermissions,
        modifier = Modifier.align(Alignment.Center),
      )
    }

    // 2. Top Geofence & Customer Control HUD Overlay
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      GeofenceHeaderBadge(
        uiState = uiState,
        hasLocationPermission = hasLocationPermission,
        onOpenCustomerPicker = onOpenCustomerPicker,
        onAddCustomerClick = { showDirectAddCustomerDialog = true },
        onOpenCustomerHub = { onOpenCustomerHub(uiState.activeCustomer?.id) },
        onResetToAutoGps = { onSelectCustomerManually(null, null) },
      )

      // Status / AI Waiting Pill
      AnimatedVisibility(visible = uiState.statusBannerMessage != null) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = Color(0xCC111827),
          tonalElevation = 4.dp,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              if (uiState.isUploadingPhoto || uiState.isAwaitingAiAnalysis) {
                CircularProgressIndicator(
                  modifier = Modifier.size(18.dp),
                  strokeWidth = 2.dp,
                  color = Color(0xFF38BDF8),
                )
              }
              Text(
                text = uiState.statusBannerMessage.orEmpty(),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
              )
              if (uiState.detectedParts.isNotEmpty() && !uiState.isBottomSheetVisible) {
                TextButton(onClick = onReopenBottomSheet) {
                  Text("View Parts", color = Color(0xFF38BDF8))
                }
              }
            }

            if (uiState.isAwaitingAiAnalysis) {
              Spacer(modifier = Modifier.height(6.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "Listening for Supabase ai_analysis_json update...",
                  color = Color(0xFF9CA3AF),
                  style = MaterialTheme.typography.labelSmall,
                )
                FilledTonalButton(
                  onClick = { onTriggerDemoAi(null) },
                  modifier = Modifier.height(30.dp),
                ) {
                  Text(
                    text = "Simulate AI Result",
                    style = MaterialTheme.typography.labelSmall,
                  )
                }
              }
            }
          }
        }
      }

      // Error Pill
      AnimatedVisibility(visible = uiState.errorMessage != null) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = Color(0xE67F1D1D),
          modifier = Modifier.fillMaxWidth().clickable { onDismissError() },
        ) {
          Text(
            text = "${uiState.errorMessage.orEmpty()} (Tap to dismiss)",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
          )
        }
      }
    }

    // 3. Bottom Zero-Click Shutter Bar (shows Customer + Site Location being tagged)
    Column(
      modifier =
        Modifier.align(Alignment.BottomCenter)
          .fillMaxWidth()
          .navigationBarsPadding()
          .padding(bottom = 28.dp, start = 24.dp, end = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      ShutterButton(
        enabled = hasCameraPermission && !uiState.isUploadingPhoto,
        isUploading = uiState.isUploadingPhoto,
        onClick = {
          capturePhotoToCache(
            context = context,
            imageCapture = imageCapture,
            onCaptured = onPhotoCapturedToCache,
          )
        },
      )
      Spacer(modifier = Modifier.height(8.dp))
      val activeCustomer = uiState.activeCustomer
      val activeLocation = uiState.activeCustomerLocation
      val locSuffix = activeLocation?.label?.let { " • $it" }.orEmpty()
      Text(
        text =
          when {
            activeCustomer != null && uiState.isManualSelection ->
              "Tagging (Manual): ${activeCustomer.name}$locSuffix"
            activeCustomer != null ->
              "Auto-tagging: ${activeCustomer.name}$locSuffix"
            else ->
              "Untagged Capture • Tap 'Select Customer' above to tag manually"
          },
        color = Color.White.copy(alpha = 0.9f),
        style = MaterialTheme.typography.labelMedium,
      )
    }
  }

  if (showDirectAddCustomerDialog) {
    AddCustomerDialog(
      hasCurrentGps = uiState.currentLocation != null,
      isSaving = uiState.isSavingCustomerOrLocation,
      onConfirm = { name, locLabel, useGps, address ->
        showDirectAddCustomerDialog = false
        onCreateCustomer(name, locLabel, useGps, address)
      },
      onDismiss = { showDirectAddCustomerDialog = false },
    )
  }

  // 4. Customer & Location Switcher Modal (Auto-Detect vs Manual Customer + Location Override)
  if (uiState.isCustomerPickerVisible) {
    CustomerPickerDialog(
      uiState = uiState,
      onSelectCustomerAndLocation = onSelectCustomerManually,
      onManageCustomersClick = {
        onCloseCustomerPicker()
        onOpenCustomerHub(uiState.activeCustomer?.id)
      },
      onDismiss = onCloseCustomerPicker,
    )
  }

  // 5. Full Customer Hub Sheet (Add Customers, Add Locations by Address/GPS, View Customer Photos)
  if (uiState.isCustomerHubVisible) {
    val hubSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
      onDismissRequest = onCloseCustomerHub,
      sheetState = hubSheetState,
      containerColor = Color(0xFF0F172A),
      contentColor = Color.White,
    ) {
      CustomerHubBottomSheet(
        uiState = uiState,
        onSelectHubCustomer = onSelectHubCustomer,
        onSetAsActiveCustomerAndLocation = { customerId, locationId ->
          onSelectCustomerManually(customerId, locationId)
          onCloseCustomerHub()
        },
        onCreateCustomer = onCreateCustomer,
        onDeleteCustomer = onDeleteCustomer,
        onAddLocationToCustomer = onAddLocationToCustomer,
        onDeleteLocationFromCustomer = onDeleteLocationFromCustomer,
        onOpenSearchSitesSettings = onOpenSearchSitesSettings,
        onTriggerDemoAi = onTriggerDemoAi,
        onShowPartsForPhoto = onShowPartsForPhoto,
        onClose = onCloseCustomerHub,
      )
    }
  }

  // 6. AI Part Identification Bottom Sheet Card
  if (uiState.isBottomSheetVisible && uiState.detectedParts.isNotEmpty()) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
      onDismissRequest = onDismissBottomSheet,
      sheetState = sheetState,
      containerColor = Color(0xFF111827),
      contentColor = Color.White,
    ) {
      val locLabel = uiState.activeCustomerLocation?.label?.let { " ($it)" }.orEmpty()
      AiPartAnalysisBottomSheet(
        customerName = (uiState.activeCustomer?.name ?: "Job Site Capture") + locLabel,
        parts = uiState.detectedParts,
        searchSites = uiState.searchSites,
        onOpenSearchSitesSettings = onOpenSearchSitesSettings,
        onSearchPartOnSite = { site, query ->
          val fullUrl = site.buildSearchUrl(Uri.encode(query))
          val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl))
          context.startActivity(intent)
        },
        onClose = onDismissBottomSheet,
      )
    }
  }

  // 7. Search Sites Settings Dialog
  if (uiState.isSearchSitesSettingsVisible) {
    SearchSitesSettingsDialog(
      searchSites = uiState.searchSites,
      onAddSite = onAddSearchSite,
      onDeleteSite = onDeleteSearchSite,
      onDismiss = onCloseSearchSitesSettings,
    )
  }
}

@Composable
private fun GeofenceHeaderBadge(
  uiState: FieldCameraUiState,
  hasLocationPermission: Boolean,
  onOpenCustomerPicker: () -> Unit,
  onAddCustomerClick: () -> Unit,
  onOpenCustomerHub: () -> Unit,
  onResetToAutoGps: () -> Unit,
) {
  val manualCustomer = uiState.manualSelectedCustomer
  val manualLocation = uiState.manualSelectedLocation
  val autoDetected = uiState.autoDetectedCustomer
  val closest = uiState.closestMatch

  val badgeBackground =
    when {
      manualCustomer != null -> Color(0xD91E3A8A)
      autoDetected != null -> Color(0xD9065F46)
      else -> Color(0xCC1F2937)
    }
  val borderColor =
    when {
      manualCustomer != null -> Color(0xFF60A5FA)
      autoDetected != null -> Color(0xFF34D399)
      else -> Color(0xFF4B5563)
    }

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = badgeBackground,
    modifier =
      Modifier.fillMaxWidth()
        .border(1.dp, borderColor, RoundedCornerShape(16.dp)),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text =
              when {
                manualCustomer != null -> "Selected: ${manualCustomer.name} (Manual)"
                autoDetected != null -> "Detected: ${autoDetected.name}"
                !hasLocationPermission -> "Location Permission Required"
                uiState.currentLocation == null -> "Acquiring GPS Location..."
                else -> "No Customer for Location"
              },
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          val subtitleText =
            when {
              manualCustomer != null -> {
                val locLabel = manualLocation?.label ?: "Primary Site"
                val locAddr = manualLocation?.address ?: manualCustomer.address
                "Location: $locLabel • $locAddr"
              }
              autoDetected != null -> {
                val matchedLoc = uiState.autoDetectedLocation
                val locLabel = matchedLoc?.label ?: "Site"
                val locAddr = matchedLoc?.address ?: autoDetected.address
                "${closest?.distanceMeters?.roundToInt() ?: 0}m away • $locLabel ($locAddr)"
              }
              uiState.currentLocation != null ->
                "GPS: ${"%.5f".format(uiState.currentLocation.latitude)}, ${"%.5f".format(uiState.currentLocation.longitude)}"
              else -> null
            }
          if (subtitleText != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = subtitleText,
              color = Color(0xFFD1D5DB),
              style = MaterialTheme.typography.bodySmall,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        if (manualCustomer != null) {
          Spacer(modifier = Modifier.width(8.dp))
          OutlinedButton(
            onClick = onResetToAutoGps,
            colors =
              ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFF93C5FD)
              ),
            modifier = Modifier.height(32.dp),
          ) {
            Text("Use Auto GPS", style = MaterialTheme.typography.labelSmall)
          }
        }
      }

      // Row 1: Select Customer | Add Customer
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        FilledTonalButton(
          onClick = onOpenCustomerPicker,
          colors =
            ButtonDefaults.filledTonalButtonColors(
              containerColor = Color(0xFF1E293B),
              contentColor = Color.White,
            ),
          modifier = Modifier.weight(1f).height(36.dp),
        ) {
          Text(
            text = "Select Customer",
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
          )
        }

        Button(
          onClick = onAddCustomerClick,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = Color(0xFF10B981),
              contentColor = Color.White,
            ),
          modifier = Modifier.weight(1f).height(36.dp),
        ) {
          Text(
            text = "Add Customer",
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
          )
        }
      }

      // Row 2: Customers & Photos
      Button(
        onClick = onOpenCustomerHub,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = Color(0xFF0284C7),
            contentColor = Color.White,
          ),
        modifier = Modifier.fillMaxWidth().height(36.dp),
      ) {
        Text(
          text = "Customers & Photos",
          style = MaterialTheme.typography.labelSmall,
          maxLines = 1,
        )
      }
    }
  }
}

@Composable
private fun CustomerPickerDialog(
  uiState: FieldCameraUiState,
  onSelectCustomerAndLocation: (Long?, Long?) -> Unit,
  onManageCustomersClick: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = Color(0xFF1E293B),
    titleContentColor = Color.White,
    textContentColor = Color.White,
    title = { Text("Select Customer & Site Location", fontWeight = FontWeight.Bold) },
    text = {
      LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        item {
          val isAutoMode = uiState.manualCustomerOverrideId == null
          val autoDetectedName =
            uiState.autoDetectedCustomer?.let { cust ->
              val locName = uiState.autoDetectedLocation?.label?.let { " ($it)" }.orEmpty()
              "${cust.name}$locName"
            } ?: "None within 100m"
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (isAutoMode) Color(0xFF065F46) else Color(0xFF0F172A),
            modifier =
              Modifier.fillMaxWidth()
                .clickable { onSelectCustomerAndLocation(null, null) },
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              RadioButton(
                selected = isAutoMode,
                onClick = { onSelectCustomerAndLocation(null, null) },
              )
              Spacer(modifier = Modifier.width(6.dp))
              Column {
                Text(
                  text = "Auto-Detect via GPS (< 100m)",
                  fontWeight = FontWeight.SemiBold,
                  color = Color.White,
                )
                Text(
                  text = "Currently detected: $autoDetectedName",
                  style = MaterialTheme.typography.bodySmall,
                  color = Color(0xFFD1D5DB),
                )
              }
            }
          }
        }

        item {
          HorizontalDivider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 4.dp))
          Text(
            text = "Or Select Customer & Site Location Manually:",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF94A3B8),
          )
        }

        items(uiState.customers) { customer ->
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF0F172A),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = customer.name,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
              )

              if (customer.locations.isEmpty()) {
                val isSelected = uiState.manualCustomerOverrideId == customer.id
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier =
                    Modifier.fillMaxWidth()
                      .clip(RoundedCornerShape(8.dp))
                      .background(if (isSelected) Color(0xFF1E3A8A) else Color(0xFF1E293B))
                      .clickable { onSelectCustomerAndLocation(customer.id, null) }
                      .padding(8.dp),
                ) {
                  RadioButton(
                    selected = isSelected,
                    onClick = { onSelectCustomerAndLocation(customer.id, null) },
                  )
                  Text(
                    text = "Default Site",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                  )
                }
              } else {
                customer.locations.forEach { loc ->
                  val isLocSelected =
                    uiState.manualCustomerOverrideId == customer.id &&
                      (uiState.manualLocationOverrideId == loc.id ||
                        (uiState.manualLocationOverrideId == null &&
                          customer.locations.first().id == loc.id))
                  val distMeters =
                    uiState.currentLocation?.let { deviceLoc ->
                      LocationTracker.distanceBetween(deviceLoc, loc.latitude, loc.longitude)
                    }
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                      Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isLocSelected) Color(0xFF1E3A8A) else Color(0xFF1E293B))
                        .clickable { onSelectCustomerAndLocation(customer.id, loc.id) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                  ) {
                    RadioButton(
                      selected = isLocSelected,
                      onClick = { onSelectCustomerAndLocation(customer.id, loc.id) },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text =
                          buildString {
                            append(loc.label)
                            if (distMeters != null) {
                              append(" (${distMeters.roundToInt()}m)")
                            }
                          },
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall,
                      )
                      Text(
                        text = loc.address,
                        color = Color(0xFF94A3B8),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onManageCustomersClick) {
        Text("+ Add Customer / View Photos", color = Color(0xFF38BDF8))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Close", color = Color(0xFF94A3B8))
      }
    },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerHubBottomSheet(
  uiState: FieldCameraUiState,
  onSelectHubCustomer: (Long) -> Unit,
  onSetAsActiveCustomerAndLocation: (Long, Long?) -> Unit,
  onCreateCustomer: (String, String, Boolean, String) -> Unit,
  onDeleteCustomer: (Long) -> Unit,
  onAddLocationToCustomer: (Long, String, Boolean, String) -> Unit,
  onDeleteLocationFromCustomer: (Long, String) -> Unit,
  onOpenSearchSitesSettings: () -> Unit,
  onTriggerDemoAi: (Long?) -> Unit,
  onShowPartsForPhoto: (CapturedPhotoRow) -> Unit,
  onClose: () -> Unit,
) {
  val selectedCustomer = uiState.selectedHubCustomer
  var selectedTabIndex by remember { mutableIntStateOf(0) } // 0 = Photos, 1 = Locations
  var showNewCustomerDialog by remember { mutableStateOf(false) }
  var showAddLocationDialog by remember { mutableStateOf(false) }
  var customerPendingDeletion by remember { mutableStateOf<Customer?>(null) }
  var locationPendingDeletion by remember { mutableStateOf<Pair<Long, String>?>(null) }

  Column(
    modifier =
      Modifier.fillMaxWidth()
        .fillMaxHeight(0.88f)
        .padding(horizontal = 16.dp)
        .padding(bottom = 24.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // Header Row
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
        Text(
          text = "Customers, Locations & Photos",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = Color.White,
        )
        Text(
          text = "Add customers, manage site locations, or inspect captured photos",
          style = MaterialTheme.typography.bodySmall,
          color = Color(0xFF94A3B8),
        )
      }
      Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Button(
          onClick = { showNewCustomerDialog = true },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = Color(0xFF10B981),
              contentColor = Color.White,
            ),
          modifier = Modifier.height(34.dp),
        ) {
          Text("+ New Customer", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
          onClick = onOpenSearchSitesSettings,
          colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
          modifier = Modifier.height(32.dp),
        ) {
          Text("Search Sites (${uiState.searchSites.size})", style = MaterialTheme.typography.labelSmall)
        }
      }
    }

    // Horizontal Customer Selector Chips
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      uiState.customers.forEach { customer ->
        val isSelected = selectedCustomer?.id == customer.id
        FilterChip(
          selected = isSelected,
          onClick = { onSelectHubCustomer(customer.id) },
          label = {
            Text(
              text = "${customer.name} (${customer.locations.size})",
              color = if (isSelected) Color.White else Color(0xFFCBD5E1),
            )
          },
          colors =
            FilterChipDefaults.filterChipColors(
              selectedContainerColor = Color(0xFF0284C7),
              containerColor = Color(0xFF1E293B),
            ),
        )
      }
    }

    if (selectedCustomer != null) {
      // Selected Customer Action Bar
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1E293B),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = selectedCustomer.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
              )
              Text(
                text =
                  "${selectedCustomer.locations.size} location(s) • ${uiState.customerPhotos.size} photo(s) captured",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8),
              )
            }
            OutlinedButton(
              onClick = { customerPendingDeletion = selectedCustomer },
              colors =
                ButtonDefaults.outlinedButtonColors(
                  contentColor = Color(0xFFF87171),
                ),
              modifier = Modifier.height(32.dp),
            ) {
              Text("Remove Customer", style = MaterialTheme.typography.labelSmall)
            }
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Button(
              onClick = { showAddLocationDialog = true },
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = Color(0xFF0284C7),
                  contentColor = Color.White,
                ),
              modifier = Modifier.weight(1f),
            ) {
              Text("+ Add Site Location", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
              onClick = {
                onSetAsActiveCustomerAndLocation(
                  selectedCustomer.id,
                  selectedCustomer.locations.firstOrNull()?.id,
                )
              },
              colors =
                ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
              modifier = Modifier.weight(1f),
            ) {
              Text("Tag Camera Here", style = MaterialTheme.typography.labelMedium)
            }
          }
        }
      }

      // Tabs: Photos Taken vs Site Locations
      SecondaryTabRow(
        selectedTabIndex = selectedTabIndex,
        containerColor = Color(0xFF1E293B),
        contentColor = Color.White,
      ) {
        Tab(
          selected = selectedTabIndex == 0,
          onClick = { selectedTabIndex = 0 },
          text = { Text("Photos Taken (${uiState.customerPhotos.size})") },
        )
        Tab(
          selected = selectedTabIndex == 1,
          onClick = { selectedTabIndex = 1 },
          text = { Text("Site Locations (${selectedCustomer.locations.size})") },
        )
      }

      if (selectedTabIndex == 0) {
        // Tab 0: Photos Taken by Customer (shows Customer + Saved Site Location on every photo)
        CustomerPhotosSection(
          customer = selectedCustomer,
          photos = uiState.customerPhotos,
          isLoading = uiState.isLoadingCustomerPhotos,
          photoBitmapsCache = uiState.photoBitmapsCache,
          onTriggerDemoAi = onTriggerDemoAi,
          onShowPartsForPhoto = onShowPartsForPhoto,
          modifier = Modifier.weight(1f),
        )
      } else {
        // Tab 1: Multiple Locations for Customer
        CustomerLocationsSection(
          customer = selectedCustomer,
          currentLocation = uiState.currentLocation,
          onAddLocationClick = { showAddLocationDialog = true },
          onTagLocationClick = { locId ->
            onSetAsActiveCustomerAndLocation(selectedCustomer.id, locId)
          },
          onDeleteLocationClick = { locId, locLabel ->
            locationPendingDeletion = locId to locLabel
          },
          modifier = Modifier.weight(1f),
        )
      }
    }
  }

  // Confirm Remove Customer Dialog (cascades and deletes all locations for that customer)
  customerPendingDeletion?.let { cust ->
    AlertDialog(
      onDismissRequest = { customerPendingDeletion = null },
      title = { Text("Remove Customer?") },
      text = {
        Text(
          "Are you sure you want to remove '${cust.name}'? This will also remove all ${cust.locations.size} site location(s) under this customer."
        )
      },
      confirmButton = {
        Button(
          onClick = {
            customerPendingDeletion = null
            onDeleteCustomer(cust.id)
          },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = Color(0xFFDC2626),
              contentColor = Color.White,
            ),
        ) {
          Text("Remove Customer")
        }
      },
      dismissButton = {
        TextButton(onClick = { customerPendingDeletion = null }) {
          Text("Cancel")
        }
      },
    )
  }

  // Confirm Remove Site Location Dialog
  locationPendingDeletion?.let { (locId, locLabel) ->
    AlertDialog(
      onDismissRequest = { locationPendingDeletion = null },
      title = { Text("Remove Site Location?") },
      text = {
        Text("Are you sure you want to remove the location '$locLabel'?")
      },
      confirmButton = {
        Button(
          onClick = {
            locationPendingDeletion = null
            onDeleteLocationFromCustomer(locId, locLabel)
          },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = Color(0xFFDC2626),
              contentColor = Color.White,
            ),
        ) {
          Text("Remove Location")
        }
      },
      dismissButton = {
        TextButton(onClick = { locationPendingDeletion = null }) {
          Text("Cancel")
        }
      },
    )
  }

  if (showNewCustomerDialog) {
    AddCustomerDialog(
      hasCurrentGps = uiState.currentLocation != null,
      isSaving = uiState.isSavingCustomerOrLocation,
      onConfirm = { name, locLabel, useGps, address ->
        showNewCustomerDialog = false
        onCreateCustomer(name, locLabel, useGps, address)
      },
      onDismiss = { showNewCustomerDialog = false },
    )
  }

  if (showAddLocationDialog && selectedCustomer != null) {
    AddLocationDialog(
      customerName = selectedCustomer.name,
      currentLocation = uiState.currentLocation,
      isSaving = uiState.isSavingCustomerOrLocation,
      onConfirm = { label, useGps, address ->
        showAddLocationDialog = false
        onAddLocationToCustomer(selectedCustomer.id, label, useGps, address)
      },
      onDismiss = { showAddLocationDialog = false },
    )
  }
}

@Composable
private fun CustomerPhotosSection(
  customer: Customer,
  photos: List<CapturedPhotoRow>,
  isLoading: Boolean,
  photoBitmapsCache: Map<String, ByteArray>,
  onTriggerDemoAi: (Long?) -> Unit,
  onShowPartsForPhoto: (CapturedPhotoRow) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (isLoading) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      CircularProgressIndicator(color = Color(0xFF38BDF8))
    }
    return
  }

  if (photos.isEmpty()) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      Text(
        text =
          "No photos taken for ${customer.name} yet.\nClose this sheet and press the camera shutter to capture one!",
        color = Color(0xFF94A3B8),
        style = MaterialTheme.typography.bodyMedium,
      )
    }
    return
  }

  LazyColumn(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    items(photos, key = { it.id }) { photo ->
      val parts = AnalysisJsonParser.parseParts(photo.aiAnalysisJson)
      val matchedLoc =
        customer.locations.find { it.id == photo.customerLocationId }
          ?: customer.locations.firstOrNull()
      val imageBytes = photoBitmapsCache[photo.imageUrl]
      val imageBitmap =
        remember(imageBytes) {
          imageBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
          }
        }

      Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          // Photo Preview Image
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0B0F14)),
            contentAlignment = Alignment.Center,
          ) {
            if (imageBitmap != null) {
              Image(
                bitmap = imageBitmap,
                contentDescription = "Captured Photo #${photo.id}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
              )
            } else {
              CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = Color(0xFF38BDF8),
                strokeWidth = 2.dp,
              )
            }
          }

          // Customer + Site Location metadata card saved with the photo
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF0F172A),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(10.dp),
              verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
              Text(
                text = "Customer: ${customer.name}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF38BDF8),
              )
              Text(
                text =
                  if (matchedLoc != null) {
                    "Site Location: ${matchedLoc.label} • ${matchedLoc.address}"
                  } else {
                    "Site Location: ${customer.address.ifBlank { "Primary Site" }}"
                  },
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFE2E8F0),
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Photo #${photo.id}",
                fontWeight = FontWeight.Bold,
                color = Color.White,
              )
              Text(
                text = photo.capturedAt?.take(19)?.replace("T", " ") ?: "Just now",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8),
              )
            }

            if (parts.isNotEmpty()) {
              Button(
                onClick = { onShowPartsForPhoto(photo) },
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF059669),
                    contentColor = Color.White,
                  ),
              ) {
                Text("View ${parts.size} AI Part(s)", style = MaterialTheme.typography.labelSmall)
              }
            } else {
              FilledTonalButton(
                onClick = { onTriggerDemoAi(photo.id) },
              ) {
                Text("Simulate AI ID", style = MaterialTheme.typography.labelSmall)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CustomerLocationsSection(
  customer: Customer,
  currentLocation: Location?,
  onAddLocationClick: () -> Unit,
  onTagLocationClick: (Long) -> Unit,
  onDeleteLocationClick: (Long, String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "Saved Job Site Locations",
        style = MaterialTheme.typography.titleSmall,
        color = Color(0xFFCBD5E1),
        modifier = Modifier.weight(1f).padding(end = 8.dp),
      )
      Button(
        onClick = onAddLocationClick,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = Color(0xFF0284C7),
            contentColor = Color.White,
          ),
      ) {
        Text("+ Add Location")
      }
    }

    LazyColumn(
      verticalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier.weight(1f),
    ) {
      items(customer.locations, key = { it.id }) { loc ->
        val distMeters =
          currentLocation?.let {
            LocationTracker.distanceBetween(it, loc.latitude, loc.longitude)
          }
        val isInsideGeofence = distMeters != null && distMeters < 100f

        Card(
          colors =
            CardDefaults.cardColors(
              containerColor = if (isInsideGeofence) Color(0xFF064E3B) else Color(0xFF1E293B)
            ),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = loc.label,
                  fontWeight = FontWeight.Bold,
                  color = Color.White,
                )
                Text(
                  text = loc.address,
                  style = MaterialTheme.typography.bodySmall,
                  color = Color(0xFFD1D5DB),
                )
                Text(
                  text =
                    "Coords: ${"%.5f".format(loc.latitude)}, ${"%.5f".format(loc.longitude)}",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color(0xFF94A3B8),
                )
              }
              if (distMeters != null) {
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = if (isInsideGeofence) Color(0xFF10B981) else Color(0xFF334155),
                ) {
                  Text(
                    text = "${distMeters.roundToInt()}m",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                  )
                }
              }
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedButton(
                onClick = { onTagLocationClick(loc.id) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                modifier = Modifier.weight(1f).height(34.dp),
              ) {
                Text(
                  text = "Tag Camera Here",
                  style = MaterialTheme.typography.labelSmall,
                )
              }
              OutlinedButton(
                onClick = { onDeleteLocationClick(loc.id, loc.label) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF87171)),
                modifier = Modifier.height(34.dp),
              ) {
                Text(
                  text = "Remove",
                  style = MaterialTheme.typography.labelSmall,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AddCustomerDialog(
  hasCurrentGps: Boolean,
  isSaving: Boolean,
  onConfirm: (String, String, Boolean, String) -> Unit,
  onDismiss: () -> Unit,
) {
  var customerName by remember { mutableStateOf("") }
  var locationLabel by remember { mutableStateOf("Main Building") }
  var useCurrentGps by remember { mutableStateOf(hasCurrentGps) }
  var addressText by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add New Customer") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = customerName,
          onValueChange = { customerName = it },
          label = { Text("Customer / Company Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = locationLabel,
          onValueChange = { locationLabel = it },
          label = { Text("First Site Label (e.g. Main Roof, Building A)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FilterChip(
            selected = useCurrentGps,
            onClick = { useCurrentGps = true },
            label = { Text("Use Current GPS") },
          )
          FilterChip(
            selected = !useCurrentGps,
            onClick = { useCurrentGps = false },
            label = { Text("By Street Address") },
          )
        }

        if (!useCurrentGps) {
          OutlinedTextField(
            value = addressText,
            onValueChange = { addressText = it },
            label = { Text("Street Address (City, State)") },
            placeholder = { Text("e.g. 350 5th Ave, New York, NY") },
            modifier = Modifier.fillMaxWidth(),
          )
        } else {
          OutlinedTextField(
            value = addressText,
            onValueChange = { addressText = it },
            label = { Text("Optional Address Note") },
            placeholder = { Text("Uses your current device GPS coordinates") },
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    },
    confirmButton = {
      Button(
        enabled = customerName.isNotBlank() && !isSaving,
        onClick = { onConfirm(customerName, locationLabel, useCurrentGps, addressText) },
      ) {
        Text("Save Customer")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    },
  )
}

@Composable
private fun AddLocationDialog(
  customerName: String,
  currentLocation: Location?,
  isSaving: Boolean,
  onConfirm: (String, Boolean, String) -> Unit,
  onDismiss: () -> Unit,
) {
  var locationLabel by remember { mutableStateOf("Secondary Site / Roof Unit") }
  var useCurrentGps by remember { mutableStateOf(currentLocation != null) }
  var addressText by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add Location for $customerName") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = locationLabel,
          onValueChange = { locationLabel = it },
          label = { Text("Location Name (e.g. North Wing, Annex)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FilterChip(
            selected = useCurrentGps,
            onClick = { useCurrentGps = true },
            label = { Text("Current GPS Location") },
          )
          FilterChip(
            selected = !useCurrentGps,
            onClick = { useCurrentGps = false },
            label = { Text("Street Address") },
          )
        }

        if (useCurrentGps) {
          Text(
            text =
              if (currentLocation != null) {
                "Device GPS: ${"%.5f".format(currentLocation.latitude)}, ${"%.5f".format(currentLocation.longitude)}"
              } else {
                "Waiting for GPS signal..."
              },
            style = MaterialTheme.typography.bodySmall,
          )
          OutlinedTextField(
            value = addressText,
            onValueChange = { addressText = it },
            label = { Text("Address / Site Note (Optional)") },
            modifier = Modifier.fillMaxWidth(),
          )
        } else {
          OutlinedTextField(
            value = addressText,
            onValueChange = { addressText = it },
            label = { Text("Enter Street Address") },
            placeholder = { Text("e.g. 1600 Amphitheatre Pkwy, Mountain View, CA") },
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    },
    confirmButton = {
      Button(
        enabled = (useCurrentGps || addressText.isNotBlank()) && !isSaving,
        onClick = { onConfirm(locationLabel, useCurrentGps, addressText) },
      ) {
        Text("Add Location")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    },
  )
}

@Composable
private fun CameraXPreview(
  imageCapture: ImageCapture,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val previewView = remember {
    PreviewView(context).apply {
      layoutParams =
        ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT,
        )
      scaleType = PreviewView.ScaleType.FILL_CENTER
      implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    }
  }

  LaunchedEffect(lifecycleOwner) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener(
      {
        val cameraProvider = cameraProviderFuture.get()
        val preview =
          CameraPreview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
          }

        try {
          cameraProvider.unbindAll()
          cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageCapture,
          )
        } catch (_: Exception) {
          // Ignore binding error on devices without a rear camera
        }
      },
      ContextCompat.getMainExecutor(context),
    )
  }

  AndroidView(
    factory = { previewView },
    modifier = modifier,
  )
}

@Composable
private fun ShutterButton(
  enabled: Boolean,
  isUploading: Boolean,
  onClick: () -> Unit,
) {
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      Modifier.size(84.dp)
        .clip(CircleShape)
        .border(4.dp, Color.White, CircleShape)
        .padding(8.dp)
        .clip(CircleShape)
        .background(if (enabled) Color.White else Color.Gray)
        .clickable(enabled = enabled, onClick = onClick),
  ) {
    if (isUploading) {
      CircularProgressIndicator(
        modifier = Modifier.size(32.dp),
        color = Color(0xFF0B0F14),
        strokeWidth = 3.dp,
      )
    }
  }
}

@Composable
private fun PermissionPromptOverlay(
  hasCameraPermission: Boolean,
  hasLocationPermission: Boolean,
  onRequestPermissions: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.padding(24.dp).fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2937)),
  ) {
    Column(
      modifier = Modifier.padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "Camera & GPS Access Needed",
        style = MaterialTheme.typography.titleLarge,
        color = Color.White,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text =
          "JobSight requires Camera access for zero-click field photos and Fine Location to auto-tag customers within 100 meters.",
        style = MaterialTheme.typography.bodyMedium,
        color = Color(0xFFD1D5DB),
      )
      Button(onClick = onRequestPermissions) {
        Text(
          when {
            !hasCameraPermission && !hasLocationPermission -> "Grant Camera & Location"
            !hasCameraPermission -> "Grant Camera Permission"
            else -> "Grant Location Permission"
          }
        )
      }
    }
  }
}

@Composable
private fun AiPartAnalysisBottomSheet(
  customerName: String,
  parts: List<HvacPartAnalysis>,
  searchSites: List<SearchSite>,
  onOpenSearchSitesSettings: () -> Unit,
  onSearchPartOnSite: (SearchSite, String) -> Unit,
  onClose: () -> Unit,
) {
  var partPendingSiteSelection by remember { mutableStateOf<HvacPartAnalysis?>(null) }

  Column(
    modifier =
      Modifier.fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 32.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "AI HVAC Part Identification",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = Color.White,
        )
        Text(
          text = "Tagged Customer: $customerName",
          style = MaterialTheme.typography.bodySmall,
          color = Color(0xFF9CA3AF),
        )
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onOpenSearchSitesSettings) {
          Text("Search Sites", color = Color(0xFF34D399))
        }
        TextButton(onClick = onClose) {
          Text("Done", color = Color(0xFF38BDF8))
        }
      }
    }

    LazyColumn(
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      items(parts) { part ->
        Card(
          colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2937)),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = part.partName,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
              color = Color.White,
            )
            if (!part.modelNumber.isNullOrBlank()) {
              Text(
                text = "Model / Part #: ${part.modelNumber}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF34D399),
              )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Button(
              onClick = { partPendingSiteSelection = part },
              modifier = Modifier.fillMaxWidth(),
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = Color(0xFF0284C7),
                  contentColor = Color.White,
                ),
            ) {
              Text("Search Online")
            }
          }
        }
      }
    }
  }

  // Popup to pick which configured search site to open for the selected HVAC part
  partPendingSiteSelection?.let { selectedPart ->
    AlertDialog(
      onDismissRequest = { partPendingSiteSelection = null },
      containerColor = Color(0xFF1E293B),
      titleContentColor = Color.White,
      textContentColor = Color.White,
      title = {
        Column {
          Text("Choose Site to Search", fontWeight = FontWeight.Bold)
          Text(
            text = selectedPart.partName,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF94A3B8),
          )
        }
      },
      text = {
        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          items(searchSites, key = { it.id }) { site ->
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = Color(0xFF0F172A),
              modifier =
                Modifier.fillMaxWidth()
                  .clickable {
                    partPendingSiteSelection = null
                    onSearchPartOnSite(site, selectedPart.searchQuery)
                  },
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = site.name,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                  )
                  Text(
                    text = "Search: \"${selectedPart.searchQuery}\"",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF38BDF8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                Text(
                  text = "Open →",
                  color = Color(0xFF34D399),
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            partPendingSiteSelection = null
            onOpenSearchSitesSettings()
          }
        ) {
          Text("Manage Search Sites", color = Color(0xFF38BDF8))
        }
      },
      dismissButton = {
        TextButton(onClick = { partPendingSiteSelection = null }) {
          Text("Cancel", color = Color(0xFF94A3B8))
        }
      },
    )
  }
}

@Composable
private fun SearchSitesSettingsDialog(
  searchSites: List<SearchSite>,
  onAddSite: (String, String) -> Unit,
  onDeleteSite: (Long, String) -> Unit,
  onDismiss: () -> Unit,
) {
  var newSiteName by remember { mutableStateOf("") }
  var newSiteUrl by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = Color(0xFF1E293B),
    titleContentColor = Color.White,
    textContentColor = Color.White,
    title = { Text("Part Search Sites Settings", fontWeight = FontWeight.Bold) },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = "Configure which websites appear when you tap 'Search Online' on an identified HVAC part.",
          style = MaterialTheme.typography.bodySmall,
          color = Color(0xFFCBD5E1),
        )

        // Add new search site form
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = Color(0xFF0F172A),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = "Add a Search Site",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF38BDF8),
            )
            OutlinedTextField(
              value = newSiteName,
              onValueChange = { newSiteName = it },
              label = { Text("Site Name (e.g. Ferguson, Johnstone)") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
              value = newSiteUrl,
              onValueChange = { newSiteUrl = it },
              label = { Text("Domain or Search URL") },
              placeholder = { Text("e.g. ferguson.com or https://.../search?q={query}") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
            Button(
              enabled = newSiteName.isNotBlank() && newSiteUrl.isNotBlank(),
              onClick = {
                onAddSite(newSiteName, newSiteUrl)
                newSiteName = ""
                newSiteUrl = ""
              },
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = Color(0xFF10B981),
                  contentColor = Color.White,
                ),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("+ Add Search Site")
            }
          }
        }

        Text(
          text = "Saved Search Sites (${searchSites.size})",
          style = MaterialTheme.typography.labelMedium,
          color = Color(0xFF94A3B8),
        )

        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.height(200.dp).fillMaxWidth(),
        ) {
          items(searchSites, key = { it.id }) { site ->
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = Color(0xFF0F172A),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                  Text(
                    text = site.name,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                  )
                  Text(
                    text = site.urlTemplate,
                    color = Color(0xFF94A3B8),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                TextButton(
                  onClick = { onDeleteSite(site.id, site.name) },
                ) {
                  Text("Remove", color = Color(0xFFF87171), style = MaterialTheme.typography.labelSmall)
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Done", color = Color(0xFF38BDF8))
      }
    },
  )
}

private fun capturePhotoToCache(
  context: Context,
  imageCapture: ImageCapture,
  onCaptured: (File) -> Unit,
) {
  val cacheFile =
    File(
      context.cacheDir,
      "jobsight_capture_${System.currentTimeMillis()}.jpg",
    )
  val outputOptions = ImageCapture.OutputFileOptions.Builder(cacheFile).build()

  imageCapture.takePicture(
    outputOptions,
    ContextCompat.getMainExecutor(context),
    object : ImageCapture.OnImageSavedCallback {
      override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
        onCaptured(cacheFile)
      }

      override fun onError(exception: ImageCaptureException) {
        if (cacheFile.exists()) {
          cacheFile.delete()
        }
      }
    },
  )
}
