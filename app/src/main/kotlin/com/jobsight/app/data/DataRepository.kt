package com.jobsight.app.data

import android.content.Context
import com.jobsight.app.BuildConfig
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

sealed interface PhotoUploadOutcome {
  data class UploadedOnline(val row: CapturedPhotoRow) : PhotoUploadOutcome
  data class QueuedOffline(val queuedCount: Int) : PhotoUploadOutcome
}

interface DataRepository {
  suspend fun getSavedCompanySession(): CompanySession?
  suspend fun loginOrRegisterCompany(
    companyName: String,
    password: String,
    isRegister: Boolean,
  ): CompanySession
  suspend fun signOutCompany()

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
  suspend fun deletePhotos(photos: List<CapturedPhotoRow>)
  suspend fun downloadImageBytes(imageUrl: String): ByteArray?

  suspend fun getSearchSites(): List<SearchSite>
  suspend fun addSearchSite(name: String, urlTemplate: String): SearchSite
  suspend fun deleteSearchSite(siteId: Long)

  suspend fun uploadPhotoOrQueueOffline(
    cacheFile: File,
    customerId: Long?,
    customerLocationId: Long? = null,
  ): PhotoUploadOutcome

  suspend fun flushPendingUploads(): Int
  suspend fun getPendingUploadCount(): Int

  fun observeAiAnalysis(photoId: Long): Flow<List<HvacPartAnalysis>>

  suspend fun populateDemoAiAnalysis(photoId: Long): List<HvacPartAnalysis>
}

