package com.jobsight.app.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.jobsight.app.R
import com.jobsight.app.data.AnalysisJsonParser
import com.jobsight.app.data.CapturedPhotoRow
import com.jobsight.app.data.Customer
import com.jobsight.app.data.DefaultDataRepository
import com.jobsight.app.data.HvacPartAnalysis
import com.jobsight.app.data.SearchSite
import com.jobsight.app.location.LocationTracker
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MainScreen(
  onItemClick: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val viewModel: MainScreenViewModel = viewModel {
    MainScreenViewModel(DefaultDataRepository(context.applicationContext))
  }
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

  var showLaunchSplash by remember { mutableStateOf(true) }
  LaunchedEffect(Unit) {
    delay(1500L)
    showLaunchSplash = false
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

  Box(modifier = modifier.fillMaxSize().background(Color(0xFF0E2243))) {
    if (!uiState.isCheckingAuthSession && uiState.companySession == null) {
      CompanyAuthScreen(
        isAuthenticating = uiState.isAuthenticatingCompany,
        errorMessage = uiState.authErrorMessage,
        onAuthenticate = viewModel::authenticateCompany,
      )
    } else {
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
        onAddLocationToCustomer = { customerId, label, useGps, address ->
          viewModel.addLocationToCustomer(customerId, label, useGps, address, selectForCameraTag = false)
        },
        onAddAndSelectLocationForCustomer = { customerId, label, useGps, address ->
          viewModel.addLocationToCustomer(customerId, label, useGps, address, selectForCameraTag = true)
        },
        onDeleteLocationFromCustomer = viewModel::deleteLocationFromCustomer,
        onOpenSearchSitesSettings = viewModel::openSearchSitesSettings,
        onCloseSearchSitesSettings = viewModel::closeSearchSitesSettings,
        onOpenSettingsSheet = viewModel::openSettingsSheet,
        onCloseSettingsSheet = viewModel::closeSettingsSheet,
        onSetGeofenceRadius = viewModel::setGeofenceRadiusMeters,
        onAddSearchSite = viewModel::addSearchSite,
        onDeleteSearchSite = viewModel::deleteSearchSite,
        onDeletePhotos = viewModel::deletePhotosForCurrentCustomer,
        onSignOutCompany = viewModel::signOutCompany,
        onTriggerDemoAi = viewModel::triggerDemoAiVisionUpdate,
        onShowPartsForPhoto = viewModel::showPartsForPhoto,
        onDismissBottomSheet = viewModel::dismissBottomSheet,
        onReopenBottomSheet = viewModel::reopenBottomSheet,
        onClearStatusBanner = viewModel::clearStatusBanner,
        onDismissError = viewModel::clearError,
        modifier = Modifier.fillMaxSize(),
      )
    }

    AnimatedVisibility(
      visible = showLaunchSplash,
      modifier = Modifier.fillMaxSize(),
    ) {
      JobSightLaunchSplashScreen()
    }
  }
}

@Composable
private fun CompanyAuthScreen(
  isAuthenticating: Boolean,
  errorMessage: String?,
  onAuthenticate: (String, String, Boolean) -> Unit,
) {
  var selectedTab by remember { mutableIntStateOf(0) } // 0 = Sign In, 1 = Create Account
  var companyName by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var confirmPassword by remember { mutableStateOf("") }
  var showPassword by remember { mutableStateOf(false) }
  var localValidationError by remember { mutableStateOf<String?>(null) }

  val isCreateMode = selectedTab == 1

  Column(
    modifier =
      Modifier.fillMaxSize()
        .background(Color(0xFF0E2243))
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(horizontal = 24.dp, vertical = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Image(
      painter = painterResource(id = R.drawable.jobsight_logo_full),
      contentDescription = "JobSight Logo",
      contentScale = ContentScale.Fit,
      modifier = Modifier.fillMaxWidth(0.68f).height(160.dp),
    )

    Spacer(modifier = Modifier.height(12.dp))

    Surface(
      shape = RoundedCornerShape(16.dp),
      color = Color(0xFF132D57),
      modifier =
        Modifier.fillMaxWidth()
          .border(1.dp, Color(0xFF0F93E8), RoundedCornerShape(16.dp)),
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        SecondaryTabRow(
          selectedTabIndex = selectedTab,
          containerColor = Color(0xFF0E2243),
          contentColor = Color.White,
        ) {
          Tab(
            selected = selectedTab == 0,
            onClick = {
              selectedTab = 0
              localValidationError = null
            },
            text = { Text("Sign In", fontWeight = FontWeight.Bold) },
          )
          Tab(
            selected = selectedTab == 1,
            onClick = {
              selectedTab = 1
              localValidationError = null
            },
            text = { Text("Create Account", fontWeight = FontWeight.Bold) },
          )
        }

        Text(
          text =
            if (isCreateMode) {
              "Create a new HVAC Company account. Your company's customers, locations, and photos are completely private to your company."
            } else {
              "Enter your HVAC Company Name and Password to access your company's customers and field photos."
            },
          style = MaterialTheme.typography.bodySmall,
          color = Color(0xFFC7D6EB),
        )

        OutlinedTextField(
          value = companyName,
          onValueChange = {
            companyName = it
            localValidationError = null
          },
          label = { Text("Company Name") },
          placeholder = { Text("e.g. Apex Heating & Cooling") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
          value = password,
          onValueChange = {
            password = it
            localValidationError = null
          },
          label = { Text("Password (min 6 chars)") },
          singleLine = true,
          visualTransformation =
            if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
          trailingIcon = {
            TextButton(onClick = { showPassword = !showPassword }) {
              Text(
                text = if (showPassword) "Hide" else "Show",
                color = Color(0xFF38BDF8),
                style = MaterialTheme.typography.labelSmall,
              )
            }
          },
          modifier = Modifier.fillMaxWidth(),
        )

        if (isCreateMode) {
          OutlinedTextField(
            value = confirmPassword,
            onValueChange = {
              confirmPassword = it
              localValidationError = null
            },
            label = { Text("Confirm Password") },
            singleLine = true,
            visualTransformation =
              if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
          )
        }

        val activeError = localValidationError ?: errorMessage
        if (!activeError.isNullOrBlank()) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF7F1D1D),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = activeError,
              color = Color.White,
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.padding(10.dp),
            )
          }
        }

        Button(
          enabled = !isAuthenticating && companyName.isNotBlank() && password.isNotBlank(),
          onClick = {
            if (companyName.trim().length < 2) {
              localValidationError = "Company Name must be at least 2 characters."
              return@Button
            }
            if (password.length < 6) {
              localValidationError = "Password must be at least 6 characters."
              return@Button
            }
            if (isCreateMode && password != confirmPassword) {
              localValidationError = "Passwords do not match."
              return@Button
            }
            onAuthenticate(companyName.trim(), password, isCreateMode)
          },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = Color(0xFF0F93E8),
              contentColor = Color.White,
            ),
          modifier = Modifier.fillMaxWidth().height(46.dp),
        ) {
          if (isAuthenticating) {
            CircularProgressIndicator(
              modifier = Modifier.size(22.dp),
              color = Color.White,
              strokeWidth = 2.dp,
            )
          } else {
            Text(
              text = if (isCreateMode) "Create Company Account" else "Sign In to Company",
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun JobSightLaunchSplashScreen() {
  Box(
    modifier = Modifier.fillMaxSize().background(Color(0xFF0E2243)),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(20.dp),
      modifier = Modifier.padding(32.dp),
    ) {
      Image(
        painter = painterResource(id = R.drawable.jobsight_logo_full),
        contentDescription = "JobSight Logo",
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxWidth(0.82f).height(240.dp),
      )
      CircularProgressIndicator(
        modifier = Modifier.size(32.dp),
        color = Color(0xFFEE4D38),
        trackColor = Color(0xFF0F93E8),
        strokeWidth = 3.dp,
      )
    }
  }
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
  onAddAndSelectLocationForCustomer: (Long, String, Boolean, String) -> Unit = onAddLocationToCustomer,
  onDeleteLocationFromCustomer: (Long, String) -> Unit,
  onOpenSearchSitesSettings: () -> Unit,
  onCloseSearchSitesSettings: () -> Unit,
  onOpenSettingsSheet: () -> Unit = onOpenSearchSitesSettings,
  onCloseSettingsSheet: () -> Unit = onCloseSearchSitesSettings,
  onSetGeofenceRadius: (Float) -> Unit = {},
  onAddSearchSite: (String, String) -> Unit,
  onDeleteSearchSite: (Long, String) -> Unit,
  onDeletePhotos: (List<CapturedPhotoRow>) -> Unit = {},
  onSignOutCompany: () -> Unit = {},
  onTriggerDemoAi: (Long?) -> Unit,
  onShowPartsForPhoto: (CapturedPhotoRow) -> Unit,
  onDismissBottomSheet: () -> Unit,
  onReopenBottomSheet: () -> Unit,
  onClearStatusBanner: () -> Unit = {},
  onDismissError: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val imageCapture = remember {
    ImageCapture.Builder()
      .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
      .build()
  }
  var activeCamera by remember { mutableStateOf<Camera?>(null) }
  var currentZoomRatio by remember { mutableFloatStateOf(1.0f) }
  var minZoomRatio by remember { mutableFloatStateOf(1.0f) }
  var maxZoomRatio by remember { mutableFloatStateOf(8.0f) }
  var isTorchEnabled by remember { mutableStateOf(false) }
  var hasFlashUnit by remember { mutableStateOf(true) }

  // Auto-dismiss notification banner after 30 seconds
  LaunchedEffect(uiState.statusBannerMessage) {
    if (uiState.statusBannerMessage != null) {
      delay(30_000L)
      onClearStatusBanner()
    }
  }

  Box(modifier = modifier.fillMaxSize().background(Color(0xFF0B0F14))) {
    // 1. Full-screen distortion-free CameraX PreviewView with Pinch-to-Zoom & Tap-to-Focus
    if (hasCameraPermission) {
      CameraXPreview(
        imageCapture = imageCapture,
        onCameraReady = { camera, initZoom, minZoom, maxZoom ->
          activeCamera = camera
          currentZoomRatio = initZoom
          minZoomRatio = minZoom
          maxZoomRatio = maxZoom
          hasFlashUnit = camera.cameraInfo.hasFlashUnit()
          isTorchEnabled = false
        },
        onZoomRatioChanged = { newRatio -> currentZoomRatio = newRatio },
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
        onOpenPhotosClick = { onOpenCustomerHub(uiState.activeCustomer?.id) },
        onOpenSettingsClick = onOpenSettingsSheet,
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
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Waiting for AI Vision analysis from cloud...",
                color = Color(0xFF9CA3AF),
                style = MaterialTheme.typography.labelSmall,
              )
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

    // 3. Bottom Zero-Click Shutter Bar + Camera Zoom Controls
    Column(
      modifier =
        Modifier.align(Alignment.BottomCenter)
          .fillMaxWidth()
          .navigationBarsPadding()
          .padding(bottom = 28.dp, start = 24.dp, end = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      if (hasCameraPermission) {
        CameraZoomSelectorBar(
          currentZoomRatio = currentZoomRatio,
          minZoomRatio = minZoomRatio,
          maxZoomRatio = maxZoomRatio,
          onSelectZoomRatio = { targetRatio ->
            val clamped = targetRatio.coerceIn(minZoomRatio, maxZoomRatio)
            currentZoomRatio = clamped
            activeCamera?.cameraControl?.setZoomRatio(clamped)
          },
        )
        Spacer(modifier = Modifier.height(14.dp))
      }

      Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
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

        if (hasCameraPermission && hasFlashUnit) {
          Box(
            contentAlignment = Alignment.Center,
            modifier =
              Modifier.align(Alignment.CenterEnd)
                .padding(end = 20.dp)
                .size(56.dp)
                .clip(CircleShape)
                .border(
                  width = 2.dp,
                  color = if (isTorchEnabled) Color(0xFFFACC15) else Color(0xFF4B5563),
                  shape = CircleShape,
                )
                .background(if (isTorchEnabled) Color(0xFFFACC15) else Color(0xCC111827))
                .clickable {
                  val nextTorch = !isTorchEnabled
                  isTorchEnabled = nextTorch
                  activeCamera?.cameraControl?.enableTorch(nextTorch)
                },
          ) {
            Icon(
              painter = painterResource(id = R.drawable.ic_flashlight),
              contentDescription = if (isTorchEnabled) "Turn Off Flashlight" else "Turn On Flashlight",
              tint = if (isTorchEnabled) Color(0xFF0B0F14) else Color.White,
              modifier = Modifier.size(26.dp),
            )
          }
        }
      }
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
              "No Customer Selected • Taking a photo will prompt you to choose one"
          },
        color = Color.White.copy(alpha = 0.9f),
        style = MaterialTheme.typography.labelMedium,
      )
    }
  }

  // 4. Two-Step Customer -> Location Selector Popup (works with or without GPS!)
  if (uiState.isCustomerPickerVisible) {
    CustomerTagListDialog(
      uiState = uiState,
      onSelectCustomerAndLocation = onSelectCustomerManually,
      onCreateCustomerFromPicker = { name, locLabel, useGps, address ->
        onCreateCustomer(name, locLabel, useGps, address)
      },
      onAddLocationFromPicker = { customerId, locLabel, useGps, address ->
        onAddAndSelectLocationForCustomer(customerId, locLabel, useGps, address)
      },
      onOpenSettingsClick = {
        onOpenSettingsSheet()
      },
      onDismiss = onCloseCustomerPicker,
    )
  }

  // 5. Full-Screen Customer Photos Gallery View (no drag-down dismissal when scrolling!)
  if (uiState.isCustomerHubVisible) {
    BackHandler(onBack = onCloseCustomerHub)
    Surface(
      modifier = Modifier.fillMaxSize(),
      color = Color(0xFF0E2243),
      contentColor = Color.White,
    ) {
      CustomerPhotosBottomSheet(
        uiState = uiState,
        onSelectHubCustomer = onSelectHubCustomer,
        onOpenSettingsClick = {
          onCloseCustomerHub()
          onOpenSettingsSheet()
        },
        onDeletePhotos = onDeletePhotos,
        onTriggerDemoAi = onTriggerDemoAi,
        onShowPartsForPhoto = onShowPartsForPhoto,
        onClose = onCloseCustomerHub,
      )
    }
  }

  // 6. Full-Screen Dedicated Settings View (Gear ⚙️: Customers, Locations, Search Sites, GPS Radius)
  if (uiState.isSettingsSheetVisible) {
    BackHandler(onBack = onCloseSettingsSheet)
    Surface(
      modifier = Modifier.fillMaxSize(),
      color = Color(0xFF0E2243),
      contentColor = Color.White,
    ) {
      AppSettingsBottomSheet(
        uiState = uiState,
        initialTab = if (uiState.isSearchSitesSettingsVisible) 1 else 0,
        onCreateCustomer = onCreateCustomer,
        onDeleteCustomer = onDeleteCustomer,
        onAddLocationToCustomer = onAddLocationToCustomer,
        onDeleteLocationFromCustomer = onDeleteLocationFromCustomer,
        onAddSearchSite = onAddSearchSite,
        onDeleteSearchSite = onDeleteSearchSite,
        onSetGeofenceRadius = onSetGeofenceRadius,
        onSignOutCompany = {
          onCloseSettingsSheet()
          onSignOutCompany()
        },
        onClose = onCloseSettingsSheet,
      )
    }
  }

  // 7. Full-Screen AI Part Identification View
  if (uiState.isBottomSheetVisible && uiState.detectedParts.isNotEmpty()) {
    BackHandler(onBack = onDismissBottomSheet)
    Surface(
      modifier = Modifier.fillMaxSize(),
      color = Color(0xFF111827),
      contentColor = Color.White,
    ) {
      val locLabel = uiState.activeCustomerLocation?.label?.let { " ($it)" }.orEmpty()
      AiPartAnalysisBottomSheet(
        customerName = (uiState.activeCustomer?.name ?: "Job Site Capture") + locLabel,
        parts = uiState.detectedParts,
        searchSites = uiState.searchSites,
        onOpenSearchSitesSettings = {
          onDismissBottomSheet()
          onOpenSearchSitesSettings()
        },
        onSearchPartOnSite = { site, query ->
          val fullUrl = site.buildSearchUrl(Uri.encode(query))
          val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl))
          context.startActivity(intent)
        },
        onClose = onDismissBottomSheet,
      )
    }
  }
}

