package dev.danields.aall.automation
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AutomationReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action == StatusNotifier.ACTION_DISMISSED) {
      StatusNotifier.dismissed(Store(context).prefs, intent.getStringExtra(StatusNotifier.KEY))
      return
    }
    // Android 17 sends the power action before it updates the sticky battery status.
    val plugged = when (intent.action) {
      Intent.ACTION_POWER_CONNECTED -> true
      Intent.ACTION_POWER_DISCONNECTED -> false
      else -> null
    }
    Engine(context).reconcile(plugged)
  }
}
