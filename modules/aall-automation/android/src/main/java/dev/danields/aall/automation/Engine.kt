package dev.danields.aall.automation

import android.app.*
import android.content.*
import android.os.*
import android.provider.Settings
import android.content.pm.PackageManager
import org.json.*
import java.time.*

class Engine(private val context: Context) {
  private val prefs = context.getSharedPreferences("automation", Context.MODE_PRIVATE)
  private val zone get() = ZoneId.systemDefault()
  private val alarm get() = context.getSystemService(AlarmManager::class.java)
  private val notification get() = context.getSystemService(NotificationManager::class.java)
  private fun put(key: String, value: String) { check(prefs.edit().putString(key,value).commit()) }
  private fun config() = prefs.getString("config",null)?.let(Config::parse) ?: Config.EMPTY
  private fun boot() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
  fun remaining(key: String, now: Long = System.currentTimeMillis()): Long {
    if (!prefs.contains(key)) return 0
    val timer = JSONObject(prefs.getString(key, "{}")!!)
    val bounded = Rules.remaining(timer.getInt("boot")==boot(),timer.getLong("elapsed"),SystemClock.elapsedRealtime(),timer.getLong("wall"),now,timer.getLong("duration"))
    if(timer.getInt("boot") != boot()) put(key,timer.put("boot",boot()).put("elapsed",SystemClock.elapsedRealtime()+bounded).toString())
    return bounded
  }
  private fun timer(key: String, duration: Long) { put(key, JSONObject().put("boot",boot()).put("elapsed",SystemClock.elapsedRealtime()+duration).put("wall",System.currentTimeMillis()+duration).put("duration",duration).toString()) }
  fun setConfig(raw: String) { put("config",Config.parse(raw).toJson().toString()); reconcile() }
  fun initialize(raw: String) { if(!prefs.contains("config")) setConfig(raw) }
  private fun effective(r: Routine, now: Long): Pair<Routine, LocalDate?> {
    val key = "today.${r.kind.id}"
    val o = JSONObject(prefs.getString(key, null) ?: return r to null)
    val routine = Routine.parse(o.getJSONObject("routine")); val date = LocalDate.parse(o.getString("date"))
    val until = Rules.overrideExpiry(routine,date,zone)
    if(until != o.getLong("until")) put(key,o.put("until",until).toString())
    if (now >= until) { prefs.edit().remove(key).commit(); return r to null }
    return routine to date
  }
  fun overrideToday(id: String, raw: String) {
    val r = Routine.parse(JSONObject(raw))
    require(r.kind.id == id && r.kind != RoutineKind.FOCUS)
    config().with(r).validated()
    val day = LocalDate.now(zone); val until = Rules.overrideExpiry(r,day,zone)
    put("today.$id", JSONObject().put("routine",r.toJson()).put("date",day.toString()).put("until",until).toString()); reconcile()
  }
  fun book(date: String) { require(Rules.canBook(date,System.currentTimeMillis(),zone)) { "Book at least 72 elapsed hours before midnight." }; val days = daysOff().toMutableSet(); days.add(date); put("daysOff",JSONArray(days.toList()).toString()); reconcile() }
  fun cancel(date: String) { put("daysOff",JSONArray(daysOff().filter{it != date}).toString()); reconcile() }
  private fun daysOff(): List<String> { val a = JSONArray(prefs.getString("daysOff","[]")); return (0 until a.length()).map{a.getString(it)} }
  fun focus() { val c = config(); require(c.routines.any{it.kind==RoutineKind.FOCUS && it.enabled}) { "Enable Focus first." }; timer("focus",c.focusMinutes*60000L); reconcile() }
  fun challenge(): String { if(!prefs.contains("challenge")) { val (expression,answer)=Rules.challenge(); put("challenge",JSONObject().put("expression",expression).put("answer",answer).toString()) }; return JSONObject(prefs.getString("challenge","{}")!!).getString("expression") }
  fun solve(answer: String): Boolean { val raw = prefs.getString("challenge",null) ?: return false; if(!Rules.verifies(answer,JSONObject(raw).getInt("answer"))) return false; prefs.edit().remove("challenge").commit(); timer("pause",900000); reconcile(); return true }
  data class Evaluation(val routines: List<Routine>, val dayOff: Boolean, val paused: Boolean)
  fun evaluate(now: Long): Evaluation {
    val dayOff = daysOff().contains(Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString())
    val paused = remaining("pause",now)>0
    val active= mutableListOf<Routine>()
    for(base in config().routines) {
      val (r,anchor)=effective(base,now); if(!r.enabled) continue
      if(r.kind==RoutineKind.BEDTIME) prefs.getString("bedtimeDate",null)?.let { date ->
        val end=Rules.interval(r.windows[0],LocalDate.parse(date),zone).second
        prefs.edit().putLong("bedtimeUntil",end).apply()
        if(end<=now) prefs.edit().remove("bedtimeDate").apply()
      }
      val running = Rules.requests(r.kind,Rules.active(r,now,zone,anchor),remaining("focus",now),prefs.getLong("bedtimeUntil",0),now)
      if(running) active.add(r)
    }
    return Evaluation(if(Rules.suppress(dayOff,remaining("pause",now))) emptyList() else active,dayOff,paused)
  }
  fun charging() {
    val now=System.currentTimeMillis(); val today=LocalDate.now(zone)
    for(base in config().routines) {
      val (r,anchor)=effective(base,now)
      if(r.kind!=RoutineKind.BEDTIME || !r.enabled) continue
      val w=r.windows[0]
      val early=today.atTime(r.earlyStart).atZone(zone).toInstant().toEpochMilli()
      val (start,end)=Rules.interval(w,today,zone)
      if(now in early until start && Rules.runsOn(w,today,anchor)) prefs.edit().putLong("bedtimeUntil",end).putString("bedtimeDate",today.toString()).commit()
    }
  }
  fun permissions() = JSONObject().put("accessibility", AallAccessibilityService.instance != null).put("dnd",notification.isNotificationPolicyAccessGranted).put("alarms",alarm.canScheduleExactAlarms()).put("grayscale",context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")==PackageManager.PERMISSION_GRANTED)
  fun blocked(pkg: String, e: Evaluation): Boolean {
    val protected = setOf(context.packageName,"com.android.settings","com.android.systemui","com.google.android.dialer","com.samsung.android.dialer","com.android.dialer","com.android.server.telecom","com.android.emergency","com.android.permissioncontroller","com.google.android.permissioncontroller","com.google.android.packageinstaller")
    if(pkg in protected || pkg == context.getSystemService(android.telecom.TelecomManager::class.java)?.defaultDialerPackage) return false
    val launcher=context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0)?.activityInfo?.packageName
    if(pkg==launcher) return false
    return e.routines.any{if(it.kind==RoutineKind.BEDTIME) pkg !in it.apps else it.kind!=RoutineKind.GRAYSCALE && pkg in it.apps}
  }
  private fun grayscale(on: Boolean) {
    val resolver=context.contentResolver
    val enabled=Settings.Secure.getInt(resolver,"accessibility_display_daltonizer_enabled",0)
    val mode=Settings.Secure.getInt(resolver,"accessibility_display_daltonizer",-1)
    if(on && !prefs.getBoolean("grayOwned",false)) {
      prefs.edit().putInt("grayBeforeEnabled",enabled).putInt("grayBeforeMode",mode).putBoolean("grayOwned",true).commit()
      check(Settings.Secure.putInt(resolver,"accessibility_display_daltonizer",0))
      check(Settings.Secure.putInt(resolver,"accessibility_display_daltonizer_enabled",1))
    } else if(!on && prefs.getBoolean("grayOwned",false)) {
      if(enabled==1 && mode==0) { Settings.Secure.putInt(resolver,"accessibility_display_daltonizer",prefs.getInt("grayBeforeMode",-1)); Settings.Secure.putInt(resolver,"accessibility_display_daltonizer_enabled",prefs.getInt("grayBeforeEnabled",0)) }
      prefs.edit().putBoolean("grayOwned",false).commit()
    }
  }
  fun testGray(enabled: Boolean) { if(enabled) timer("grayTest",10000) else prefs.edit().remove("grayTest").commit(); reconcile() }
  fun reconcile(): JSONObject {
    val battery=context.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    if ((battery?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED,0) ?: 0) != 0) charging()
    val now=System.currentTimeMillis(); val e=evaluate(now); val errors=JSONArray(); val permissions=permissions()
    val dnd=e.routines.any{it.kind==RoutineKind.BEDTIME || it.kind==RoutineKind.FOCUS}
    try { if(permissions.getBoolean("dnd") && (dnd != prefs.getBoolean("dndOwned",false))) { notification.setInterruptionFilter(if(dnd) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL); prefs.edit().putBoolean("dndOwned",dnd).commit() } } catch(ex: Exception) { errors.put("DND: ${ex.message}") }
    val pkg=AallAccessibilityService.instance?.foreground ?: ""
    val gray=e.routines.any{Rules.grayRequest(it.kind,pkg in it.colorApps)}
    try { if(permissions.getBoolean("grayscale")) grayscale((gray || remaining("grayTest")>0) && !e.dayOff && !e.paused) } catch(ex: Exception) { errors.put("Grayscale: ${ex.message}") }
    AallAccessibilityService.instance?.update(this,e)
    val next=nextChange(now)
    val pending=PendingIntent.getBroadcast(context,0,Intent(context,AutomationReceiver::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    if(alarm.canScheduleExactAlarms()) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,pending) else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,pending)
    val names=e.routines.map{it.kind.id}
    if(e.routines.any{it.kind!=RoutineKind.GRAYSCALE} && !permissions.getBoolean("accessibility")) errors.put("App blocking needs accessibility access.")
    if(dnd && !permissions.getBoolean("dnd")) errors.put("DND access missing.")
    if(gray && e.routines.any{it.colorApps.isNotEmpty()} && !permissions.getBoolean("accessibility")) errors.put("Color exceptions need accessibility access.")
    if(gray && !permissions.getBoolean("grayscale")) errors.put("Grayscale needs the ADB grant.")
    if(!permissions.getBoolean("alarms")) errors.put("Exact alarm access missing; changes may be delayed.")
    return JSONObject().put("config",config().toJson()).put("active",JSONArray(names)).put("dayOff",e.dayOff).put("daysOff",JSONArray(daysOff())).put("focusUntil",now+remaining("focus")).put("pauseUntil",now+remaining("pause")).put("nextChange",next).put("permissions",permissions).put("errors",errors)
  }
  private fun nextChange(now: Long): Long {
    val candidates=mutableListOf<Long>(); val today=LocalDate.now(zone)
    candidates.add(today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
    for(key in listOf("focus","pause","grayTest")) if(remaining(key)>0) candidates.add(now+remaining(key))
    candidates.add(prefs.getLong("bedtimeUntil",0))
    for(base in config().routines) {
      val (r,anchor)=effective(base,now)
      prefs.getString("today.${r.kind.id}",null)?.let{candidates.add(JSONObject(it).getLong("until"))}
      if(r.kind==RoutineKind.BEDTIME && r.enabled) candidates.add(today.atTime(r.earlyStart).atZone(zone).toInstant().toEpochMilli())
      for(rule in listOf(r,base)) {
        if(!rule.enabled) continue
        for(w in rule.windows) for(day in -1L..8L) {
          val d=today.plusDays(day)
          if(Rules.runsOn(w,d,anchor)) { val (start,end)=Rules.interval(w,d,zone); candidates.add(start); candidates.add(end) }
        }
      }
    }
    return candidates.filter{it>now+100}.minOrNull() ?: now+60000
  }
}