@Composable
private fun GeofenceHeaderBadge(
  uiState: FieldCameraUiState,
  hasLocationPermission: Boolean,
  onOpenCustomerPicker: () -> Unit,
  onOpenPhotosClick: () -> Unit,
  onOpenSettingsClick: () -> Unit,
  onResetToAutoGps: () -> Unit,
) {
  val manualCustomer = uiState.manualSelectedCustomer
  val manualLocation = uiState.manualSelectedLocation
  val autoDetected = uiState.autoDetectedCustomer
  val closest = uiState.closestMatch
  val context = LocalContext.current
  val activeLocForDirections =
    if (manualCustomer != null) {
      manualLocation ?: manualCustomer.locations.firstOrNull()
    } else {
      null
    }

  val badgeBackground =
    when {
      manualCustomer != null -> Color(0xE6132D57)
      autoDetected != null -> Color(0xE60E2243)
      else -> Color(0xD90E2243)
    }
  val borderColor =
    when {
      manualCustomer != null -> Color(0xFFEE4D38)
      autoDetected != null -> Color(0xFF0F93E8)
      else -> Color(0xFF284B80)
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
      verticalArrangement = Arrangement.spacedBy(10.dp),
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
                manualCustomer != null -> "Selected: ${manualCustomer.name}"
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
                "Manual tag • $locLabel ($locAddr)"
              }
              autoDetected != null -> {
                val matchedLoc = uiState.autoDetectedLocation
                val locLabel = matchedLoc?.label ?: "Site"
                val locAddr = matchedLoc?.address ?: autoDetected.address
                val distLabel =
                  LocationTracker.formatDistanceImperial(closest?.distanceMeters ?: 0f)
                "$distLabel away • $locLabel ($locAddr)"
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

        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (activeLocForDirections != null) {
            FilledTonalButton(
              onClick = {
                openMapsDirections(
                  context = context,
                  latitude = activeLocForDirections.latitude,
                  longitude = activeLocForDirections.longitude,
                  address = activeLocForDirections.address,
                  label = activeLocForDirections.label,
                )
              },
              colors =
                ButtonDefaults.filledTonalButtonColors(
                  containerColor = Color(0xFF0F93E8),
                  contentColor = Color.White,
                ),
              contentPadding = PaddingValues(0.dp),
              modifier = Modifier.size(34.dp),
            ) {
              Icon(
                painter = painterResource(id = R.drawable.ic_directions),
                contentDescription = "Directions to Location",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
              )
            }
          }

          if (manualCustomer != null) {
            OutlinedButton(
              onClick = onResetToAutoGps,
              colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF93C5FD)),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
              modifier = Modifier.height(34.dp),
            ) {
              Text("Auto GPS", style = MaterialTheme.typography.labelSmall)
            }
          }

          // Gear Button for all Settings (Customers, Locations, Search Sites, Geofence Radius)
          FilledTonalButton(
            onClick = onOpenSettingsClick,
            colors =
              ButtonDefaults.filledTonalButtonColors(
                containerColor = Color(0xFF132D57),
                contentColor = Color.White,
              ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            modifier = Modifier.height(34.dp),
          ) {
            Text("⚙ Settings", style = MaterialTheme.typography.labelSmall)
          }
        }
      }

      // Clean 2-button bar: Select Customer (like Search Sites list) | View Photos
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Button(
          onClick = onOpenCustomerPicker,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = Color(0xFF132D57),
              contentColor = Color.White,
            ),
          modifier = Modifier.weight(1f).height(38.dp),
        ) {
          Text(
            text = "Select Customer",
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
          )
        }

        Button(
          onClick = onOpenPhotosClick,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = Color(0xFF0F93E8),
              contentColor = Color.White,
            ),
          modifier = Modifier.weight(1f).height(38.dp),
        ) {
          Text(
            text = "View Photos",
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
          )
        }
      }
    }
  }
}

