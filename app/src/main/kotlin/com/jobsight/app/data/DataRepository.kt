package com.jobsight.app.data

import com.jobsight.app.BuildConfig
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

interface DataRepository {
  suspend fun getCustomers(): List<Customer>
  suspend fun createCustomer(name: String): Customer
  suspend fun deleteCustomer(customerId: Long)
  suspend fun addCustomerLocation(
    customerId: Long,
    label: String,
    address: String,
    latitude: Double,
    longitude: Double,
  ): CustomerLocation
  suspend fun deleteCustomerLocation(locationId: Long)

  suspend fun geocodeAddress(addressQuery: String): ResolvedAddress

  suspend fun getPhotosForCustomer(customerId: Long): List<CapturedPhotoRow>
  suspend fun downloadImageBytes(imageUrl: String): ByteArray?

  suspend fun getSearchSites(): List<SearchSite>
  suspend fun addSearchSite(name: String, urlTemplate: String): SearchSite
  suspend fun deleteSearchSite(siteId: Long)

  suspend fun uploadPhotoAndFlushCache(
    cacheFile: File,
    customerId: Long?,
    customerLocationId: Long? = null,
  ): CapturedPhotoRow

  fun observeAiAnalysis(photoId: Long): Flow<List<HvacPartAnalysis>>

  suspend fun populateDemoAiAnalysis(photoId: Long): List<HvacPartAnalysis>
}

