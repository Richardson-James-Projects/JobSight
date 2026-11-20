package com.jobsight.app.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class PhotoUploadWorker(
  appContext: Context,
  workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    val repository = DefaultDataRepository(applicationContext)
    return try {
      val remaining = repository.flushPendingUploads()
      if (remaining == 0) {
        Result.success()
      } else {
        Result.retry()
      }
    } catch (_: Exception) {
      Result.retry()
    }
  }

  companion object {
    private const val WORK_NAME = "jobsight_offline_photo_upload_work"

    fun enqueueWhenConnected(context: Context) {
      val constraints =
        Constraints.Builder()
          .setRequiredNetworkType(NetworkType.CONNECTED)
          .build()

      val workRequest =
        OneTimeWorkRequestBuilder<PhotoUploadWorker>()
          .setConstraints(constraints)
          .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
          .build()

      WorkManager.getInstance(context.applicationContext)
        .enqueueUniqueWork(
          WORK_NAME,
          ExistingWorkPolicy.REPLACE,
          workRequest,
        )
    }
  }
}