/**
 * Two-Step Customer -> Location Selector Dialog:
 * Step 1: Pick a Customer (or "+ New Customer")
 * Step 2: Pick which Site Location of that Customer to tag (or "+ New Location for [Customer]")
 * Works seamlessly even when the device has no GPS signal!
 */
@Composable
private fun CustomerTagListDialog(
  uiState: FieldCameraUiState,
  onSelectCustomerAndLocation: (Long?, Long?) -> Unit,
  onCreateCustomerFromPicker: (String, String, Boolean, String) -> Unit,
  onAddLocationFromPicker: (Long, String, Boolean, String) -> Unit,
  onOpenSettingsClick: () -> Unit,
  onDismiss: () -> Unit,
) {
  var showNewCustomerDialog by remember { mutableStateOf(false) }
  var selectedCustomerForLocationStep by remember { mutableStateOf<Customer?>(null) }
  var showNewLocationForSelectedCustomer by remember { mutableStateOf(false) }
  val context = LocalContext.current

  // Keep selectedCustomerForLocationStep in sync with latest customers list
  val activeStepCustomer =
    selectedCustomerForLocationStep?.let { picked ->
      uiState.customers.find { it.id == picked.id } ?: picked
    }

  if (showNewCustomerDialog) {
    AddCustomerDialog(
      hasCurrentGps = uiState.currentLocation != null,
      isSaving = uiState.isSavingCustomerOrLocation,
      onConfirm = { name, locLabel, useGps, address ->
        showNewCustomerDialog = false
        onCreateCustomerFromPicker(name, locLabel, useGps, address)
      },
      onDismiss = { showNewCustomerDialog = false },
    )
    return
  }

  if (showNewLocationForSelectedCustomer && activeStepCustomer != null) {
    AddLocationDialog(
      customerName = activeStepCustomer.name,
      currentLocation = uiState.currentLocation,
      isSaving = uiState.isSavingCustomerOrLocation,
      onConfirm = { label, useGps, address ->
        showNewLocationForSelectedCustomer = false
        onAddLocationFromPicker(activeStepCustomer.id, label, useGps, address)
      },
      onDismiss = { showNewLocationForSelectedCustomer = false },
    )
    return
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = Color(0xFF132D57),
    titleContentColor = Color.White,
    textContentColor = Color.White,
    title = {
      Column {
        if (activeStepCustomer == null) {
          Text(
            text =
              if (uiState.hasPendingPhotoAwaitingCustomer) {
                "Step 1: Select Customer for Photo"
              } else {
                "Step 1: Select Customer"
              },
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "Choose a customer below, then pick their site location",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF94A3B8),
          )
        } else {
          Text(
            text = "Step 2: Select Location",
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "Customer: ${activeStepCustomer.name}",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF38BDF8),
            fontWeight = FontWeight.SemiBold,
          )
        }
      }
    },
    text = {
      if (activeStepCustomer == null) {
        // STEP 1: Select a Customer
        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          // 0. "+ New Customer" option right at the top
          item {
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = Color(0xFF1A3A6E),
              modifier =
                Modifier.fillMaxWidth()
                  .border(1.dp, Color(0xFFEE4D38), RoundedCornerShape(10.dp))
                  .clickable { showNewCustomerDialog = true },
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "+ New Customer",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                  )
                  Text(
                    text =
                      if (uiState.currentLocation != null) {
                        "Create customer at current GPS or by street address"
                      } else {
                        "Create customer & add location by street address"
                      },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFA7F3D0),
                  )
                }
                Text(
                  text = "Add →",
                  color = Color(0xFF34D399),
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
          }

          // 1. Auto-Detect via GPS option (only when GPS is available and not waiting on an unmatched photo)
          if (uiState.currentLocation != null &&
              (!uiState.hasPendingPhotoAwaitingCustomer || uiState.autoDetectedCustomer != null)
          ) {
            item {
              val isAutoMode = uiState.manualCustomerOverrideId == null
              val radiusLabel = LocationTracker.formatDistanceImperial(uiState.geofenceRadiusMeters)
              val autoDetectedName =
                uiState.autoDetectedCustomer?.let { cust ->
                  val locName = uiState.autoDetectedLocation?.label?.let { " • $it" }.orEmpty()
                  "${cust.name}$locName"
                } ?: "No customer within $radiusLabel"

              Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isAutoMode) Color(0xFF065F46) else Color(0xFF0E2243),
                modifier =
                  Modifier.fillMaxWidth()
                    .clickable { onSelectCustomerAndLocation(null, null) },
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "Auto-Detect via GPS",
                      fontWeight = FontWeight.Bold,
                      color = Color.White,
                    )
                    Text(
                      text = autoDetectedName,
                      style = MaterialTheme.typography.labelSmall,
                      color = Color(0xFFD1D5DB),
                    )
                  }
                  Text(
                    text = if (isAutoMode) "Active ✓" else "Use →",
                    color = Color(0xFF34D399),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                  )
                }
              }
            }
          }

          // 2. Customer list — tapping a customer moves to Step 2 (Select Location for that customer)
          items(uiState.customers, key = { it.id }) { customer ->
            val isCurrentCustomer = uiState.manualCustomerOverrideId == customer.id
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = if (isCurrentCustomer) Color(0xFF1E3A8A) else Color(0xFF0E2243),
              modifier =
                Modifier.fillMaxWidth()
                  .clickable { selectedCustomerForLocationStep = customer },
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                  Text(
                    text = customer.name,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                  )
                  Text(
                    text =
                      if (customer.locations.isEmpty()) {
                        "No saved locations yet • Tap to add or select"
                      } else {
                        "${customer.locations.size} site location(s) • Tap to choose location"
                      },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF38BDF8),
                  )
                }
                Text(
                  text = "Locations →",
                  color = Color(0xFF34D399),
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
          }
        }
      } else {
        // STEP 2: Select a Location under `activeStepCustomer`
        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          // "+ Add New Location" for this customer
          item {
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = Color(0xFF1A3A6E),
              modifier =
                Modifier.fillMaxWidth()
                  .border(1.dp, Color(0xFFEE4D38), RoundedCornerShape(10.dp))
                  .clickable { showNewLocationForSelectedCustomer = true },
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "+ Add New Location",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                  )
                  Text(
                    text = "Add a site location for ${activeStepCustomer.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFA7F3D0),
                  )
                }
                Text(
                  text = "Add →",
                  color = Color(0xFF34D399),
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
          }

          if (activeStepCustomer.locations.isEmpty()) {
            item {
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF0E2243),
                modifier =
                  Modifier.fillMaxWidth()
                    .clickable {
                      onSelectCustomerAndLocation(activeStepCustomer.id, null)
                    },
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "Default Site (Unassigned Location)",
                      fontWeight = FontWeight.Bold,
                      color = Color.White,
                    )
                    Text(
                      text = activeStepCustomer.address.ifBlank { "Tag customer without specific site" },
                      style = MaterialTheme.typography.labelSmall,
                      color = Color(0xFF94A3B8),
                    )
                  }
                  Text(
                    text = "Select →",
                    color = Color(0xFF38BDF8),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                  )
                }
              }
            }
          } else {
            items(activeStepCustomer.locations, key = { it.id }) { loc ->
              val isSelected =
                uiState.manualCustomerOverrideId == activeStepCustomer.id &&
                  uiState.manualLocationOverrideId == loc.id
              val distMeters =
                uiState.currentLocation?.let { deviceLoc ->
                  LocationTracker.distanceBetween(deviceLoc, loc.latitude, loc.longitude)
                }

              Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) Color(0xFF1E3A8A) else Color(0xFF0E2243),
                modifier =
                  Modifier.fillMaxWidth()
                    .clickable {
                      onSelectCustomerAndLocation(activeStepCustomer.id, loc.id)
                    },
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                      text =
                        buildString {
                          append(loc.label)
                          if (distMeters != null) {
                            append(" (${LocationTracker.formatDistanceImperial(distMeters)})")
                          }
                        },
                      fontWeight = FontWeight.Bold,
                      color = Color.White,
                    )
                    Text(
                      text = loc.address,
                      style = MaterialTheme.typography.labelSmall,
                      color = Color(0xFF38BDF8),
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                  Text(
                    text = if (isSelected) "Selected ✓" else "Select →",
                    color = Color(0xFF34D399),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                  )
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onOpenSettingsClick) {
        Text("⚙ Settings", color = Color(0xFF38BDF8))
      }
    },
    dismissButton = {
      if (activeStepCustomer != null) {
        TextButton(onClick = { selectedCustomerForLocationStep = null }) {
          Text("← Back to Customers", color = Color(0xFF38BDF8))
        }
      } else {
        TextButton(onClick = onDismiss) {
          Text("Cancel", color = Color(0xFF94A3B8))
        }
      }
    },
  )
}

/**
 * Clean Customer Photos Bottom Sheet — dedicated purely to viewing photos by customer,
 * with a clean popup list button to switch which customer you're viewing and a Gear button for Settings.
 */
