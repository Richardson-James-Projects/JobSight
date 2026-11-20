package com.jobsight.app.ui.main

import com.jobsight.app.data.AnalysisJsonParser
import com.jobsight.app.data.CapturedPhotoRow
import com.jobsight.app.data.Customer
import com.jobsight.app.data.CustomerLocation
import com.jobsight.app.data.CustomerMatch
import com.jobsight.app.data.DataRepository
import com.jobsight.app.data.HvacPartAnalysis
import com.jobsight.app.data.ResolvedAddress
import java.io.File
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun geofenceRadius_under100Meters_isDetected() {
    val location =
      CustomerLocation(
        id = 10L,
        customerId = 1L,
        label = "Main Building",
        address = "450 Industrial Blvd",
        latitude = 43.6532,
        longitude = -79.3831,
      )
    val customer =
      Customer(
        id = 1L,
        name = "Northside Medical Center",
        locations = listOf(location),
      )
    val insideMatch =
      CustomerMatch(customer = customer, matchedLocation = location, distanceMeters = 42.5f)
    val outsideMatch =
      CustomerMatch(customer = customer, matchedLocation = location, distanceMeters = 125.0f)

    assertTrue(insideMatch.isWithinGeofence)
    assertFalse(outsideMatch.isWithinGeofence)
  }

  @Test
  fun manualCustomerSelection_overridesAutoDetection() = runTest {
    val viewModel = MainScreenViewModel(FakeJobSightRepository())
    viewModel.selectCustomerManually(2L)
    assertTrue(viewModel.uiState.value.isManualSelection)
    assertEquals("Northside Medical Center", viewModel.uiState.value.activeCustomer?.name)

    viewModel.selectCustomerManually(null)
    assertFalse(viewModel.uiState.value.isManualSelection)
  }

  @Test
  fun analysisJsonParser_parsesEdgeFunctionJsonArray() {
    val rawJson =
      """
      [
        {
          "part_name": "Carrier Condenser Contactor",
          "model_number": "HN52KC024",
          "search_query": "Carrier HN52KC024 replacement"
        }
      ]
      """.trimIndent()

    val element = Json.parseToJsonElement(rawJson)
    val parts = AnalysisJsonParser.parseParts(element)
    assertEquals(1, parts.size)
    assertEquals("Carrier Condenser Contactor", parts.first().partName)
    assertEquals("HN52KC024", parts.first().modelNumber)
  }
}

private class FakeJobSightRepository : DataRepository {
  private val customers =
    listOf(
      Customer(
        id = 1L,
        name = "Apex Commercial Plaza",
        locations =
          listOf(
            CustomerLocation(
              id = 10L,
              customerId = 1L,
              label = "Main Building",
              address = "1600 Amphitheatre Pkwy",
              latitude = 37.4219983,
              longitude = -122.084,
            )
          ),
      ),
      Customer(
        id = 2L,
        name = "Northside Medical Center",
        locations =
          listOf(
            CustomerLocation(
              id = 11L,
              customerId = 2L,
              label = "Hospital Wing",
              address = "450 Industrial Blvd",
              latitude = 43.6532,
              longitude = -79.3831,
            )
          ),
      ),
    )

  override suspend fun getCustomers(): List<Customer> = customers

  override suspend fun createCustomer(name: String): Customer =
    Customer(id = 3L, name = name)

  override suspend fun deleteCustomer(customerId: Long) {}

  override suspend fun addCustomerLocation(
    customerId: Long,
    label: String,
    address: String,
    latitude: Double,
    longitude: Double,
  ): CustomerLocation =
    CustomerLocation(
      id = 20L,
      customerId = customerId,
      label = label,
      address = address,
      latitude = latitude,
      longitude = longitude,
    )

  override suspend fun deleteCustomerLocation(locationId: Long) {}

  override suspend fun geocodeAddress(addressQuery: String): ResolvedAddress =
    ResolvedAddress(addressQuery, 37.422, -122.084)

  override suspend fun getPhotosForCustomer(customerId: Long): List<CapturedPhotoRow> = emptyList()

  override suspend fun downloadImageBytes(imageUrl: String): ByteArray? = null

  override suspend fun uploadPhotoAndFlushCache(
    cacheFile: File,
    customerId: Long?,
    customerLocationId: Long?,
  ): CapturedPhotoRow =
    CapturedPhotoRow(
      id = 99L,
      customerId = customerId,
      customerLocationId = customerLocationId,
      imageUrl = "https://example.com/photo.jpg",
    )

  override fun observeAiAnalysis(photoId: Long): Flow<List<HvacPartAnalysis>> =
    flowOf(
      listOf(
        HvacPartAnalysis(
          partName = "Contactor",
          modelNumber = "HN52KC024",
          searchQuery = "HN52KC024",
        )
      )
    )

  override suspend fun populateDemoAiAnalysis(photoId: Long): List<HvacPartAnalysis> = emptyList()
}