class DefaultDataRepository : DataRepository {
  private val supabaseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
  private val apiKey = BuildConfig.SUPABASE_ANON_KEY
  private val storageBucket = "job_site_photos"

  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  private val httpClient =
    OkHttpClient.Builder()
      .connectTimeout(20, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .writeTimeout(30, TimeUnit.SECONDS)
      .pingInterval(25, TimeUnit.SECONDS)
      .build()

  override suspend fun getCustomers(): List<Customer> =
    withContext(Dispatchers.IO) {
      val selectCols =
        "id,name,address,latitude,longitude,customer_locations(id,customer_id,label,address,latitude,longitude)"
      val request =
        Request.Builder()
          .url("$supabaseUrl/rest/v1/customers?select=$selectCols&order=id.asc")
          .header("apikey", apiKey)
          .header("Authorization", "Bearer $apiKey")
          .get()
          .build()

      httpClient.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
          throw IOException("Failed to fetch customers (${response.code}): $body")
        }
        json.decodeFromString<List<Customer>>(body)
      }
    }

  override suspend fun createCustomer(name: String): Customer =
    withContext(Dispatchers.IO) {
      val payload = json.encodeToString(CreateCustomerRequest(name = name.trim()))
      val request =
        Request.Builder()
          .url("$supabaseUrl/rest/v1/customers")
          .header("apikey", apiKey)
          .header("Authorization", "Bearer $apiKey")
          .header("Content-Type", "application/json")
          .header("Prefer", "return=representation")
          .post(payload.toRequestBody("application/json".toMediaType()))
          .build()

      httpClient.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
          throw IOException("Failed to create customer (${response.code}): $body")
        }
        json.decodeFromString<List<Customer>>(body).first()
      }
    }

  override suspend fun deleteCustomer(customerId: Long) =
    withContext(Dispatchers.IO) {
      val request =
        Request.Builder()
          .url("$supabaseUrl/rest/v1/customers?id=eq.$customerId")
          .header("apikey", apiKey)
          .header("Authorization", "Bearer $apiKey")
          .delete()
          .build()

      httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          val body = response.body?.string().orEmpty()
          throw IOException("Failed to delete customer (${response.code}): $body")
        }
      }
    }

  override suspend fun addCustomerLocation(
    customerId: Long,
    label: String,
    address: String,
    latitude: Double,
    longitude: Double,
  ): CustomerLocation =
    withContext(Dispatchers.IO) {
      val payload =
        json.encodeToString(
          CreateCustomerLocationRequest(
            customerId = customerId,
            label = label.trim().ifEmpty { "Job Site" },
            address = address.trim(),
            latitude = latitude,
            longitude = longitude,
          )
        )
      val request =
        Request.Builder()
          .url("$supabaseUrl/rest/v1/customer_locations")
          .header("apikey", apiKey)
          .header("Authorization", "Bearer $apiKey")
          .header("Content-Type", "application/json")
          .header("Prefer", "return=representation")
          .post(payload.toRequestBody("application/json".toMediaType()))
          .build()

      httpClient.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
          throw IOException("Failed to add customer location (${response.code}): $body")
        }
        json.decodeFromString<List<CustomerLocation>>(body).first()
      }
    }

  override suspend fun deleteCustomerLocation(locationId: Long) =
    withContext(Dispatchers.IO) {
      val request =
        Request.Builder()
          .url("$supabaseUrl/rest/v1/customer_locations?id=eq.$locationId")
          .header("apikey", apiKey)
          .header("Authorization", "Bearer $apiKey")
          .delete()
          .build()

      httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          val body = response.body?.string().orEmpty()
          throw IOException("Failed to delete customer location (${response.code}): $body")
        }
      }
    }

  override suspend fun geocodeAddress(addressQuery: String): ResolvedAddress =
    withContext(Dispatchers.IO) {
      val trimmed = addressQuery.trim()
      if (trimmed.isEmpty()) {
        throw IOException("Please enter a street address")
      }
      val encoded = URLEncoder.encode(trimmed, "UTF-8")
      val request =
        Request.Builder()
          .url("https://nominatim.openstreetmap.org/search?format=json&limit=1&q=$encoded")
          .header("User-Agent", "JobSightAndroidApp/1.0 (com.jobsight.app)")
          .get()
          .build()

      httpClient.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
          throw IOException("Geocoding service returned ${response.code}")
        }
        val array = json.parseToJsonElement(body) as? JsonArray
        val first =
          array?.firstOrNull() as? JsonObject
            ?: throw IOException("Could not find coordinates for address: \"$trimmed\"")

        val lat =
          first["lat"]?.jsonPrimitive?.content?.toDoubleOrNull()
            ?: throw IOException("Invalid latitude returned for address")
        val lon =
          first["lon"]?.jsonPrimitive?.content?.toDoubleOrNull()
            ?: throw IOException("Invalid longitude returned for address")
        val displayName = first["display_name"]?.jsonPrimitive?.content ?: trimmed

        ResolvedAddress(
          formattedAddress = displayName,
          latitude = lat,
          longitude = lon,
        )
      }
    }

  override suspend fun getPhotosForCustomer(customerId: Long): List<CapturedPhotoRow> =
    withContext(Dispatchers.IO) {
      val request =
        Request.Builder()
          .url(
            "$supabaseUrl/rest/v1/captured_photos?customer_id=eq.$customerId&select=id,customer_id,customer_location_id,image_url,captured_at,ai_analysis_json&order=captured_at.desc"
          )
          .header("apikey", apiKey)
          .header("Authorization", "Bearer $apiKey")
          .get()
          .build()

      httpClient.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
          throw IOException("Failed to fetch photos (${response.code}): $body")
        }
        json.decodeFromString<List<CapturedPhotoRow>>(body)
      }
    }

  override suspend fun downloadImageBytes(imageUrl: String): ByteArray? =
    withContext(Dispatchers.IO) {
      try {
        val request = Request.Builder().url(imageUrl).get().build()
        httpClient.newCall(request).execute().use { response ->
          if (!response.isSuccessful) return@withContext null
          response.body?.bytes()
        }
      } catch (_: Exception) {
        null
      }
    }

  override suspend fun getSearchSites(): List<SearchSite> =
    withContext(Dispatchers.IO) {
      val request =
        Request.Builder()
          .url("$supabaseUrl/rest/v1/search_sites?select=id,name,url_template&order=id.asc")
          .header("apikey", apiKey)
          .header("Authorization", "Bearer $apiKey")
          .get()
          .build()

      httpClient.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
          throw IOException("Failed to fetch search sites (${response.code}): $body")
        }
        val list = json.decodeFromString<List<SearchSite>>(body)
        list.ifEmpty { SearchSite.DEFAULT_SITES }
      }
    }

  override suspend fun addSearchSite(name: String, urlTemplate: String): SearchSite =
    withContext(Dispatchers.IO) {
      val cleanName = name.trim()
      val rawInput = urlTemplate.trim()
      val normalizedTemplate =
        when {
          rawInput.contains("{query}") -> rawInput
          rawInput.startsWith("http://") || rawInput.startsWith("https://") -> {
            if (rawInput.contains("?")) "$rawInput{query}" else rawInput
          }
          else -> "https://www.google.com/search?q=site:${rawInput.trimEnd('/')}+{query}"
        }

      val payload =
        json.encodeToString(
          CreateSearchSiteRequest(
            name = cleanName,
            urlTemplate = normalizedTemplate,
          )
        )
      val request =
        Request.Builder()
          .url("$supabaseUrl/rest/v1/search_sites")
          .header("apikey", apiKey)
          .header("Authorization", "Bearer $apiKey")
          .header("Content-Type", "application/json")
          .header("Prefer", "return=representation")
          .post(payload.toRequestBody("application/json".toMediaType()))
          .build()

      httpClient.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
          throw IOException("Failed to add search site (${response.code}): $body")
        }
        json.decodeFromString<List<SearchSite>>(body).first()
      }
    }

  override suspend fun deleteSearchSite(siteId: Long) =
    withContext(Dispatchers.IO) {
      val request =
        Request.Builder()
          .url("$supabaseUrl/rest/v1/search_sites?id=eq.$siteId")
          .header("apikey", apiKey)
          .header("Authorization", "Bearer $apiKey")
          .delete()
          .build()

      httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          val body = response.body?.string().orEmpty()
          throw IOException("Failed to delete search site (${response.code}): $body")
        }
      }
    }

  override suspend fun uploadPhotoAndFlushCache(
    cacheFile: File,
    customerId: Long?,
    customerLocationId: Long?,
  ): CapturedPhotoRow =
    withContext(Dispatchers.IO) {
      try {
        if (!cacheFile.exists()) {
          throw IOException("Cached photo file does not exist")
        }

        val folder = customerId?.let { "customer_$it" } ?: "untagged"
        val objectPath = "$folder/${System.currentTimeMillis()}_${cacheFile.name}"

        // 1. Upload image to Supabase Storage bucket 'job_site_photos'
        val uploadRequest =
          Request.Builder()
            .url("$supabaseUrl/storage/v1/object/$storageBucket/$objectPath")
            .header("apikey", apiKey)
            .header("Authorization", "Bearer $apiKey")
            .header("x-upsert", "true")
            .post(cacheFile.asRequestBody("image/jpeg".toMediaType()))
            .build()

        httpClient.newCall(uploadRequest).execute().use { uploadResponse ->
          if (!uploadResponse.isSuccessful) {
            val err = uploadResponse.body?.string().orEmpty()
            throw IOException("Storage upload failed (${uploadResponse.code}): $err")
          }
        }

        val publicImageUrl = "$supabaseUrl/storage/v1/object/public/$storageBucket/$objectPath"

        // 2. Insert database row in 'captured_photos' linking image_url to customer_id & customer_location_id
        val insertBody =
          json.encodeToString(
            CapturedPhotoInsert(
              customerId = customerId,
              customerLocationId = customerLocationId,
              imageUrl = publicImageUrl,
            )
          )

        val insertRequest =
          Request.Builder()
            .url("$supabaseUrl/rest/v1/captured_photos")
            .header("apikey", apiKey)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("Prefer", "return=representation")
            .post(insertBody.toRequestBody("application/json".toMediaType()))
            .build()

        val insertedPhoto =
          httpClient.newCall(insertRequest).execute().use { insertResponse ->
            val body = insertResponse.body?.string().orEmpty()
            if (!insertResponse.isSuccessful) {
              throw IOException("Database insert failed (${insertResponse.code}): $body")
            }
            json.decodeFromString<List<CapturedPhotoRow>>(body).first()
          }

        // Trigger the Supabase Edge Function 'analyze-job-photo' asynchronously
        try {
          val fnPayload =
            buildJsonObject {
              put("photo_id", insertedPhoto.id)
              put("image_url", insertedPhoto.imageUrl)
            }
          val fnRequest =
            Request.Builder()
              .url("$supabaseUrl/functions/v1/analyze-job-photo")
              .header("apikey", apiKey)
              .header("Authorization", "Bearer $apiKey")
              .header("Content-Type", "application/json")
              .post(fnPayload.toString().toRequestBody("application/json".toMediaType()))
              .build()
          httpClient.newCall(fnRequest).enqueue(
            object : okhttp3.Callback {
              override fun onFailure(call: okhttp3.Call, e: IOException) {}
              override fun onResponse(call: okhttp3.Call, response: Response) {
                response.close()
              }
            }
          )
        } catch (_: Exception) {
          // Database trigger (pg_net) also invokes the Edge Function on INSERT
        }

        insertedPhoto
      } finally {
        // 3. Flush local cache immediately per architecture plan
        if (cacheFile.exists()) {
          cacheFile.delete()
        }
      }
    }

  override fun observeAiAnalysis(photoId: Long): Flow<List<HvacPartAnalysis>> = callbackFlow {
    val wsUrl =
      supabaseUrl.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") +
        "/realtime/v1/websocket?apikey=$apiKey&vsn=1.0.0"

    val wsRequest = Request.Builder().url(wsUrl).build()
    val webSocket =
      httpClient.newWebSocket(
        wsRequest,
        object : WebSocketListener() {
          override fun onOpen(webSocket: WebSocket, response: Response) {
            val joinPayload =
              buildJsonObject {
                put("topic", "realtime:public:captured_photos")
                put("event", "phx_join")
                put(
                  "payload",
                  buildJsonObject {
                    put(
                      "config",
                      buildJsonObject {
                        put(
                          "postgres_changes",
                          buildJsonArray {
                            add(
                              buildJsonObject {
                                put("event", "UPDATE")
                                put("schema", "public")
                                put("table", "captured_photos")
                                put("filter", "id=eq.$photoId")
                              }
                            )
                          }
                        )
                      }
                    )
                  }
                )
                put("ref", "1")
              }
            webSocket.send(joinPayload.toString())
          }

          override fun onMessage(webSocket: WebSocket, text: String) {
            try {
              val root = json.parseToJsonElement(text).jsonObject
              val payload = root["payload"] as? JsonObject ?: return
              val data = payload["data"] as? JsonObject ?: return
              val record = data["record"] as? JsonObject ?: return
              val aiJson = record["ai_analysis_json"]
              val parts = AnalysisJsonParser.parseParts(aiJson)
              if (parts.isNotEmpty()) {
                trySend(parts)
              }
            } catch (_: Exception) {
              // Ignore non-matching realtime frames
            }
          }
        }
      )

    val pollerJob =
      launch(Dispatchers.IO) {
        while (isActive) {
          try {
            val row = fetchCapturedPhotoById(photoId)
            val parts = AnalysisJsonParser.parseParts(row?.aiAnalysisJson)
            if (parts.isNotEmpty()) {
              trySend(parts)
              break
            }
          } catch (_: Exception) {
            // Continue polling while waiting for Edge Function
          }
          delay(2500L)
        }
      }

    awaitClose {
      pollerJob.cancel()
      webSocket.close(1000, "Closed")
    }
  }

  private fun fetchCapturedPhotoById(photoId: Long): CapturedPhotoRow? {
    val request =
      Request.Builder()
        .url(
          "$supabaseUrl/rest/v1/captured_photos?id=eq.$photoId&select=id,customer_id,customer_location_id,image_url,captured_at,ai_analysis_json"
        )
        .header("apikey", apiKey)
        .header("Authorization", "Bearer $apiKey")
        .get()
        .build()

    httpClient.newCall(request).execute().use { response ->
      if (!response.isSuccessful) return null
      val body = response.body?.string().orEmpty()
      return json.decodeFromString<List<CapturedPhotoRow>>(body).firstOrNull()
    }
  }

  override suspend fun populateDemoAiAnalysis(photoId: Long): List<HvacPartAnalysis> =
    withContext(Dispatchers.IO) {
      val demoParts =
        listOf(
          HvacPartAnalysis(
            partName = "Carrier Condenser Contactor (2-Pole 30A 24V)",
            modelNumber = "HN52KC024",
            searchQuery = "Carrier HN52KC024 2-pole 30A 24V contactor replacement",
          ),
          HvacPartAnalysis(
            partName = "Turbo200 Dual Run Capacitor (45/5 MFD 440V)",
            modelNumber = "CPT00668",
            searchQuery = "45/5 MFD 440V dual run HVAC capacitor replacement",
          ),
        )

      val updatePayload =
        buildJsonObject {
          put(
            "ai_analysis_json",
            json.encodeToJsonElement(
              kotlinx.serialization.builtins.ListSerializer(HvacPartAnalysis.serializer()),
              demoParts,
            ),
          )
        }

      val request =
        Request.Builder()
          .url("$supabaseUrl/rest/v1/captured_photos?id=eq.$photoId")
          .header("apikey", apiKey)
          .header("Authorization", "Bearer $apiKey")
          .header("Content-Type", "application/json")
          .header("Prefer", "return=representation")
          .patch(updatePayload.toString().toRequestBody("application/json".toMediaType()))
          .build()

      httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          val err = response.body?.string().orEmpty()
          throw IOException("Failed to update ai_analysis_json (${response.code}): $err")
        }
      }
      demoParts
    }
}
