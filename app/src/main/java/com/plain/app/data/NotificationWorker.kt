package com.plain.app.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Recoge avisos con la app cerrada (cada ~15 min, el mínimo que permite Android).
 *
 * Sin Firebase: no hace falta cuenta ni google-services.json. Cuando se cree el
 * proyecto de Firebase se puede añadir FCM para que lleguen al segundo; este
 * worker seguiría como respaldo.
 */
class NotificationWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        AuthManager.init(applicationContext)
        if (AuthManager.getUserToken() == null) return Result.success()
        PlainNotifier.ensureChannels(applicationContext)
        val since = PlainNotifier.lastShownId(applicationContext)
        return try {
            val resp = ApiClient.service.getNotifications(sinceId = since, limit = 20)
            if (!resp.isSuccessful) {
                // 401 tras intentar renovar = sesión cerrada: no reintentar en bucle
                return if (resp.code() == 401) Result.success() else Result.retry()
            }
            val body = resp.body() ?: return Result.success()
            RealtimeClient.setUnread(body.unread)
            val nuevos = body.items.filter { !it.read }.sortedBy { it.id }
            if (since == 0) {
                // Primera vez en este móvil: no vomitar el historial entero en la barra
                body.items.maxOfOrNull { it.id }?.let { PlainNotifier.setLastShownId(applicationContext, it) }
            } else {
                nuevos.forEach { PlainNotifier.show(applicationContext, it) }
                body.items.maxOfOrNull { it.id }?.let { PlainNotifier.setLastShownId(applicationContext, it) }
            }
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC = "plain-avisos"
        private const val NOW = "plain-avisos-ahora"

        private val red = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<NotificationWorker>(15, TimeUnit.MINUTES)
                .setConstraints(red)
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun runNow(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<NotificationWorker>().setConstraints(red).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, req)
        }

        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(PERIODIC)
        }
    }
}
