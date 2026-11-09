package dev.danields.aall.automation

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** The full-screen view shown over a blocked app. Colors follow the app's tokens in src/theme.ts. */
class BlockScreen(
  private val context: Context,
  private val app: String,
  private val notice: BlockNotice,
  private val onHome: () -> Unit,
  private val onOpen: () -> Unit
) {
  private companion object {
    val CANVAS = Color.parseColor("#0a0e0e")
    val SURFACE = Color.parseColor("#151b1b")
    val LINE = Color.parseColor("#2c3737")
    val INK = Color.parseColor("#f1f5f4")
    val MUTED = Color.parseColor("#a0aeac")
    val ACCENT = Color.parseColor("#5eead4")
    val ON_ACCENT = Color.parseColor("#04211c")
    val ACCENT_SOFT = Color.parseColor("#123a35")
  }

  private fun dp(v: Int) = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP,
    v.toFloat(),
    context.resources.displayMetrics
  )
    .toInt()

  private fun rounded(color: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = dp(radius).toFloat()
    stroke?.let { setStroke(dp(1), it) }
  }

  private fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
    TextView(context).apply {
      text = value
      textSize = size
      setTextColor(color)
      gravity = Gravity.CENTER
      if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

  private fun LinearLayout.add(
    view: View,
    top: Int = 0,
    width: Int = LinearLayout.LayoutParams.WRAP_CONTENT
  ) {
    addView(
      view,
      LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        topMargin = dp(top)
        gravity = Gravity.CENTER_HORIZONTAL
      }
    )
  }

  private fun label(): String = try {
    context.packageManager.getApplicationLabel(
      context.packageManager.getApplicationInfo(app, 0)
    ).toString()
  } catch (_: PackageManager.NameNotFoundException) {
    app
  }

  private fun icon(): Drawable? = try {
    context.packageManager.getApplicationIcon(app)
  } catch (_: PackageManager.NameNotFoundException) {
    null
  }

  /** The app's icon, dimmed, with a lock badge on its corner. */
  private fun appBadge(): View = FrameLayout(context).apply {
    val size = dp(88)
    addView(
      ImageView(context).apply {
        icon()?.let(::setImageDrawable)
        alpha = 0.55f
        colorFilter = android.graphics.ColorMatrixColorFilter(
          android.graphics.ColorMatrix().apply { setSaturation(0f) }
        )
      },
      FrameLayout.LayoutParams(dp(72), dp(72), Gravity.CENTER)
    )
    addView(
      ImageView(context).apply {
        setImageResource(R.drawable.aall_lock)
        background = rounded(ACCENT, 16).apply { setStroke(dp(3), CANVAS) }
        setPadding(dp(7), dp(7), dp(7), dp(7))
        imageTintList = ColorStateList.valueOf(ON_ACCENT)
      },
      FrameLayout.LayoutParams(dp(32), dp(32), Gravity.BOTTOM or Gravity.END)
    )
    layoutParams = LinearLayout.LayoutParams(size, size)
  }

  private fun chips(): View = LinearLayout(context).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER
    notice.routines.forEachIndexed { i, name ->
      addView(
        text(name, 13f, ACCENT, bold = true).apply {
          background = rounded(ACCENT_SOFT, 999)
          setPadding(dp(12), dp(6), dp(12), dp(6))
        },
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { if (i > 0) marginStart = dp(8) }
      )
    }
  }

  private fun button(value: String, primary: Boolean, action: () -> Unit) = Button(context).apply {
    text = value
    textSize = 16f
    setTextColor(if (primary) ON_ACCENT else INK)
    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    isAllCaps = false
    stateListAnimator = null
    minHeight = 0
    minimumHeight = 0
    val shape = if (primary) rounded(ACCENT, 999) else rounded(CANVAS, 999, LINE)
    background = RippleDrawable(
      ColorStateList.valueOf(if (primary) 0x33000000 else 0x33FFFFFF),
      shape,
      null
    )
    setPadding(dp(24), dp(16), dp(24), dp(16))
    setOnClickListener { action() }
  }

  fun build(): View {
    val center = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER
      add(appBadge())
      add(text(label(), 15f, MUTED), top = 20)
      add(text(notice.title, 28f, INK, bold = true), top = 6)
      add(
        text(notice.text, 16f, MUTED).apply { setLineSpacing(0f, 1.15f) },
        top = 12,
        width = LinearLayout.LayoutParams.MATCH_PARENT
      )
      if (notice.routines.isNotEmpty()) add(chips(), top = 24)
    }
    val actions = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      add(button("Volver al inicio", true, onHome), width = LinearLayout.LayoutParams.MATCH_PARENT)
      add(
        button("Abrir aall", false, onOpen),
        top = 12,
        width = LinearLayout.LayoutParams.MATCH_PARENT
      )
      add(
        text("Desde aall puedes pausar las restricciones 15 minutos.", 13f, MUTED).apply {
          alpha = 0.8f
        },
        top = 16,
        width = LinearLayout.LayoutParams.MATCH_PARENT
      )
    }
    val column = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      addView(
        center,
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
      )
      addView(
        actions,
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        )
      )
    }
    // Scrolls when the content is taller than the screen, as in landscape or with large text.
    return ScrollView(context).apply {
      isFillViewport = true
      setBackgroundColor(CANVAS)
      val side = dp(28)
      setPadding(side, dp(48), side, dp(32))
      addView(column)
      setOnApplyWindowInsetsListener { v, insets ->
        val bars = insets.getInsets(
          WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
        )
        v.setPadding(side, dp(48) + bars.top, side, dp(32) + bars.bottom)
        insets
      }
    }
  }
}
