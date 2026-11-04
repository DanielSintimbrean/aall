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
  private fun config() = JSONObject(prefs.getString("config", "{\"version\":1,\"focusMinutes\":50,\"routines\":[]}")!!)
  private fun boot() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
  fun remaining(key: String, now: Long = System.currentTimeMillis()): Long {
    if (!prefs.contains(key)) return 0
    val timer = JSONObject(prefs.getString(key, "{}")!!)
    val bounded = Rules.remaining(timer.getInt("boot")==boot(),timer.getLong("elapsed"),SystemClock.elapsedRealtime(),timer.getLong("wall"),now,timer.getLong("duration"))
    if(timer.getInt("boot") != boot()) put(key,timer.put("boot",boot()).put("elapsed",SystemClock.elapsedRealtime()+bounded).toString())
    return bounded
  }
  private fun timer(key: String, duration: Long) { put(key, JSONObject().put("boot",boot()).put("elapsed",SystemClock.elapsedRealtime()+duration).put("wall",System.currentTimeMillis()+duration).put("duration",duration).toString()) }
  fun setConfig(raw: String) { val c = JSONObject(raw); Rules.validate(c); put("config",raw); reconcile() }
  fun initialize(raw: String) { if(!prefs.contains("config")) setConfig(raw) }
  private fun effective(r: JSONObject, now: Long): Pair<JSONObject, LocalDate?> {
    val raw = prefs.getString("today.${r.getString("id")}", null) ?: return r to null
    val o = JSONObject(raw)
    if (now >= o.getLong("until")) { prefs.edit().remove("today.${r.getString("id")}").commit(); return r to null }
    return o.getJSONObject("routine") to LocalDate.parse(o.getString("date"))
  }
  fun overrideToday(id: String, raw: String) {
    val c = config(); val rs = c.getJSONArray("routines"); val r = JSONObject(raw)
    require(r.getString("id") == id && id != "focus")
    for(i in 0 until rs.length()) if(rs.getJSONObject(i).getString("id") == id) rs.put(i,r)
    Rules.validate(c)
    val day = LocalDate.now(zone); var until = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val ws = r.getJSONArray("windows")
    for(i in 0 until ws.length()) until = maxOf(until, Rules.interval(ws.getJSONObject(i),day,zone).second)
    put("today.$id", JSONObject().put("routine",r).put("date",day.toString()).put("until",until).toString()); reconcile()
  }
  fun book(date: String) { require(Rules.canBook(date,System.currentTimeMillis(),zone)) { "Book at least 72 elapsed hours before midnight." }; val days = daysOff().toMutableSet(); days.add(date); put("daysOff",JSONArray(days.toList()).toString()); reconcile() }
  fun cancel(date: String) { put("daysOff",JSONArray(daysOff().filter{it != date}).toString()); reconcile() }
  private fun daysOff(): List<String> { val a = JSONArray(prefs.getString("daysOff","[]")); return (0 until a.length()).map{a.getString(it)} }
  fun focus() { val c = config(); val rs=c.getJSONArray("routines"); require((0 until rs.length()).any{rs.getJSONObject(it).let{r->r.getString("id")=="focus" && r.getBoolean("enabled")}}) { "Enable Focus first." }; timer("focus",c.getLong("focusMinutes")*60000); reconcile() }
  fun challenge(): String { if(!prefs.contains("challenge")) { val (expression,answer)=Rules.challenge(); put("challenge",JSONObject().put("expression",expression).put("answer",answer).toString()) }; return JSONObject(prefs.getString("challenge","{}")!!).getString("expression") }
  fun solve(answer: String): Boolean { val raw = prefs.getString("challenge",null) ?: return false; if(!Rules.verifies(answer,JSONObject(raw).getInt("answer"))) return false; prefs.edit().remove("challenge").commit(); timer("pause",900000); reconcile(); return true }
  data class Evaluation(val routines: List<JSONObject>, val dayOff: Boolean, val paused: Boolean)
  fun evaluate(now: Long): Evaluation {
    val dayOff = daysOff().contains(Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString())
    val paused = remaining("pause",now)>0
    val rs=config().getJSONArray("routines"); val active= mutableListOf<JSONObject>()
    for(i in 0 until rs.length()) {
      val (r,anchor)=effective(rs.getJSONObject(i),now); if(!r.getBoolean("enabled")) continue
      val id=r.getString("id")
      val running = Rules.requests(id,Rules.active(r,now,zone,anchor),remaining("focus",now),prefs.getLong("bedtimeUntil",0),now)
      if(running) active.add(r)
    }
    return Evaluation(if(Rules.suppress(dayOff,remaining("pause",now))) emptyList() else active,dayOff,paused)
  }
  fun charging() {
    val now=System.currentTimeMillis(); val today=LocalDate.now(zone); val rs=config().getJSONArray("routines")
    for(i in 0 until rs.length()) {
      val (r,anchor)=effective(rs.getJSONObject(i),now)
      if(r.getString("id")!="bedtime" || !r.getBoolean("enabled")) continue
      val w=r.getJSONArray("windows").getJSONObject(0)
      val early=today.atTime(LocalTime.parse(r.getString("earlyStart"))).atZone(zone).toInstant().toEpochMilli()
      val (start,end)=Rules.interval(w,today,zone)
      val days=w.getJSONArray("days")
      if(now in early until start && (anchor==today || (0 until days.length()).any{days.getInt(it)==today.dayOfWeek.value})) prefs.edit().putLong("bedtimeUntil",end).commit()
    }
  }
  fun permissions() = JSONObject().put("accessibility", AallAccessibilityService.instance != null).put("dnd",notification.isNotificationPolicyAccessGranted).put("alarms",alarm.canScheduleExactAlarms()).put("grayscale",context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")==PackageManager.PERMISSION_GRANTED)
  private fun contains(r: JSONObject,key: String,pkg: String): Boolean { val a=r.getJSONArray(key); return (0 until a.length()).any{a.getString(it)==pkg} }
  fun blocked(pkg: String, e: Evaluation): Boolean {
    val protected = setOf(context.packageName,"com.android.settings","com.android.systemui","com.google.android.dialer","com.samsung.android.dialer","com.android.dialer","com.android.server.telecom","com.android.emergency","com.android.permissioncontroller","com.google.android.permissioncontroller","com.google.android.packageinstaller")
    if(pkg in protected || pkg == context.getSystemService(android.telecom.TelecomManager::class.java)?.defaultDialerPackage) return false
    val launcher=context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0)?.activityInfo?.packageName
    if(pkg==launcher) return false
    return e.routines.any{if(it.getString("id")=="bedtime") !contains(it,"apps",pkg) else it.getString("id")!="grayscale" && contains(it,"apps",pkg)}
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
    val dnd=e.routines.any{it.getString("id") in listOf("bedtime","focus")}
    try { if(permissions.getBoolean("dnd") && (dnd != prefs.getBoolean("dndOwned",false))) { notification.setInterruptionFilter(if(dnd) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL); prefs.edit().putBoolean("dndOwned",dnd).commit() } } catch(ex: Exception) { errors.put("DND: ${ex.message}") }
    val pkg=AallAccessibilityService.instance?.foreground ?: ""
    val gray=e.routines.any{it.getString("id")!="access"} && e.routines.none{contains(it,"colorApps",pkg)}
    try { if(permissions.getBoolean("grayscale")) grayscale((gray || remaining("grayTest")>0) && !e.dayOff && !e.paused) } catch(ex: Exception) { errors.put("Grayscale: ${ex.message}") }
    AallAccessibilityService.instance?.update(this,e)
    val next=nextChange(now)
    val pending=PendingIntent.getBroadcast(context,0,Intent(context,AutomationReceiver::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    if(alarm.canScheduleExactAlarms()) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,pending) else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,pending)
    val names=e.routines.map{it.getString("id")}
    if(names.any{it!="grayscale"} && !permissions.getBoolean("accessibility")) errors.put("App blocking needs accessibility access.")
    if(dnd && !permissions.getBoolean("dnd")) errors.put("DND access missing.")
    if(gray && !permissions.getBoolean("grayscale")) errors.put("Grayscale needs the ADB grant.")
    if(!permissions.getBoolean("alarms")) errors.put("Exact alarm access missing; changes may be delayed.")
    return JSONObject().put("config",config()).put("active",JSONArray(names)).put("dayOff",e.dayOff).put("daysOff",JSONArray(daysOff())).put("focusUntil",now+remaining("focus")).put("pauseUntil",now+remaining("pause")).put("nextChange",next).put("permissions",permissions).put("errors",errors)
  }
  private fun nextChange(now: Long): Long {
    val candidates=mutableListOf<Long>(); val today=LocalDate.now(zone)
    candidates.add(today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
    for(key in listOf("focus","pause","grayTest")) if(remaining(key)>0) candidates.add(now+remaining(key))
    candidates.add(prefs.getLong("bedtimeUntil",0))
    val rs=config().getJSONArray("routines")
    for(i in 0 until rs.length()) {
      val base=rs.getJSONObject(i); val (r,anchor)=effective(base,now)
      prefs.getString("today.${r.getString("id")}",null)?.let{candidates.add(JSONObject(it).getLong("until"))}
      if(r.getString("id")=="bedtime" && r.getBoolean("enabled")) candidates.add(today.atTime(LocalTime.parse(r.getString("earlyStart"))).atZone(zone).toInstant().toEpochMilli())
      for(rule in listOf(r,base)) {
        if(!rule.getBoolean("enabled")) continue
        val ws=rule.getJSONArray("windows")
        for(j in 0 until ws.length()) for(day in -1L..8L) {
          val d=today.plusDays(day); val w=ws.getJSONObject(j); val days=w.getJSONArray("days")
          if(anchor==d || (0 until days.length()).any{days.getInt(it)==d.dayOfWeek.value}) { val (start,end)=Rules.interval(w,d,zone); candidates.add(start); candidates.add(end) }
        }
      }
    }
    return candidates.filter{it>now+100}.minOrNull() ?: now+60000
  }
}
