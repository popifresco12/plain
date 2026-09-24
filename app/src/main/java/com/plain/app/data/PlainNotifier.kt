package com.plain.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.plain.app.MainActivity
import com.plain.app.R

/**
 * Avisos del sistema (barra de notificaciones).
 *
 * Llegan por dos vías que pueden solaparse: el WebSocket (app abierta) y el
 * NotificationWorker (cada ~15 min, app cerrada). Para no duplicar, se recuerda
 * el último id mostrado y todo lo que no sea mayor se ignora.
 */
object PlainNotifier {
    const val CH_CHAT = "plain_chat"
    const val CH_GROUPS = "plain_quedadas"
    const val CH_MATCH = "plain_match"

    const val EXTRA_KIND = "plain_nav_kind"
    const val EXTRA_GROUP_ID = "plain_group_id"
    const val EXTRA_GROUP_TITLE = "plain_group_title"
    const val EXTRA_PLAN_ID = "plain_plan_id"

    private const val PREFS = "plain_notifs"
    private const val KEY_LAST = "last_shown_id"

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_CHAT, "Mensajes de quedadas", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Mensajes nuevos en los chats de tus quedadas" }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_GROUPS, "Quedadas", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Alguien se une, te aceptan o se crea una quedada de un plan que te gusta" }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_MATCH, "Match", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Personas a las que les gusta el mismo plan que a ti" }
        )
    }

    fun lastShownId(ctx: Context): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_LAST, 0)

    fun setLastShownId(ctx: Context, id: Int) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (id > prefs.getInt(KEY_LAST, 0)) prefs.edit().putInt(KEY_LAST, id).apply()
    }

    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Muestra el aviso si es nuevo. Devuelve true si llegó a la barra. */
    @Synchronized
    fun show(ctx: Context, n: NotificationItem): Boolean {
        if (n.read || n.id <= lastShownId(ctx)) return false
        setLastShownId(ctx, n.id)
        val groupId = n.intData("group_id")
        // Con ese chat abierto en pantalla, el mensaje ya se está viendo
        if (n.kind == "message" && groupId != null && groupId == RealtimeClient.activeGroupId) return false
        if (!canPost(ctx)) return false

        val channel = when (n.kind) {
            "message" -> CH_CHAT
            "match" -> CH_MATCH
            else -> CH_GROUPS
        }
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_KIND, n.kind)
            groupId?.let { putExtra(EXTRA_GROUP_ID, it) }
            n.strData("group_title")?.let { putExtra(EXTRA_GROUP_TITLE, it) }
            n.intData("plan_id")?.let { putExtra(EXTRA_PLAN_ID, it) }
        }
        // Mismo id de sistema para lo agrupado: un chat = una notificación que se actualiza
        val systemId = (n.collapseKey ?: "n${n.id}").hashCode()
        val pending = PendingIntent.getActivity(
            ctx, systemId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_plain)
            .setContentTitle(n.title)
            .setContentText(n.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(
                if (channel == CH_CHAT) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT
            )
            .build()
        return try {
            NotificationManagerCompat.from(ctx).notify(systemId, notif)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