@Composable
private fun CustomerPhotosBottomSheet(
  uiState: FieldCameraUiState,
  onSelectHubCustomer: (Long) -> Unit,
  onOpenSettingsClick: () -> Unit,
  onDeletePhotos: (List<CapturedPhotoRow>) -> Unit,
  onTriggerDemoAi: (Long?) -> Unit,
  onShowPartsForPhoto: (CapturedPhotoRow) -> Unit,
  onClose: () -> Unit,
) {
  val selectedCustomer = uiState.selectedHubCustomer
  var showPhotoCustomerPicker by remember { mutableStateOf(false) }

  Column(
    modifier =
      Modifier.fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // Top Header: Back to Camera + Title + Gear Settings button
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedButton(
        onClick = onClose,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        modifier = Modifier.height(36.dp),
      ) {
        Text("← Camera", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
      }
      Text(
        text = "Customer Photos",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = Color.White,
      )
      FilledTonalButton(
        onClick = onOpenSettingsClick,
        colors =
          ButtonDefaults.filledTonalButtonColors(
            containerColor = Color(0xFF132D57),
            contentColor = Color.White,
          ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        modifier = Modifier.height(36.dp),
      ) {
        Text("⚙ Settings", style = MaterialTheme.typography.labelMedium)
      }
    }

    // Clean Button to pull up the Customer List Popup (just like Search Sites!)
    Surface(
      shape = RoundedCornerShape(12.dp),
      color = Color(0xFF132D57),
      modifier =
        Modifier.fillMaxWidth()
          .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(12.dp))
          .clickable { showPhotoCustomerPicker = true },
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Viewing Customer",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF94A3B8),
          )
          Text(
            text = selectedCustomer?.name ?: "Select a Customer",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
          )
        }
        Text(
          text = "Change Customer ▾",
          color = Color(0xFF38BDF8),
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
        )
      }
    }

    if (selectedCustomer != null) {
      CustomerPhotosSection(
        customer = selectedCustomer,
        photos = uiState.customerPhotos,
        isLoading = uiState.isLoadingCustomerPhotos,
        photoBitmapsCache = uiState.photoBitmapsCache,
        onDeletePhotos = onDeletePhotos,
        onTriggerDemoAi = onTriggerDemoAi,
        onShowPartsForPhoto = onShowPartsForPhoto,
        modifier = Modifier.weight(1f),
      )
    }
  }

  // Popup list (like Search Sites) to pick which customer's photos to view
  if (showPhotoCustomerPicker) {
    AlertDialog(
      onDismissRequest = { showPhotoCustomerPicker = false },
      containerColor = Color(0xFF132D57),
      titleContentColor = Color.White,
      textContentColor = Color.White,
      title = { Text("Choose Customer to View Photos", fontWeight = FontWeight.Bold) },
      text = {
        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          items(uiState.customers, key = { it.id }) { customer ->
            val isSelected = selectedCustomer?.id == customer.id
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = if (isSelected) Color(0xFF1E3A8A) else Color(0xFF0E2243),
              modifier =
                Modifier.fillMaxWidth()
                  .clickable {
                    showPhotoCustomerPicker = false
                    onSelectHubCustomer(customer.id)
                  },
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = customer.name,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                  )
                  Text(
                    text = "${customer.locations.size} site location(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8),
                  )
                }
                Text(
                  text = if (isSelected) "Viewing ✓" else "View →",
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
        TextButton(onClick = { showPhotoCustomerPicker = false }) {
          Text("Close", color = Color(0xFF94A3B8))
        }
      },
    )
  }
}

/**
 * Dedicated Settings Sheet (Gear ⚙️) for all configuration:
 * - Tab 0: Customers & Site Locations (Add Customer, Remove Customer, Add/Remove Site Locations)
 * - Tab 1: Search Sites (Add/Remove online part search websites)
 * - Tab 2: Field Preferences (GPS Auto-Detect Geofence Radius)
 */
