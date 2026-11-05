package dev.danields.aall.movent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MoventReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    MoventService.resume(context)
  }
}
