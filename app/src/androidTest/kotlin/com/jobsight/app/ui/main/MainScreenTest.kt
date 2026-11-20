package com.jobsight.app.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.jobsight.app.data.Customer
import com.jobsight.app.data.CustomerLocation
import com.jobsight.app.data.CustomerMatch
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.jobsight.app.ui.main.FieldCameraContent]. */
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setup() {
    val sampleLocation =
      CustomerLocation(
        id = 10L,
        customerId = 1L,
        label = "Main Building",
        address = "1600 Amphitheatre Pkwy",
        latitude = 37.4219983,
        longitude = -122.084,
      )
    val sampleCustomer =
      Customer(
        id = 1L,
        name = "Apex Commercial Plaza",
        address = "1600 Amphitheatre Pkwy",
        latitude = 37.4219983,
        longitude = -122.084,
        locations = listOf(sampleLocation),
      )
    composeTestRule.setContent {
      FieldCameraContent(
        uiState =
          FieldCameraUiState(
            isLoadingCustomers = false,
            customers = listOf(sampleCustomer),
            closestMatch =
              CustomerMatch(
                customer = sampleCustomer,
                matchedLocation = sampleLocation,
                distanceMeters = 18f,
              ),
          ),
        hasCameraPermission = false,
        hasLocationPermission = true,
        onRequestPermissions = {},
        onPhotoCapturedToCache = {},
        onOpenCustomerPicker = {},
        onCloseCustomerPicker = {},
        onSelectCustomerManually = {},
        onOpenCustomerHub = {},
        onCloseCustomerHub = {},
        onSelectHubCustomer = {},
        onCreateCustomer = { _, _, _, _ -> },
        onAddLocationToCustomer = { _, _, _, _ -> },
        onTriggerDemoAi = {},
        onShowPartsForPhoto = {},
        onDismissBottomSheet = {},
        onReopenBottomSheet = {},
        onDismissError = {},
      )
    }
  }

  @Test
  fun detectedCustomerBadge_isDisplayedWhenWithin100Meters() {
    composeTestRule.onNodeWithText("Detected: Apex Commercial Plaza").assertExists()
  }
}
