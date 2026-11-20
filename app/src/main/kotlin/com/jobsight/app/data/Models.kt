package com.jobsight.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

@Serializable
data class CustomerLocation(
  val id: Long,
  @SerialName("customer_id") val customerId: Long,
  val label: String = "Job Site",
  val address: String,
  val latitude: Double,
  val longitude: Double,
)

@Serializable
data class Customer(
  val id: Long,
  val name: String,
  val address: String = "",
  val latitude: Double = 0.0,
  val longitude: Double = 0.0,
  @SerialName("customer_locations") val locations: List<CustomerLocation> = emptyList(),
)

@Serializable
data class CreateCustomerRequest(
  val name: String,
  val address: String = "",
  val latitude: Double = 0.0,
  val longitude: Double = 0.0,
)

@Serializable
data class CreateCustomerLocationRequest(
  @SerialName("customer_id") val customerId: Long,
  val label: String,
  val address: String,
  val latitude: Double,
  val longitude: Double,
)

@Serializable
data class CapturedPhotoInsert(
  @SerialName("customer_id") val customerId: Long?,
  @SerialName("customer_location_id") val customerLocationId: Long? = null,
  @SerialName("image_url") val imageUrl: String,
)

@Serializable
data class CapturedPhotoRow(
  val id: Long,
  @SerialName("customer_id") val customerId: Long? = null,
  @SerialName("customer_location_id") val customerLocationId: Long? = null,
  @SerialName("image_url") val imageUrl: String,
  @SerialName("captured_at") val capturedAt: String? = null,
  @SerialName("ai_analysis_json") val aiAnalysisJson: JsonElement? = null,
)

@Serializable
data class HvacPartAnalysis(
  @SerialName("part_name") val partName: String,
  @SerialName("model_number") val modelNumber: String? = null,
  @SerialName("search_query") val searchQuery: String,
)

@Serializable
data class SearchSite(
  val id: Long,
  val name: String,
  @SerialName("url_template") val urlTemplate: String,
) {
  fun buildSearchUrl(encodedQuery: String): String {
    val trimmed = urlTemplate.trim()
    return when {
      trimmed.contains("{query}") -> trimmed.replace("{query}", encodedQuery)
      trimmed.startsWith("http://") || trimmed.startsWith("https://") -> {
        if (trimmed.contains("?")) "$trimmed$encodedQuery"
        else "https://www.google.com/search?q=site:${trimmed.removePrefix("https://").removePrefix("http://").trimEnd('/')}+$encodedQuery"
      }
      else -> "https://www.google.com/search?q=site:${trimmed.trimEnd('/')}+$encodedQuery"
    }
  }

  companion object {
    val DEFAULT_SITES =
      listOf(
        SearchSite(
          id = -1L,
          name = "SupplyHouse.com",
          urlTemplate = "https://www.supplyhouse.com/sh/control/search/~SEARCH_STRING={query}",
        ),
        SearchSite(
          id = -2L,
          name = "Grainger",
          urlTemplate = "https://www.grainger.com/search?searchQuery={query}",
        ),
        SearchSite(
          id = -3L,
          name = "RepairClinic",
          urlTemplate = "https://www.repairclinic.com/Shop-For-Parts?query={query}",
        ),
        SearchSite(
          id = -4L,
          name = "Google Search",
          urlTemplate = "https://www.google.com/search?q={query}",
        ),
      )
  }
}

@Serializable
data class CreateSearchSiteRequest(
  val name: String,
  @SerialName("url_template") val urlTemplate: String,
)

data class ResolvedAddress(
  val formattedAddress: String,
  val latitude: Double,
  val longitude: Double,
)

data class CustomerMatch(
  val customer: Customer,
  val matchedLocation: CustomerLocation?,
  val distanceMeters: Float,
) {
  companion object {
    const val GEOFENCE_RADIUS_METERS = 100f
  }

  val isWithinGeofence: Boolean
    get() = distanceMeters < GEOFENCE_RADIUS_METERS
}

object AnalysisJsonParser {
  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  fun parseParts(element: JsonElement?): List<HvacPartAnalysis> {
    if (element == null) return emptyList()
    return try {
      when (element) {
        is JsonArray -> json.decodeFromJsonElement<List<HvacPartAnalysis>>(element)
        is JsonObject -> {
          val nestedArray = element["parts"] ?: element["items"] ?: element["results"]
          if (nestedArray is JsonArray) {
            json.decodeFromJsonElement<List<HvacPartAnalysis>>(nestedArray)
          } else if (element.containsKey("part_name")) {
            listOf(json.decodeFromJsonElement<HvacPartAnalysis>(element))
          } else {
            emptyList()
          }
        }
        is JsonPrimitive -> {
          if (element.isString && element.content.isNotBlank()) {
            val parsed = json.parseToJsonElement(element.content)
            parseParts(parsed)
          } else {
            emptyList()
          }
        }
      }
    } catch (_: Exception) {
      emptyList()
    }
  }
}