@Composable
private fun AppSettingsBottomSheet(
  uiState: FieldCameraUiState,
  initialTab: Int = 0,
  onCreateCustomer: (String, String, Boolean, String) -> Unit,
  onDeleteCustomer: (Long) -> Unit,
  onAddLocationToCustomer: (Long, String, Boolean, String) -> Unit,
  onAddAndSelectLocationForCustomer: (Long, String, Boolean, String) -> Unit = onAddLocationToCustomer,
  onDeleteLocationFromCustomer: (Long, String) -> Unit,
  onAddSearchSite: (String, String) -> Unit,
  onDeleteSearchSite: (Long, String) -> Unit,
  onSetGeofenceRadius: (Float) -> Unit,
  onSignOutCompany: () -> Unit,
  onClose: () -> Unit,
) {
  var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
  var showNewCustomerDialog by remember { mutableStateOf(false) }
  var customerForNewLocation by remember { mutableStateOf<Customer?>(null) }
  var customerPendingDeletion by remember { mutableStateOf<Customer?>(null) }
  var locationPendingDeletion by remember { mutableStateOf<Pair<Long, String>?>(null) }
  val context = LocalContext.current

  var newSiteName by remember { mutableStateOf("") }
  var newSiteUrl by remember { mutableStateOf("") }

  Column(
    modifier =
      Modifier.fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedButton(
        onClick = onClose,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        modifier = Modifier.height(36.dp),
      ) {
        Text("← Camera", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
      }
      Text(
        text = "⚙ JobSight Settings",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = Color.White,
      )
      Spacer(modifier = Modifier.width(64.dp))
    }

    SecondaryTabRow(
      selectedTabIndex = selectedTab,
      containerColor = Color(0xFF132D57),
      contentColor = Color.White,
    ) {
      Tab(
        selected = selectedTab == 0,
        onClick = { selectedTab = 0 },
        text = { Text("Customers (${uiState.customers.size})") },
      )
      Tab(
        selected = selectedTab == 1,
        onClick = { selectedTab = 1 },
        text = { Text("Search Sites (${uiState.searchSites.size})") },
      )
      Tab(
        selected = selectedTab == 2,
        onClick = { selectedTab = 2 },
        text = { Text("GPS & App") },
      )
    }

    when (selectedTab) {
      0 -> {
        // Tab 0: Customers & Locations Management
        Button(
          onClick = { showNewCustomerDialog = true },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = Color(0xFF0F93E8),
              contentColor = Color.White,
            ),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("+ Add Customer", fontWeight = FontWeight.Bold)
        }

        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f),
        ) {
          items(uiState.customers, key = { it.id }) { customer ->
            Card(
              colors = CardDefaults.cardColors(containerColor = Color(0xFF132D57)),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = customer.name,
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold,
                      color = Color.White,
                    )
                    Text(
                      text = "${customer.locations.size} saved site location(s)",
                      style = MaterialTheme.typography.bodySmall,
                      color = Color(0xFF94A3B8),
                    )
                  }
                  Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    FilledTonalButton(
                      onClick = { customerForNewLocation = customer },
                      colors =
                        ButtonDefaults.filledTonalButtonColors(
                          containerColor = Color(0xFF0F93E8),
                          contentColor = Color.White,
                        ),
                      contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                      modifier = Modifier.height(32.dp),
                    ) {
                      Text("+ Location", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                      onClick = { customerPendingDeletion = customer },
                      colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF87171)),
                      contentPadding = PaddingValues(0.dp),
                      modifier = Modifier.size(32.dp),
                    ) {
                      Text("✕", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                  }
                }

                // List each site location under this customer with a Directions button and an ✕ delete button
                customer.locations.forEach { loc ->
                  Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0E2243),
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                      Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                          text = loc.label,
                          fontWeight = FontWeight.SemiBold,
                          color = Color.White,
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
                      Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                      ) {
                        FilledTonalButton(
                          onClick = {
                            openMapsDirections(
                              context = context,
                              latitude = loc.latitude,
                              longitude = loc.longitude,
                              address = loc.address,
                              label = "${customer.name} - ${loc.label}",
                            )
                          },
                          colors =
                            ButtonDefaults.filledTonalButtonColors(
                              containerColor = Color(0xFF0F93E8),
                              contentColor = Color.White,
                            ),
                          contentPadding = PaddingValues(0.dp),
                          modifier = Modifier.size(28.dp),
                        ) {
                          Icon(
                            painter = painterResource(id = R.drawable.ic_directions),
                            contentDescription = "Directions to ${loc.label}",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                          )
                        }
                        Box(
                          contentAlignment = Alignment.Center,
                          modifier =
                            Modifier.size(28.dp)
                              .clip(CircleShape)
                              .clickable { locationPendingDeletion = loc.id to loc.label },
                        ) {
                          Text(
                            text = "✕",
                            color = Color(0xFFF87171),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                          )
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }

      1 -> {
        // Tab 1: Search Sites Settings
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = Color(0xFF132D57),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = "Add a Part Search Website",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = Color(0xFF38BDF8),
            )
            OutlinedTextField(
              value = newSiteName,
              onValueChange = { newSiteName = it },
              label = { Text("Website Name (e.g. Ferguson, Johnstone)") },
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
                onAddSearchSite(newSiteName, newSiteUrl)
                newSiteName = ""
                newSiteUrl = ""
              },
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = Color(0xFF0F93E8),
                  contentColor = Color.White,
                ),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("+ Add Search Site", fontWeight = FontWeight.Bold)
            }
          }
        }

        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.weight(1f),
        ) {
          items(uiState.searchSites, key = { it.id }) { site ->
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = Color(0xFF132D57),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                  Text(
                    text = site.name,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                  )
                  Text(
                    text = site.urlTemplate,
                    color = Color(0xFF94A3B8),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                OutlinedButton(
                  onClick = { onDeleteSearchSite(site.id, site.name) },
                  colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF87171)),
                  contentPadding = PaddingValues(0.dp),
                  modifier = Modifier.size(32.dp),
                ) {
                  Text("✕", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }
      }

      else -> {
        // Tab 2: GPS & App Settings (Company Account + Geofence Radius)
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = Color(0xFF132D57),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = "HVAC Company Account",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = Color.White,
            )
            Text(
              text =
                "Signed in as: ${uiState.companySession?.companyName ?: "Active Company"}",
              style = MaterialTheme.typography.bodyMedium,
              color = Color(0xFF38BDF8),
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text =
                "All customers, locations, and photos are privately isolated to your company account.",
              style = MaterialTheme.typography.bodySmall,
              color = Color(0xFF94A3B8),
            )
            OutlinedButton(
              onClick = onSignOutCompany,
              colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEE4D38)),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("Sign Out / Switch Company", fontWeight = FontWeight.Bold)
            }
          }
        }

        Surface(
          shape = RoundedCornerShape(12.dp),
          color = Color(0xFF132D57),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = "GPS Auto-Detect Geofence Radius",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = Color.White,
            )
            Text(
              text =
                "Maximum distance from a saved customer site location for JobSight to automatically tag that customer:",
              style = MaterialTheme.typography.bodySmall,
              color = Color(0xFF94A3B8),
            )
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              val presets =
                listOf(
                  45.72f to "150 ft",
                  100f to "325 ft",
                  152.4f to "500 ft",
                  304.8f to "1000 ft",
                  804.67f to "0.5 mi",
                )
              presets.forEach { (radiusMeters, label) ->
                val isSelected = kotlin.math.abs(uiState.geofenceRadiusMeters - radiusMeters) < 2f
                FilterChip(
                  selected = isSelected,
                  onClick = { onSetGeofenceRadius(radiusMeters) },
                  label = {
                    Text(
                      text = label,
                      color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                    )
                  },
                  colors =
                    FilterChipDefaults.filterChipColors(
                      selectedContainerColor = Color(0xFF0F93E8),
                      containerColor = Color(0xFF0E2243),
                    ),
                )
              }
            }
          }
        }
      }
    }
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

  customerForNewLocation?.let { targetCustomer ->
    AddLocationDialog(
      customerName = targetCustomer.name,
      currentLocation = uiState.currentLocation,
      isSaving = uiState.isSavingCustomerOrLocation,
      onConfirm = { label, useGps, address ->
        customerForNewLocation = null
        onAddLocationToCustomer(targetCustomer.id, label, useGps, address)
      },
      onDismiss = { customerForNewLocation = null },
    )
  }

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

  locationPendingDeletion?.let { (locId, locLabel) ->
    AlertDialog(
      onDismissRequest = { locationPendingDeletion = null },
      title = { Text("Remove Site Location?") },
      text = { Text("Are you sure you want to remove the location '$locLabel'?") },
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
}

@Composable
private fun CustomerPhotosSection(
  customer: Customer,
  photos: List<CapturedPhotoRow>,
  isLoading: Boolean,
  photoBitmapsCache: Map<String, ByteArray>,
  onDeletePhotos: (List<CapturedPhotoRow>) -> Unit,
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
          "No photos taken for ${customer.name} yet.\nReturn to the camera and press the shutter to capture one!",
        color = Color(0xFF94A3B8),
        style = MaterialTheme.typography.bodyMedium,
      )
    }
    return
  }

  val sortedPhotos = remember(photos) { photos.sortedByDescending { it.id } }
  var isSelectionMode by remember(customer.id) { mutableStateOf(false) }
  var selectedPhotoIds by remember(customer.id, sortedPhotos.size) { mutableStateOf(emptySet<Long>()) }
  var photosPendingDeleteConfirmation by remember { mutableStateOf<List<CapturedPhotoRow>?>(null) }
  var photoPendingViewer by remember { mutableStateOf<CapturedPhotoRow?>(null) }

  var searchQuery by remember(customer.id) { mutableStateOf("") }
  var selectedLocationId by remember(customer.id) { mutableStateOf<Long?>(null) }

  val filteredPhotos =
    remember(sortedPhotos, selectedLocationId, searchQuery) {
      sortedPhotos.filter { photo ->
        val matchesLoc =
          selectedLocationId == null || photo.customerLocationId == selectedLocationId
        if (!matchesLoc) return@filter false

        if (searchQuery.isBlank()) return@filter true

        val query = searchQuery.trim().lowercase()
        val matchedLoc = customer.locations.find { it.id == photo.customerLocationId }
        val locMatch =
          matchedLoc?.label?.lowercase()?.contains(query) == true ||
            matchedLoc?.address?.lowercase()?.contains(query) == true
        val dateMatch = formatCapturedDate(photo.capturedAt).lowercase().contains(query)
        val parts = AnalysisJsonParser.parseParts(photo.aiAnalysisJson)
        val partMatch =
          parts.any { part ->
            part.partName.lowercase().contains(query) ||
              part.modelNumber?.lowercase()?.contains(query) == true ||
              part.searchQuery.lowercase().contains(query)
          }
        val rawJsonMatch = photo.aiAnalysisJson?.toString()?.lowercase()?.contains(query) == true

        locMatch || dateMatch || partMatch || rawJsonMatch
      }
    }

  val listState = rememberLazyListState()
  LaunchedEffect(customer.id, filteredPhotos.firstOrNull()?.id) {
    if (filteredPhotos.isNotEmpty()) {
      listState.scrollToItem(0)
    }
  }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    // 1. Search Bar for Parts, Model #, Site Location, or Date
    OutlinedTextField(
      value = searchQuery,
      onValueChange = { searchQuery = it },
      placeholder = { Text("Search parts, model #, location, date...") },
      leadingIcon = {
        Icon(
          painter = painterResource(id = R.drawable.ic_search),
          contentDescription = "Search Photos",
          tint = Color(0xFF94A3B8),
          modifier = Modifier.size(18.dp),
        )
      },
      trailingIcon = {
        if (searchQuery.isNotEmpty()) {
          Box(
            contentAlignment = Alignment.Center,
            modifier =
              Modifier.size(28.dp)
                .clip(CircleShape)
                .clickable { searchQuery = "" },
          ) {
            Text("✕", color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
          }
        }
      },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )

    // 2. Horizontal Site Location Filter Chips (when customer has site locations)
    if (customer.locations.isNotEmpty()) {
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        item {
          val isAllSelected = selectedLocationId == null
          FilterChip(
            selected = isAllSelected,
            onClick = { selectedLocationId = null },
            label = { Text("All Sites (${photos.size})") },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFF0F93E8),
                selectedLabelColor = Color.White,
                containerColor = Color(0xFF132D57),
                labelColor = Color(0xFFCBD5E1),
              ),
          )
        }
        items(customer.locations, key = { it.id }) { loc ->
          val isLocSelected = selectedLocationId == loc.id
          val locPhotoCount = photos.count { it.customerLocationId == loc.id }
          FilterChip(
            selected = isLocSelected,
            onClick = {
              selectedLocationId = if (isLocSelected) null else loc.id
            },
            label = { Text("${loc.label} ($locPhotoCount)") },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFF0F93E8),
                selectedLabelColor = Color.White,
                containerColor = Color(0xFF132D57),
                labelColor = Color(0xFFCBD5E1),
              ),
          )
        }
      }
    }

    // 3. Bulk Selection / Delete Action Bar
    Surface(
      shape = RoundedCornerShape(10.dp),
      color = Color(0xFF132D57),
      modifier = Modifier.fillMaxWidth(),
    ) {
      if (!isSelectionMode) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text =
              if (filteredPhotos.size == sortedPhotos.size) {
                "${sortedPhotos.size} photo(s)"
              } else {
                "Showing ${filteredPhotos.size} of ${sortedPhotos.size} photo(s)"
              },
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFCBD5E1),
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
              onClick = {
                isSelectionMode = true
                selectedPhotoIds = emptySet()
              },
              colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
              modifier = Modifier.height(32.dp),
            ) {
              Text("Select", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
              onClick = { photosPendingDeleteConfirmation = filteredPhotos },
              colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEE4D38)),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
              modifier = Modifier.height(32.dp),
            ) {
              Text(
                if (filteredPhotos.size < sortedPhotos.size) "Delete Filtered" else "Delete All",
                style = MaterialTheme.typography.labelSmall,
              )
            }
          }
        }
      } else {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          val allSelected =
            filteredPhotos.isNotEmpty() && selectedPhotoIds.containsAll(filteredPhotos.map { it.id })
          TextButton(
            onClick = {
              selectedPhotoIds =
                if (allSelected) emptySet() else filteredPhotos.map { it.id }.toSet()
            },
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
          ) {
            Text(
              text = if (allSelected) "Clear All" else "Select All (${filteredPhotos.size})",
              color = Color(0xFF38BDF8),
              style = MaterialTheme.typography.labelMedium,
            )
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              enabled = selectedPhotoIds.isNotEmpty(),
              onClick = {
                val chosen = sortedPhotos.filter { it.id in selectedPhotoIds }
                if (chosen.isNotEmpty()) {
                  photosPendingDeleteConfirmation = chosen
                }
              },
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = Color(0xFFEE4D38),
                  contentColor = Color.White,
                ),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
              modifier = Modifier.height(32.dp),
            ) {
              Text(
                text = "Delete (${selectedPhotoIds.size})",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
              )
            }
            TextButton(
              onClick = {
                isSelectionMode = false
                selectedPhotoIds = emptySet()
              },
              contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            ) {
              Text("Cancel", color = Color(0xFF94A3B8), style = MaterialTheme.typography.labelSmall)
            }
          }
        }
      }
    }

    if (filteredPhotos.isEmpty()) {
      Box(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = "No photos match your search or filter.\nTry clearing the search or location filter.",
          color = Color(0xFF94A3B8),
          style = MaterialTheme.typography.bodyMedium,
          textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
      }
    } else {
      LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        items(filteredPhotos, key = { it.id }) { photo ->
          val isPhotoSelected = photo.id in selectedPhotoIds
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
          val formattedDate = remember(photo.capturedAt) { formatCapturedDate(photo.capturedAt) }

          Card(
            colors =
              CardDefaults.cardColors(
                containerColor =
                  if (isPhotoSelected) Color(0xFF1A3A6E) else Color(0xFF132D57)
              ),
            shape = RoundedCornerShape(14.dp),
            modifier =
              Modifier.fillMaxWidth()
                .then(
                  if (isPhotoSelected) {
                    Modifier.border(2.dp, Color(0xFFEE4D38), RoundedCornerShape(14.dp))
                  } else {
                    Modifier
                  }
                )
                .clickable {
                  if (isSelectionMode) {
                    selectedPhotoIds =
                      if (isPhotoSelected) selectedPhotoIds - photo.id
                      else selectedPhotoIds + photo.id
                  } else {
                    photoPendingViewer = photo
                  }
                },
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
                    contentDescription = "Captured Photo on $formattedDate • Tap to Zoom",
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

                // Tap to view full screen badge
                if (!isSelectionMode) {
                  Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xCC0E2243),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                  ) {
                    Text(
                      text = "🔍 Tap to Zoom",
                      color = Color.White,
                      style = MaterialTheme.typography.labelSmall,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                  }
                }

                if (isSelectionMode) {
                  Surface(
                    shape = CircleShape,
                    color = Color(0xD90E2243),
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                  ) {
                    Checkbox(
                      checked = isPhotoSelected,
                      onCheckedChange = { checked ->
                        selectedPhotoIds =
                          if (checked) selectedPhotoIds + photo.id
                          else selectedPhotoIds - photo.id
                      },
                      colors =
                        CheckboxDefaults.colors(
                          checkedColor = Color(0xFFEE4D38),
                          uncheckedColor = Color.White,
                        ),
                    )
                  }
                }
              }

              Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF0E2243),
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
                    text = formattedDate,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                  )
                }

                if (parts.isNotEmpty()) {
                  Button(
                    onClick = { onShowPartsForPhoto(photo) },
                    colors =
                      ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0F93E8),
                        contentColor = Color.White,
                      ),
                  ) {
                    Text("View ${parts.size} AI Part(s)", style = MaterialTheme.typography.labelSmall)
                  }
                } else if (photo.imageUrl.startsWith("file://")) {
                  Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1A3A6E),
                  ) {
                    Text(
                      text = "Queued Offline",
                      color = Color(0xFF56B7F7),
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                  }
                } else {
                  Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF334155),
                  ) {
                    Text(
                      text = "Pending AI ID",
                      color = Color(0xFFCBD5E1),
                      style = MaterialTheme.typography.labelSmall,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  // Full-Screen Pinch-to-Zoom Photo Viewer Dialog
  photoPendingViewer?.let { photo ->
    FullScreenPhotoViewerDialog(
      photo = photo,
      customer = customer,
      bitmapBytes = photoBitmapsCache[photo.imageUrl],
      onShowParts = {
        photoPendingViewer = null
        onShowPartsForPhoto(photo)
      },
      onDismiss = { photoPendingViewer = null },
    )
  }

  photosPendingDeleteConfirmation?.let { toDelete ->
    AlertDialog(
      onDismissRequest = { photosPendingDeleteConfirmation = null },
      containerColor = Color(0xFF132D57),
      titleContentColor = Color.White,
      textContentColor = Color.White,
      title = { Text("Delete ${toDelete.size} Photo(s)?", fontWeight = FontWeight.Bold) },
      text = {
        Text(
          "Are you sure you want to permanently delete ${toDelete.size} photo(s) from ${customer.name}?"
        )
      },
      confirmButton = {
        Button(
          onClick = {
            photosPendingDeleteConfirmation = null
            isSelectionMode = false
            selectedPhotoIds = emptySet()
            onDeletePhotos(toDelete)
          },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = Color(0xFFEE4D38),
              contentColor = Color.White,
            ),
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { photosPendingDeleteConfirmation = null }) {
          Text("Cancel", color = Color(0xFF94A3B8))
        }
      },
    )
  }
}

