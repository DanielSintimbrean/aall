package dev.danields.aall.movent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import dev.danields.aall.automation.R
import dev.danields.aall.core.Board
import dev.danields.aall.core.BoardNotice

/** Keeps the board in the notification shade. It only polls while the screen is on, since nobody can read it otherwise. */
class MoventService : Service() {
  private val worker = HandlerThread("movent").apply { start() }
  private val handler = Handler(worker.looper)
  private val tick = Runnable { refresh() }
  private var departures: List<Departure>? = null
  private var offline = false
  private val screen = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      handler.removeCallbacks(tick)
      if (intent.action == Intent.ACTION_SCREEN_ON) handler.post(tick)
    }
  }

  override fun onCreate() {
    super.onCreate()
    registerReceiver(
      screen,
      IntentFilter().apply {
        addAction(Intent.ACTION_SCREEN_ON)
        addAction(Intent.ACTION_SCREEN_OFF)
      }
    )
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    getSystemService(NotificationManager::class.java).createNotificationChannel(channel())
    startForeground(
      ID,
      build(BoardNotice.of(departures, offline, System.currentTimeMillis())),
      ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
    )
    if (intent?.action == ACTION_STOP || !isEnabled(this)) {
      setEnabled(this, false)
      stopForeground(STOP_FOREGROUND_REMOVE)
      stopSelf()
      return START_NOT_STICKY
    }
    handler.removeCallbacks(tick)
    handler.post(tick)
    return START_STICKY
  }

  override fun onDestroy() {
    unregisterReceiver(screen)
    handler.removeCallbacks(tick)
    // Runs after any fetch in flight, which would otherwise repost the notification once it was removed.
    handler.post { getSystemService(NotificationManager::class.java).cancel(ID) }
    worker.quitSafely()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?) = null

  private fun refresh() {
    try {
      departures = Board.fetch(System.currentTimeMillis())
      offline = false
    } catch (_: Exception) {
      offline = true
    }
    getSystemService(NotificationManager::class.java)
      .notify(ID, build(BoardNotice.of(departures, offline, System.currentTimeMillis())))
    if (getSystemService(PowerManager::class.java).isInteractive) {
      // A wake during the fetch queued another tick, which would start a second polling loop.
      handler.removeCallbacks(tick)
      handler.postDelayed(tick, REFRESH_MILLIS)
    }
  }

  private fun channel() =
    NotificationChannel(CHANNEL, "Autobuses", NotificationManager.IMPORTANCE_LOW).apply {
      description = "Próximos buses en Cascada de les Moles."
      setBypassDnd(true)
      setShowBadge(false)
    }

  private fun build(notice: BoardNotice): Notification {
    val open = PendingIntent.getActivity(
      this,
      0,
      packageManager.getLaunchIntentForPackage(packageName),
      PendingIntent.FLAG_IMMUTABLE
    )
    val stop = PendingIntent.getService(
      this,
      0,
      Intent(this, MoventService::class.java).setAction(ACTION_STOP),
      PendingIntent.FLAG_IMMUTABLE
    )
    val builder = Notification.Builder(this, CHANNEL)
      .setSmallIcon(R.drawable.aall_movent)
      .setColor(Color.parseColor("#5eead4"))
      .setContentTitle(notice.title)
      .setContentText(notice.text)
      .setCategory(Notification.CATEGORY_STATUS)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setLocalOnly(true)
      .setShowWhen(false)
      .setContentIntent(open)
      .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
      .addAction(Notification.Action.Builder(null, "Parar", stop).build())
    if (notice.offline) builder.setSubText("Sin conexión")
    if (notice.lines.isNotEmpty()) {
      val style = Notification.InboxStyle().setSummaryText(notice.text)
      notice.lines.forEach(style::addLine)
      builder.setStyle(style)
    }
    return builder.build()
  }

  companion object {
    private const val ACTION_STOP = "dev.danields.aall.movent.STOP"
    private const val CHANNEL = "movent"
    private const val ID = 2
    private const val KEY = "running"
    private const val REFRESH_MILLIS = 60_000L

    private fun prefs(context: Context) = context.getSharedPreferences("movent", MODE_PRIVATE)

    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY, false)

    private fun setEnabled(context: Context, enabled: Boolean) {
      prefs(context).edit().putBoolean(KEY, enabled).apply()
    }

    fun set(context: Context, enabled: Boolean) {
      setEnabled(context, enabled)
      val intent = Intent(context, MoventService::class.java)
      if (enabled) context.startForegroundService(intent) else context.stopService(intent)
    }

    fun resume(context: Context) {
      if (isEnabled(context)) set(context, true)
    }
  }
}
