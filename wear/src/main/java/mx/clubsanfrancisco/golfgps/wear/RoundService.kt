package mx.clubsanfrancisco.golfgps.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status

/**
 * Servicio en primer plano que mantiene viva la ronda mientras la pantalla
 * está apagada o el sistema regresa a la carátula. Publica una Ongoing
 * Activity: el ícono de SF Golf aparece como chip en la carátula y un toque
 * regresa a la app exactamente donde estaba. Sin configuración del usuario.
 */
class RoundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        return START_STICKY
    }

    private fun startInForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Ronda en curso", NotificationManager.IMPORTANCE_LOW)
            )
        }

        val touch = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("SF Golf")
            .setContentText("Ronda en curso")
            .setContentIntent(touch)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)

        OngoingActivity.Builder(applicationContext, NOTIF_ID, builder)
            .setStaticIcon(R.mipmap.ic_launcher)
            .setTouchIntent(touch)
            .setStatus(Status.Builder().addTemplate("Ronda en curso").build())
            .build()
            .apply(applicationContext)

        val notification: Notification = builder.build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "round"
        private const val NOTIF_ID = 1
    }
}