private fun formatCapturedDate(isoTimestamp: String?): String {
  if (isoTimestamp.isNullOrBlank()) return "Just now"
  return try {
    val cleanIso = isoTimestamp.take(19).replace("T", " ")
    val parser = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).apply {
      timeZone = java.util.TimeZone.getTimeZone("UTC")
    }
    val parsedDate = parser.parse(cleanIso)
    if (parsedDate != null) {
      val formatter =
        java.text.SimpleDateFormat("MMM d, yyyy • h:mm a", java.util.Locale.US).apply {
          timeZone = java.util.TimeZone.getDefault()
        }
      formatter.format(parsedDate)
    } else {
      cleanIso
    }
  } catch (_: Exception) {
    isoTimestamp.take(16).replace("T", " ")
  }
}

/**
 * Full-screen Pinch-to-Zoom & Pan Photo Viewer:
 * - Double-tap to quickly zoom in (2.5x) or reset (1.0x)
 * - Smooth two-finger pinch-to-zoom (1.0x to 5.0x) with pan dragging
 * - Displays local capture timestamp, site location label, and one-tap button to view AI detected parts
 */
@Composable
private fun FullScreenPhotoViewerDialog(
  photo: CapturedPhotoRow,
  customer: Customer,
  bitmapBytes: ByteArray?,
  onShowParts: () -> Unit,
  onDismiss: () -> Unit,
) {
  val imageBitmap =
    remember(bitmapBytes) {
      bitmapBytes?.let { bytes ->
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
      }
    }
  val formattedDate = remember(photo.capturedAt) { formatCapturedDate(photo.capturedAt) }
  val matchedLoc =
    customer.locations.find { it.id == photo.customerLocationId }
      ?: customer.locations.firstOrNull()
  val parts = remember(photo.aiAnalysisJson) { AnalysisJsonParser.parseParts(photo.aiAnalysisJson) }

  var scale by remember { mutableFloatStateOf(1f) }
  var offset by remember { mutableStateOf(Offset.Zero) }

  val transformableState =
    rememberTransformableState { zoomChange, offsetChange, _ ->
      val newScale = (scale * zoomChange).coerceIn(1f, 5f)
      scale = newScale
      offset =
        if (newScale > 1f) {
          offset + offsetChange
        } else {
          Offset.Zero
        }
    }

  Dialog(
    onDismissRequest = onDismiss,
    properties =
      DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false,
      ),
  ) {
    BackHandler(onBack = onDismiss)

    Surface(
      modifier = Modifier.fillMaxSize(),
      color = Color(0xFF070B10),
      contentColor = Color.White,
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        // High-res zoomable & pannable image area
        Box(
          modifier =
            Modifier.fillMaxSize()
              .pointerInput(Unit) {
                detectTapGestures(
                  onDoubleTap = {
                    if (scale > 1.2f) {
                      scale = 1f
                      offset = Offset.Zero
                    } else {
                      scale = 2.5f
                    }
                  }
                )
              }
              .transformable(state = transformableState),
          contentAlignment = Alignment.Center,
        ) {
          if (imageBitmap != null) {
            Image(
              bitmap = imageBitmap,
              contentDescription = "Full Screen Photo $formattedDate",
              contentScale = ContentScale.Fit,
              modifier =
                Modifier.fillMaxSize()
                  .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                  },
            )
          } else {
            CircularProgressIndicator(color = Color(0xFF38BDF8))
          }
        }

        // Top translucent action bar
        Surface(
          color = Color(0xCC0E2243),
          modifier =
            Modifier.align(Alignment.TopCenter)
              .fillMaxWidth()
              .statusBarsPadding(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            OutlinedButton(
              onClick = onDismiss,
              colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
              modifier = Modifier.height(34.dp),
            ) {
              Text("← Back", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }

            Column(
              modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text(
                text = customer.name,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = matchedLoc?.label ?: "Site Location",
                color = Color(0xFF38BDF8),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }

            if (parts.isNotEmpty()) {
              Button(
                onClick = onShowParts,
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0F93E8),
                    contentColor = Color.White,
                  ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(34.dp),
              ) {
                Text(
                  "${parts.size} Part(s)",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                )
              }
            } else {
              Spacer(modifier = Modifier.width(54.dp))
            }
          }
        }

        // Bottom translucent info & zoom hint bar
        Surface(
          color = Color(0xCC0E2243),
          modifier =
            Modifier.align(Alignment.BottomCenter)
              .fillMaxWidth()
              .navigationBarsPadding(),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = formattedDate,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
              )
              matchedLoc?.address?.let { addr ->
                if (addr.isNotBlank()) {
                  Text(
                    text = addr,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }

            Text(
              text =
                if (scale > 1.05f) "${"%.1f".format(scale)}x • Double-tap resets"
                else "Pinch or double-tap to zoom",
              style = MaterialTheme.typography.labelSmall,
              color = Color(0xFF38BDF8),
            )
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
  var pickedMapLat by remember { mutableStateOf<Double?>(null) }
  var pickedMapLng by remember { mutableStateOf<Double?>(null) }
  var showMapPicker by remember { mutableStateOf(false) }

  if (showMapPicker) {
    InteractiveMapPickerDialog(
      initialLatitude = pickedMapLat ?: 41.32094,
      initialLongitude = pickedMapLng ?: -112.06075,
      onLocationPicked = { addr, lat, lng ->
        addressText = addr
        pickedMapLat = lat
        pickedMapLng = lng
        useCurrentGps = false
        showMapPicker = false
      },
      onDismiss = { showMapPicker = false },
    )
    return
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = Color(0xFF132D57),
    titleContentColor = Color.White,
    textContentColor = Color.White,
    title = { Text("Add New Customer", fontWeight = FontWeight.Bold) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = customerName,
          onValueChange = { customerName = it },
          label = { Text("Customer / Site Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = locationLabel,
          onValueChange = { locationLabel = it },
          label = { Text("First Site Label (e.g. Main Building, Roof)") },
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
            label = { Text("Current GPS") },
          )
          FilterChip(
            selected = !useCurrentGps,
            onClick = {
              useCurrentGps = false
              if (pickedMapLat == null) {
                showMapPicker = true
              }
            },
            label = { Text("Pick on Map") },
          )
        }

        if (!useCurrentGps) {
          Button(
            onClick = { showMapPicker = true },
            colors =
              ButtonDefaults.buttonColors(
                containerColor = Color(0xFF0F93E8),
                contentColor = Color.White,
              ),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text =
                if (pickedMapLat != null) "📍 Change Location on Map"
                else "📍 Set Address on Map",
              fontWeight = FontWeight.Bold,
            )
          }

          if (pickedMapLat != null && pickedMapLng != null) {
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = Color(0xFF0E2243),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(10.dp)) {
                Text(
                  text = addressText.ifBlank { "Selected Map Pin" },
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = FontWeight.Bold,
                  color = Color.White,
                )
                Text(
                  text =
                    "Exact Pin: ${"%.5f".format(pickedMapLat)}, ${"%.5f".format(pickedMapLng)}",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color(0xFF38BDF8),
                )
              }
            }
          }
        } else {
          Text(
            text = "Uses your phone's current GPS coordinates & auto-fills the street address.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF94A3B8),
          )
        }
      }
    },
    confirmButton = {
      Button(
        enabled =
          customerName.isNotBlank() &&
            (useCurrentGps || pickedMapLat != null || addressText.isNotBlank()) &&
            !isSaving,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = Color(0xFF0F93E8),
            contentColor = Color.White,
          ),
        onClick = {
          val finalAddressPayload =
            if (!useCurrentGps && pickedMapLat != null && pickedMapLng != null) {
              "${addressText.ifBlank { "Pinned Map Location" }}@@COORDS:$pickedMapLat,$pickedMapLng"
            } else {
              addressText
            }
          onConfirm(customerName, locationLabel, useCurrentGps, finalAddressPayload)
        },
      ) {
        Text("Save Customer", fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel", color = Color(0xFF94A3B8))
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
  var locationLabel by remember { mutableStateOf("Main Building") }
  var useCurrentGps by remember { mutableStateOf(currentLocation != null) }
  var addressText by remember { mutableStateOf("") }
  var pickedMapLat by remember { mutableStateOf<Double?>(null) }
  var pickedMapLng by remember { mutableStateOf<Double?>(null) }
  var showMapPicker by remember { mutableStateOf(false) }

  if (showMapPicker) {
    InteractiveMapPickerDialog(
      initialLatitude = pickedMapLat ?: currentLocation?.latitude ?: 41.32094,
      initialLongitude = pickedMapLng ?: currentLocation?.longitude ?: -112.06075,
      onLocationPicked = { addr, lat, lng ->
        addressText = addr
        pickedMapLat = lat
        pickedMapLng = lng
        useCurrentGps = false
        showMapPicker = false
      },
      onDismiss = { showMapPicker = false },
    )
    return
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = Color(0xFF132D57),
    titleContentColor = Color.White,
    textContentColor = Color.White,
    title = { Text("Add Location for $customerName", fontWeight = FontWeight.Bold) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = locationLabel,
          onValueChange = { locationLabel = it },
          label = { Text("Location Name (e.g. Main Building, Roof Unit)") },
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
            label = { Text("Current GPS") },
          )
          FilterChip(
            selected = !useCurrentGps,
            onClick = {
              useCurrentGps = false
              if (pickedMapLat == null) {
                showMapPicker = true
              }
            },
            label = { Text("Pick on Map") },
          )
        }

        if (useCurrentGps) {
          Text(
            text =
              if (currentLocation != null) {
                "Device GPS: ${"%.5f".format(currentLocation.latitude)}, ${"%.5f".format(currentLocation.longitude)}\nAuto-detects street address from your GPS."
              } else {
                "Waiting for GPS signal... Or tap 'Pick on Map' above."
              },
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF94A3B8),
          )
        } else {
          Button(
            onClick = { showMapPicker = true },
            colors =
              ButtonDefaults.buttonColors(
                containerColor = Color(0xFF0F93E8),
                contentColor = Color.White,
              ),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text =
                if (pickedMapLat != null) "📍 Change Location on Map"
                else "📍 Set Address on Map",
              fontWeight = FontWeight.Bold,
            )
          }

          if (pickedMapLat != null && pickedMapLng != null) {
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = Color(0xFF0E2243),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(10.dp)) {
                Text(
                  text = addressText.ifBlank { "Selected Map Pin" },
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = FontWeight.Bold,
                  color = Color.White,
                )
                Text(
                  text =
                    "Exact Pin: ${"%.5f".format(pickedMapLat)}, ${"%.5f".format(pickedMapLng)}",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color(0xFF38BDF8),
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        enabled = (useCurrentGps || pickedMapLat != null || addressText.isNotBlank()) && !isSaving,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = Color(0xFF0F93E8),
            contentColor = Color.White,
          ),
        onClick = {
          val finalAddressPayload =
            if (!useCurrentGps && pickedMapLat != null && pickedMapLng != null) {
              "${addressText.ifBlank { "Pinned Map Location" }}@@COORDS:$pickedMapLat,$pickedMapLng"
            } else {
              addressText
            }
          onConfirm(locationLabel, useCurrentGps, finalAddressPayload)
        },
      ) {
        Text("Add Location", fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel", color = Color(0xFF94A3B8))
      }
    },
  )
}

