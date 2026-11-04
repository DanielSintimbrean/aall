package dev.danields.aall.automation
import android.content.*

class AutomationReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val engine = Engine(context)
    if (intent.action == Intent.ACTION_POWER_CONNECTED) engine.charging()
    engine.reconcile()
  }
}