class DefaultDataRepository(
  private val appContext: Context? = null,
) : DataRepository {
  private val supabaseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
  private val apiKey = BuildConfig.SUPABASE_ANON_KEY
  private val storageBucket = "job_site_photos"

  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  private val httpClient =
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(25, TimeUnit.SECONDS)
      .writeTimeout(25, TimeUnit.SECONDS)
      .pingInterval(25, TimeUnit.SECONDS)
      .build()

  private val pendingDir: File?
    get() = appContext?.filesDir?.let { File(it, "pending_photo_uploads").apply { mkdirs() } }

  private val queueManifestFile: File?
    get() = appContext?.filesDir?.let { File(it, "pending_photo_queue.json") }

  private val companySessionFile: File?
    get() = appContext?.filesDir?.let { File(it, "company_session.json") }

  @Volatile private var activeSession: CompanySession? = null

  private val customersCacheFile: File?
    get() {
      val suffix = activeSession?.companyId ?: "default"
      return appContext?.filesDir?.let { File(it, "cached_customers_$suffix.json") }
    }

  private val searchSitesCacheFile: File?
    get() {
      val suffix = activeSession?.companyId ?: "default"
      return appContext?.filesDir?.let { File(it, "cached_search_sites_$suffix.json") }
    }

  private fun authBearerToken(): String = activeSession?.accessToken ?: apiKey

  companion object {
    private val queueMutex = Mutex()
  }

  override suspend fun getSavedCompanySession(): CompanySession? =
    withContext(Dispatchers.IO) {
      val cached = activeSession
      if (cached != null) return@withContext cached
      val file = companySessionFile ?: return@withContext null
      if (!file.exists()) return@withContext null
      try {
        val loaded = json.decodeFromString<CompanySession>(file.readText())
        activeSession = loaded
        // Attempt silent token refresh in background if online
        tryRefreshSession(loaded) ?: loaded
      } catch (_: Exception) {
        null
      }
    }

  private fun tryRefreshSession(session: CompanySession): CompanySession? {
    return try {
      val payload =
        buildJsonObject {
          put("refresh_token", session.refreshToken)
        }
      val request =
        Request.Builder()
          .url("$supabaseUrl/auth/v1/token?grant_type=refresh_token")
          .header("apikey", apiKey)
          .header("Content-Type", "application/json")
          .post(payload.toString().toRequestBody("application/json".toMediaType()))
          .build()

      httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return null
        val body = response.body?.string().orEmpty()
        val obj = json.parseToJsonElement(body).jsonObject
        val newAccess = obj["access_token"]?.jsonPrimitive?.content ?: return null
        val newRefresh = obj["refresh_token"]?.jsonPrimitive?.content ?: session.refreshToken
        val updated =
          session.copy(
            accessToken = newAccess,
            refreshToken = newRefresh,
          )
        activeSession = updated
        companySessionFile?.writeText(json.encodeToString(updated))
        updated
      }
    } catch (_: Exception) {
      null
    }
  }

  override suspend fun loginOrRegisterCompany(
    companyName: String,
    password: String,
    isRegister: Boolean,
  ): CompanySession =
    withContext(Dispatchers.IO) {
      val payload =
        buildJsonObject {
          put("action", if (isRegister) "register" else "login")
          put("company_name", companyName.trim())
          put("password", password)
        }

      val request =
        Request.Builder()
          .url("$supabaseUrl/functions/v1/company-auth")
          .header("apikey", apiKey)
          .header("Authorization", "Bearer ${authBearerToken()}")
          .header("Content-Type", "application/json")
          .post(payload.toString().toRequestBody("application/json".toMediaType()))
          .build()

      httpClient.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
          val errorMsg =
            try {
              json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content
            } catch (_: Exception) {
              null
            } ?: "Authentication failed (${response.code})"
          throw IOException(errorMsg)
        }
        val session = json.decodeFromString<CompanySession>(body)
        activeSession = session
        try {
          companySessionFile?.writeText(json.encodeToString(session))
        } catch (_: Exception) {}
        session
      }
    }

  override suspend fun signOutCompany(): Unit =
    withContext(Dispatchers.IO) {
      activeSession = null
      try {
        companySessionFile?.takeIf { it.exists() }?.delete()
      } catch (_: Exception) {}
      Unit
    }

  override suspend fun getCustomers(): List<Customer> =
    withContext(Dispatchers.IO) {
      val selectCols =
        "id,name,address,latitude,longitude,customer_locations(id,customer_id,label,address,latitude,longitude)"
      val request =
        Request.Builder()
          .url("$supabaseUrl/rest/v1/customers?select=$selectCols&order=id.asc")
          .header("apikey", apiKey)
          .header("Authorization", "Bearer ${authBearerToken()}")
          .get()
          .build()

      try {
        httpClient.newCall(request).execute().use { response ->
          val body = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            throw IOException("Failed to fetch customers (${response.code}): $body")
          }
          val customers = json.decodeFromString<List<Customer>>(body)
          // Save offline cache of customers & locations
          try {
            customersCacheFile?.writeText(body)
          } catch (_: Exception) {}
          customers
        }
      } catch (e: Exception) {
        // Fallback to local cached customers when offline!
        val cachedText =
          try {
            customersCacheFile?.takeIf { it.exists() }?.readText()
          } catch (_: Exception) {
            null
          }
        if (!cachedText.isNullOrBlank()) {
          return@withContext json.decodeFromString<List<Customer>>(cachedText)
        }
        throw e
      }
    }

  override suspend fun createCustomer(name: String): Customer =
    withContext(Dispatchers.IO) {
      val payload = json.encodeToString(CreateCustomerRequest(name = name.trim()))
      val request =
        Request.Builder()
          .url("$supabaseUrl/rest/v1/customers")
          .header("apikey", apiKey)
          .header("Authorization", "Bearer ${authBearerToken()}")
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
          .header("Authorization", "Bearer ${authBearerToken()}")
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
          .header("Authorization", "Bearer ${authBearerToken()}")
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
          .header("Authorization", "Bearer ${authBearerToken()}")
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
        return@withContext ResolvedAddress(
          formattedAddress = "Unspecified Address",
          latitude = 0.0,
          longitude = 0.0,
        )
      }
      try {
        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        val request =
          Request.Builder()
            .url("https://nominatim.openstreetmap.org/search?format=json&limit=1&q=$encoded")
            .header("User-Agent", "JobSightAndroidApp/1.0 (com.jobsight.app)")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
          val body = response.body?.string().orEmpty()
          if (response.isSuccessful) {
            val array = json.parseToJsonElement(body) as? JsonArray
            val first = array?.firstOrNull() as? JsonObject
            val lat = first?.get("lat")?.jsonPrimitive?.content?.toDoubleOrNull()
            val lon = first?.get("lon")?.jsonPrimitive?.content?.toDoubleOrNull()
            if (lat != null && lon != null) {
              return@withContext ResolvedAddress(
                formattedAddress = trimmed,
                latitude = lat,
                longitude = lon,
              )
            }
          }
        }
      } catch (_: Exception) {}

      ResolvedAddress(
        formattedAddress = trimmed,
        latitude = 0.0,
        longitude = 0.0,
      )
    }

  override suspend fun getPhotosForCustomer(customerId: Long): List<CapturedPhotoRow> =
    withContext(Dispatchers.IO) {
      // Also include any locally queued offline photos for this customer at the top!
      val localQueuedRows =
        readQueueManifest()
          .filter { it.customerId == customerId && File(it.localFilePath).exists() }
          .map { queued ->
            CapturedPhotoRow(
              id = queued.id,
              customerId = queued.customerId,
              customerLocationId = queued.customerLocationId,
              imageUrl = "file://${queued.localFilePath}",
              capturedAt = "${queued.capturedAt} (Queued Offline)",
              aiAnalysisJson = null,
            )
          }

      val request =
        Request.Builder()
          .url(
            "$supabaseUrl/rest/v1/captured_photos?customer_id=eq.$customerId&select=id,customer_id,customer_location_id,image_url,captured_at,ai_analysis_json&order=captured_at.desc,id.desc"
          )
          .header("apikey", apiKey)
          .header("Authorization", "Bearer ${authBearerToken()}")
          .get()
          .build()

      val remoteRows =
        try {
          httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
              throw IOException("Failed to fetch photos (${response.code}): $body")
            }
            json.decodeFromString<List<CapturedPhotoRow>>(body).sortedByDescending { it.id }
          }
        } catch (_: Exception) {
          emptyList()
        }

      localQueuedRows + remoteRows
    }

  override suspend fun deletePhotos(photos: List<CapturedPhotoRow>) =
    withContext(Dispatchers.IO) {
      if (photos.isEmpty()) return@withContext

      // 1. Separate any locally queued offline photos vs remote Supabase photos
      val (localPhotos, remotePhotos) = photos.partition { it.imageUrl.startsWith("file://") }

      if (localPhotos.isNotEmpty()) {
        queueMutex.withLock {
          val localIds = localPhotos.map { it.id }.toSet()
          val currentQueue = readQueueManifest()
          currentQueue
            .filter { it.id in localIds }
            .forEach { item ->
              val f = File(item.localFilePath)
              if (f.exists()) f.delete()
            }
          writeQueueManifest(currentQueue.filterNot { it.id in localIds })
        }
      }

      if (remotePhotos.isNotEmpty()) {
        val idsCsv = remotePhotos.joinToString(",") { it.id.toString() }
        val dbDeleteRequest =
          Request.Builder()
            .url("$supabaseUrl/rest/v1/captured_photos?id=in.($idsCsv)")
            .header("apikey", apiKey)
            .header("Authorization", "Bearer ${authBearerToken()}")
            .delete()
            .build()

        httpClient.newCall(dbDeleteRequest).execute().use { response ->
          if (!response.isSuccessful) {
            val body = response.body?.string().orEmpty()
            throw IOException("Failed to delete photos (${response.code}): $body")
          }
        }

        val publicPrefix = "$supabaseUrl/storage/v1/object/public/$storageBucket/"
        for (photo in remotePhotos) {
          if (photo.imageUrl.startsWith(publicPrefix)) {
            val objectPath = photo.imageUrl.removePrefix(publicPrefix)
            try {
              val storageDeleteReq =
                Request.Builder()
                  .url("$supabaseUrl/storage/v1/object/$storageBucket/$objectPath")
                  .header("apikey", apiKey)
                  .header("Authorization", "Bearer ${authBearerToken()}")
                  .delete()
                  .build()
              httpClient.newCall(storageDeleteReq).execute().close()
            } catch (_: Exception) {}
          }
        }
      }
    }

  override suspend fun downloadImageBytes(imageUrl: String): ByteArray? =
    withContext(Dispatchers.IO) {
      try {
        if (imageUrl.startsWith("file://")) {
          val localFile = File(imageUrl.removePrefix("file://"))
          return@withContext if (localFile.exists()) localFile.readBytes() else null
        }
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
          .header("Authorization", "Bearer ${authBearerToken()}")
          .get()
          .build()

      try {
        httpClient.newCall(request).execute().use { response ->
          val body = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            throw IOException("Failed to fetch search sites (${response.code}): $body")
          }
          val list = json.decodeFromString<List<SearchSite>>(body)
          try {
            searchSitesCacheFile?.writeText(body)
          } catch (_: Exception) {}
          list.ifEmpty { SearchSite.DEFAULT_SITES }
        }
      } catch (_: Exception) {
        val cachedText =
          try {
            searchSitesCacheFile?.takeIf { it.exists() }?.readText()
          } catch (_: Exception) {
            null
          }
        if (!cachedText.isNullOrBlank()) {
          return@withContext json.decodeFromString<List<SearchSite>>(cachedText)
        }
        SearchSite.DEFAULT_SITES
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
          .header("Authorization", "Bearer ${authBearerToken()}")
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
          .header("Authorization", "Bearer ${authBearerToken()}")
          .delete()
          .build()

      httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          val body = response.body?.string().orEmpty()
          throw IOException("Failed to delete search site (${response.code}): $body")
        }
      }
    }

  override suspend fun uploadPhotoOrQueueOffline(
    cacheFile: File,
    customerId: Long?,
    customerLocationId: Long?,
  ): PhotoUploadOutcome =
    withContext(Dispatchers.IO) {
      if (!cacheFile.exists()) {
        throw IOException("Cached photo file does not exist")
      }

      try {
        val insertedPhoto = uploadSingleFileToSupabase(cacheFile, customerId, customerLocationId)
        // Upload succeeded online -> delete local copy immediately!
        if (cacheFile.exists()) {
          cacheFile.delete()
        }
        PhotoUploadOutcome.UploadedOnline(insertedPhoto)
      } catch (_: IOException) {
        // Offline or network failure -> move photo into persistent offline queue & schedule WorkManager!
        val queuedCount = enqueueFileForOfflineUpload(cacheFile, customerId, customerLocationId)
        appContext?.let { PhotoUploadWorker.enqueueWhenConnected(it) }
        PhotoUploadOutcome.QueuedOffline(queuedCount)
      }
    }

  private fun uploadSingleFileToSupabase(
    file: File,
    customerId: Long?,
    customerLocationId: Long?,
  ): CapturedPhotoRow {
    val folder = customerId?.let { "customer_$it" } ?: "untagged"
    val objectPath = "$folder/${System.currentTimeMillis()}_${file.name}"

    val uploadRequest =
      Request.Builder()
        .url("$supabaseUrl/storage/v1/object/$storageBucket/$objectPath")
        .header("apikey", apiKey)
        .header("Authorization", "Bearer ${authBearerToken()}")
        .header("x-upsert", "true")
        .post(file.asRequestBody("image/jpeg".toMediaType()))
        .build()

    httpClient.newCall(uploadRequest).execute().use { uploadResponse ->
      if (!uploadResponse.isSuccessful) {
        val err = uploadResponse.body?.string().orEmpty()
        throw IOException("Storage upload failed (${uploadResponse.code}): $err")
      }
    }

    val publicImageUrl = "$supabaseUrl/storage/v1/object/public/$storageBucket/$objectPath"

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
        .header("Authorization", "Bearer ${authBearerToken()}")
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
          .header("Authorization", "Bearer ${authBearerToken()}")
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
    } catch (_: Exception) {}

    return insertedPhoto
  }

  private suspend fun enqueueFileForOfflineUpload(
    cacheFile: File,
    customerId: Long?,
    customerLocationId: Long?,
  ): Int =
    queueMutex.withLock {
      val dir = pendingDir ?: cacheFile.parentFile ?: File(".")
      val destFile = File(dir, "queued_${System.currentTimeMillis()}_${cacheFile.name}")
      if (cacheFile.absolutePath != destFile.absolutePath) {
        cacheFile.copyTo(destFile, overwrite = true)
        cacheFile.delete()
      }

      val isoFormat =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
          timeZone = TimeZone.getTimeZone("UTC")
        }
      val entry =
        QueuedPhotoUpload(
          id = System.currentTimeMillis(),
          localFilePath = destFile.absolutePath,
          customerId = customerId,
          customerLocationId = customerLocationId,
          capturedAt = isoFormat.format(Date()),
        )

      val updatedQueue = readQueueManifest() + entry
      writeQueueManifest(updatedQueue)
      updatedQueue.size
    }

  override suspend fun flushPendingUploads(): Int =
    withContext(Dispatchers.IO) {
      queueMutex.withLock {
        val currentQueue = readQueueManifest()
        if (currentQueue.isEmpty()) return@withLock 0

        val remaining = mutableListOf<QueuedPhotoUpload>()
        for (item in currentQueue) {
          val file = File(item.localFilePath)
          if (!file.exists()) {
            continue
          }
          try {
            uploadSingleFileToSupabase(
              file = file,
              customerId = item.customerId,
              customerLocationId = item.customerLocationId,
            )
            // Uploaded successfully -> delete local copy immediately!
            file.delete()
          } catch (_: Exception) {
            remaining.add(item)
          }
        }
        writeQueueManifest(remaining)
        remaining.size
      }
    }

  override suspend fun getPendingUploadCount(): Int =
    withContext(Dispatchers.IO) {
      queueMutex.withLock {
        val valid = readQueueManifest().filter { File(it.localFilePath).exists() }
        writeQueueManifest(valid)
        valid.size
      }
    }

  private fun readQueueManifest(): List<QueuedPhotoUpload> {
    val file = queueManifestFile ?: return emptyList()
    if (!file.exists()) return emptyList()
    return try {
      json.decodeFromString<List<QueuedPhotoUpload>>(file.readText())
    } catch (_: Exception) {
      emptyList()
    }
  }

  private fun writeQueueManifest(items: List<QueuedPhotoUpload>) {
    val file = queueManifestFile ?: return
    try {
      file.writeText(json.encodeToString(items))
    } catch (_: Exception) {}
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
            } catch (_: Exception) {}
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
          } catch (_: Exception) {}
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
        .header("Authorization", "Bearer ${authBearerToken()}")
        .get()
        .build()

    httpClient.newCall(request).execute().use { response ->
      if (!response.isSuccessful) return null
      val body = response.body?.string().orEmpty()
      return json.decodeFromString<List<CapturedPhotoRow>>(body).firstOrNull()
    }
  }

  override suspend fun populateDemoAiAnalysis(photoId: Long): List<HvacPartAnalysis> = emptyList()
}