/**
 * Full-screen Interactive Map Picker (Google Maps Hybrid Satellite + Street View):
 * - Search any street address or business at the top to jump the map there
 * - Pan & zoom the map with your finger and tap the exact building rooftop to drop the pin
 * - Automatically reverse-geocodes the pin into a readable street address
 * - Saves the exact rooftop GPS coordinates so GPS Auto-Detect (< 325 ft) works 100% of the time!
 */
@android.annotation.SuppressLint("SetJavaScriptEnabled")
@Composable
private fun InteractiveMapPickerDialog(
  initialLatitude: Double,
  initialLongitude: Double,
  onLocationPicked: (String, Double, Double) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var pinLat by remember { mutableDoubleStateOf(initialLatitude) }
  var pinLng by remember { mutableDoubleStateOf(initialLongitude) }
  var resolvedAddress by remember { mutableStateOf("Tap on the map or search an address above") }
  var searchQuery by remember { mutableStateOf("") }
  var isSearching by remember { mutableStateOf(false) }
  var webViewRef by remember { mutableStateOf<WebView?>(null) }

  // Reverse-geocode whenever the pin moves
  LaunchedEffect(pinLat, pinLng) {
    withContext(Dispatchers.IO) {
      if (Geocoder.isPresent()) {
        try {
          @Suppress("DEPRECATION")
          val results = Geocoder(context, Locale.getDefault()).getFromLocation(pinLat, pinLng, 1)
          val line = results?.firstOrNull()?.getAddressLine(0)
          if (!line.isNullOrBlank()) {
            resolvedAddress = line
            return@withContext
          }
        } catch (_: Exception) {}
      }
      resolvedAddress = "GPS (${"%.5f".format(pinLat)}, ${"%.5f".format(pinLng)})"
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties =
      DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false,
      ),
  ) {
    Surface(
      modifier = Modifier.fillMaxSize(),
      color = Color(0xFF0E2243),
      contentColor = Color.White,
    ) {
    Column(
      modifier =
        Modifier.fillMaxSize()
          .statusBarsPadding()
          .navigationBarsPadding(),
    ) {
      // Top Search & Navigation Bar
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .background(Color(0xFF132D57))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedButton(
            onClick = onDismiss,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            modifier = Modifier.height(34.dp),
          ) {
            Text("← Back", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
          }
          Text(
            text = "Tap Rooftop or Search Address",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
          )
          Spacer(modifier = Modifier.width(48.dp))
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search address, city, or business...") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          Button(
            enabled = searchQuery.isNotBlank() && !isSearching,
            onClick = {
              val q = searchQuery.trim()
              if (q.isEmpty()) return@Button
              isSearching = true
              scope.launch {
                val found =
                  withContext(Dispatchers.IO) {
                    if (Geocoder.isPresent()) {
                      try {
                        @Suppress("DEPRECATION")
                        val list = Geocoder(context, Locale.getDefault()).getFromLocationName(q, 1)
                        list?.firstOrNull()
                      } catch (_: Exception) {
                        null
                      }
                    } else {
                      null
                    }
                  }
                isSearching = false
                if (found != null) {
                  pinLat = found.latitude
                  pinLng = found.longitude
                  @Suppress("DEPRECATION")
                  val line = found.getAddressLine(0)
                  if (!line.isNullOrBlank()) {
                    resolvedAddress = line
                  }
                  webViewRef?.evaluateJavascript(
                    "window.movePinTo(${found.latitude}, ${found.longitude});",
                    null,
                  )
                } else {
                  // Let the map's JS geocoder search as fallback
                  val escaped = q.replace("'", "\\'")
                  webViewRef?.evaluateJavascript("window.searchInMap('$escaped');", null)
                }
              }
            },
            colors =
              ButtonDefaults.buttonColors(
                containerColor = Color(0xFF0F93E8),
                contentColor = Color.White,
              ),
            modifier = Modifier.height(52.dp),
          ) {
            Text(if (isSearching) "..." else "Search", fontWeight = FontWeight.Bold)
          }
        }
      }

      // Interactive Map WebView (Google Maps Hybrid Satellite + Street Tiles)
      Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        AndroidView(
          modifier = Modifier.fillMaxSize(),
          factory = { ctx ->
            WebView(ctx).apply {
              layoutParams =
                ViewGroup.LayoutParams(
                  ViewGroup.LayoutParams.MATCH_PARENT,
                  ViewGroup.LayoutParams.MATCH_PARENT,
                )
              settings.javaScriptEnabled = true
              settings.domStorageEnabled = true
              webViewClient = WebViewClient()
              addJavascriptInterface(
                object {
                  @JavascriptInterface
                  fun onPinSelected(lat: Double, lng: Double) {
                    pinLat = lat
                    pinLng = lng
                  }
                },
                "AndroidBridge",
              )
              val html = buildInteractiveMapHtml(initialLatitude, initialLongitude)
              loadDataWithBaseURL("https://unpkg.com/", html, "text/html", "UTF-8", null)
              webViewRef = this
            }
          },
        )
      }

      // Bottom Selected Pin Confirmation Bar
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .background(Color(0xFF132D57))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = resolvedAddress,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold,
          color = Color.White,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "Pin GPS: ${"%.6f".format(pinLat)}, ${"%.6f".format(pinLng)} (Tap anywhere on map to move pin)",
          style = MaterialTheme.typography.labelSmall,
          color = Color(0xFF38BDF8),
        )
        Button(
          onClick = { onLocationPicked(resolvedAddress, pinLat, pinLng) },
          colors =
            ButtonDefaults.buttonColors(
              containerColor = Color(0xFF0F93E8),
              contentColor = Color.White,
            ),
          modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
          Text("Use This Location", fontWeight = FontWeight.Bold)
        }
      }
    }
  }
  }
}

