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
import android.os.Looper
import android.os.PowerManager
import com.facebook.react.ReactApplication
import com.facebook.react.ReactInstanceEventListener
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactContext
import com.facebook.react.jstasks.HeadlessJsTaskConfig
import com.facebook.react.jstasks.HeadlessJsTaskContext

data class Notice(
  val title: String,
  val text: String,
  val lines: List<String>,
  val offline: Boolean
)

/** Keeps the board JS sends in the notification shade. It only ticks while the screen is on, since nobody can read it otherwise. */
class MoventService : Service() {
  private val handler = Handler(Looper.getMainLooper())
  private val tick = Runnable { tick() }
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
    startForeground(ID, build(this, notice), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
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
    getSystemService(NotificationManager::class.java).cancel(ID)
    super.onDestroy()
  }

  override fun onBind(intent: Intent?) = null

  // HeadlessJsTaskService would stop itself after each task and hold a wake lock until destroyed, so ticks start the task directly.
  private fun tick() {
    val host = (application as ReactApplication).reactHost ?: return
    val context = host.currentReactContext
    if (context != null) {
      startTask(context)
    } else {
      host.addReactInstanceEventListener(
        object : ReactInstanceEventListener {
          override fun onReactContextInitialized(context: ReactContext) {
            host.removeReactInstanceEventListener(this)
            handler.post { startTask(context) }
          }
        }
      )
      host.start()
    }
    val interactive = getSystemService(PowerManager::class.java).isInteractive
    if (interactive) handler.postDelayed(tick, REFRESH_MILLIS)
  }

  private fun startTask(context: ReactContext) {
    HeadlessJsTaskContext.getInstance(context)
      .startTask(HeadlessJsTaskConfig(TASK, Arguments.createMap(), TASK_TIMEOUT_MILLIS, true))
  }

  private fun channel() =
    NotificationChannel(CHANNEL, "Autobuses", NotificationManager.IMPORTANCE_LOW).apply {
      description = "Próximos buses en Cascada de les Moles."
      setBypassDnd(true)
      setShowBadge(false)
    }

  companion object {
    private const val ACTION_STOP = "dev.danields.aall.movent.STOP"
    private const val CHANNEL = "movent"
    private const val ID = 2
    private const val KEY = "running"
    private const val TASK = "MoventTick"
    private const val REFRESH_MILLIS = 60_000L
    private const val TASK_TIMEOUT_MILLIS = 30_000L
    private var notice = Notice("Movent", "Buscando buses…", emptyList(), false)

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

    /** Runs on the main thread, after the service has started or stopped, so a late tick never reposts a stopped board. */
    fun show(context: Context, next: Notice) {
      Handler(Looper.getMainLooper()).post {
        notice = next
        if (isEnabled(context)) {
          context.getSystemService(NotificationManager::class.java).notify(ID, build(context, next))
        }
      }
    }

    private fun build(context: Context, notice: Notice): Notification {
      val open = PendingIntent.getActivity(
        context,
        0,
        context.packageManager.getLaunchIntentForPackage(context.packageName),
        PendingIntent.FLAG_IMMUTABLE
      )
      val stop = PendingIntent.getService(
        context,
        0,
        Intent(context, MoventService::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE
      )
      val builder = Notification.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.movent_bus)
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
  }
}