private fun buildInteractiveMapHtml(initLat: Double, initLng: Double): String {
  return """
    <!DOCTYPE html>
    <html>
    <head>
      <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
      <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
      <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
      <style>
        html, body, #map { margin: 0; padding: 0; width: 100%; height: 100%; background: #0E2243; font-family: sans-serif; }
        .layer-toggle {
          position: absolute; top: 12px; right: 12px; z-index: 1000;
          background: #132D57; color: #fff; border: 2px solid #0F93E8;
          border-radius: 8px; padding: 8px 12px; font-weight: bold; font-size: 13px;
          box-shadow: 0 2px 8px rgba(0,0,0,0.4);
        }
      </style>
    </head>
    <body>
      <div id="map"></div>
      <button id="toggleBtn" class="layer-toggle" onclick="toggleLayer()">Satellite View</button>
      <script>
        var streetLayer = L.tileLayer('https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}', {
          maxZoom: 21, attribution: 'Google Maps'
        });
        var hybridLayer = L.tileLayer('https://mt1.google.com/vt/lyrs=y&x={x}&y={y}&z={z}', {
          maxZoom: 21, attribution: 'Google Satellite'
        });

        var isHybrid = true;
        var map = L.map('map', {
          center: [$initLat, $initLng],
          zoom: 18,
          layers: [hybridLayer],
          zoomControl: true
        });

        var marker = L.marker([$initLat, $initLng], { draggable: true }).addTo(map);

        function notifyAndroid(latlng) {
          if (window.AndroidBridge && window.AndroidBridge.onPinSelected) {
            window.AndroidBridge.onPinSelected(latlng.lat, latlng.lng);
          }
        }

        map.on('click', function(e) {
          marker.setLatLng(e.latlng);
          notifyAndroid(e.latlng);
        });

        marker.on('dragend', function(e) {
          notifyAndroid(marker.getLatLng());
        });

        window.movePinTo = function(lat, lng) {
          var ll = L.latLng(lat, lng);
          marker.setLatLng(ll);
          map.setView(ll, 18);
          notifyAndroid(ll);
        };

        window.searchInMap = function(query) {
          fetch('https://nominatim.openstreetmap.org/search?format=json&limit=1&q=' + encodeURIComponent(query))
            .then(function(r) { return r.json(); })
            .then(function(data) {
              if (data && data.length > 0) {
                var lat = parseFloat(data[0].lat);
                var lon = parseFloat(data[0].lon);
                window.movePinTo(lat, lon);
              }
            });
        };

        window.toggleLayer = function() {
          var btn = document.getElementById('toggleBtn');
          if (isHybrid) {
            map.removeLayer(hybridLayer);
            map.addLayer(streetLayer);
            btn.innerText = 'Satellite View';
            isHybrid = false;
          } else {
            map.removeLayer(streetLayer);
            map.addLayer(hybridLayer);
            btn.innerText = 'Street View';
            isHybrid = true;
          }
        };
      </script>
    </body>
    </html>
  """.trimIndent()
}

@android.annotation.SuppressLint("ClickableViewAccessibility")
@Composable
private fun CameraXPreview(
  imageCapture: ImageCapture,
  onCameraReady: (Camera, Float, Float, Float) -> Unit,
  onZoomRatioChanged: (Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  var boundCamera by remember { mutableStateOf<Camera?>(null) }

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

  LaunchedEffect(previewView, boundCamera) {
    val camera = boundCamera ?: return@LaunchedEffect
    val scaleGestureDetector =
      ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
          override fun onScale(detector: ScaleGestureDetector): Boolean {
            val zoomState = camera.cameraInfo.zoomState.value ?: return false
            val newRatio =
              (zoomState.zoomRatio * detector.scaleFactor).coerceIn(
                zoomState.minZoomRatio,
                zoomState.maxZoomRatio,
              )
            camera.cameraControl.setZoomRatio(newRatio)
            onZoomRatioChanged(newRatio)
            return true
          }
        },
      )

    var downTimestamp = 0L
    previewView.setOnTouchListener { _, event ->
      scaleGestureDetector.onTouchEvent(event)
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          downTimestamp = System.currentTimeMillis()
        }
        MotionEvent.ACTION_UP -> {
          if (!scaleGestureDetector.isInProgress &&
              System.currentTimeMillis() - downTimestamp < 250L
          ) {
            val point = previewView.meteringPointFactory.createPoint(event.x, event.y)
            val focusAction = FocusMeteringAction.Builder(point).build()
            camera.cameraControl.startFocusAndMetering(focusAction)
          }
        }
      }
      true
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
          val camera =
            cameraProvider.bindToLifecycle(
              lifecycleOwner,
              CameraSelector.DEFAULT_BACK_CAMERA,
              preview,
              imageCapture,
            )
          boundCamera = camera
          val zoomState = camera.cameraInfo.zoomState.value
          onCameraReady(
            camera,
            zoomState?.zoomRatio ?: 1.0f,
            zoomState?.minZoomRatio ?: 1.0f,
            zoomState?.maxZoomRatio ?: 8.0f,
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
private fun CameraZoomSelectorBar(
  currentZoomRatio: Float,
  minZoomRatio: Float,
  maxZoomRatio: Float,
  onSelectZoomRatio: (Float) -> Unit,
) {
  val presets =
    remember(minZoomRatio, maxZoomRatio) {
      buildList {
        if (minZoomRatio < 0.9f) add(minZoomRatio)
        add(1.0f)
        if (maxZoomRatio >= 2.0f) add(2.0f)
        if (maxZoomRatio >= 4.0f) add(4.0f)
        if (maxZoomRatio >= 8.0f) add(8.0f)
      }
    }

  Surface(
    shape = RoundedCornerShape(24.dp),
    color = Color(0xB3111827),
    modifier = Modifier.border(1.dp, Color(0xFF374151), RoundedCornerShape(24.dp)),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      presets.forEach { preset ->
        val isActive = kotlin.math.abs(currentZoomRatio - preset) < 0.15f
        val displayLabel =
          if (isActive) {
            if (currentZoomRatio % 1.0f < 0.08f) "${currentZoomRatio.toInt()}x"
            else "${"%.1f".format(currentZoomRatio)}x"
          } else {
            if (preset % 1.0f < 0.08f) "${preset.toInt()}x" else "${"%.1f".format(preset)}x"
          }

        Box(
          contentAlignment = Alignment.Center,
          modifier =
            Modifier.size(36.dp)
              .clip(CircleShape)
              .background(if (isActive) Color.White else Color(0xFF1F2937))
              .clickable { onSelectZoomRatio(preset) },
        ) {
          Text(
            text = displayLabel,
            color = if (isActive) Color(0xFF0B0F14) else Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
          )
        }
      }

      // If user pinched to an in-between zoom ratio (e.g. 2.7x), show a live indicator chip
      val matchesAnyPreset = presets.any { kotlin.math.abs(currentZoomRatio - it) < 0.15f }
      if (!matchesAnyPreset) {
        Box(
          contentAlignment = Alignment.Center,
          modifier =
            Modifier.height(36.dp)
              .clip(RoundedCornerShape(18.dp))
              .background(Color(0xFF38BDF8))
              .padding(horizontal = 10.dp),
        ) {
          Text(
            text = "${"%.1f".format(currentZoomRatio)}x",
            color = Color(0xFF0B0F14),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }
  }
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
      Modifier.fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(horizontal = 20.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OutlinedButton(
        onClick = onClose,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        modifier = Modifier.height(36.dp),
      ) {
        Text("← Back", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
      }
      Column(
        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text = "AI HVAC Part ID",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = Color.White,
        )
        Text(
          text = customerName,
          style = MaterialTheme.typography.bodySmall,
          color = Color(0xFF9CA3AF),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      TextButton(onClick = onOpenSearchSitesSettings) {
        Text("⚙ Sites", color = Color(0xFF34D399))
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
                  containerColor = Color(0xFF0F93E8),
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
      containerColor = Color(0xFF132D57),
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
              color = Color(0xFF0E2243),
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
          Text("⚙ Manage Search Sites", color = Color(0xFF38BDF8))
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

/**
 * Launches the user's preferred Maps app (Google Maps, Waze, etc.) with directions
 * directly to the site location's exact coordinates or street address.
 */
private fun openMapsDirections(
  context: Context,
  latitude: Double,
  longitude: Double,
  address: String,
  label: String,
) {
  val hasValidCoords = latitude != 0.0 || longitude != 0.0
  val destinationQuery =
    if (hasValidCoords) {
      "$latitude,$longitude"
    } else {
      Uri.encode(address.ifBlank { label })
    }
  val mapsUrl = "https://www.google.com/maps/dir/?api=1&destination=$destinationQuery&travelmode=driving"
  try {
    val intent =
      Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    context.startActivity(intent)
  } catch (_: Exception) {
    // Fallback to geo: URI if no handler for https URL
    try {
      val geoUri =
        if (hasValidCoords) {
          Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude(${Uri.encode(label)})")
        } else {
          Uri.parse("geo:0,0?q=${Uri.encode(address.ifBlank { label })}")
        }
      context.startActivity(Intent(Intent.ACTION_VIEW, geoUri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    } catch (_: Exception) {}
  }
}

