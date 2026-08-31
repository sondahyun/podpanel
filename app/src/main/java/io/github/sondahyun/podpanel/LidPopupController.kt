package io.github.sondahyun.podpanel

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import io.github.sondahyun.podpanel.protocol.PodsStatus

/** A short-lived app overlay shown after a new lid-open advertisement. */
class LidPopupController(private val context: Context) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var view: View? = null

    fun show(status: PodsStatus) {
        if (!Settings.canDrawOverlays(context)) return
        dismissLater()

        val next = makeView(status)
        view?.let { runCatching { windowManager.removeView(it) } }
        view = next
        runCatching {
            windowManager.addView(next, WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.BOTTOM
                y = 32.dp
            })
        }.onFailure { view = null }
        dismissLater()
    }

    fun close() {
        handler.removeCallbacksAndMessages(null)
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
    }

    private fun dismissLater() {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(::close, DISMISS_AFTER_MS)
    }

    private fun makeView(status: PodsStatus): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(24.dp, 20.dp, 24.dp, 20.dp)
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = 28.dp.toFloat()
            setStroke(1.dp, 0x22000000)
        }
        elevation = 20.dp.toFloat()
        setOnClickListener {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            close()
        }

        addView(ImageView(context).apply {
            setImageResource(R.drawable.pods_product_hero)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            76.dp,
        ))
        addView(label(status.modelName, 20f, Color.BLACK))
        addView(label(context.getString(R.string.lid_popup_opened), 14f, 0xFF6B6B6B.toInt()).apply {
            setPadding(0, 4.dp, 0, 16.dp)
        })
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
            addView(battery(context.getString(R.string.left), status.left.percent, status.left.charging), weight())
            addView(battery(context.getString(R.string.right), status.right.percent, status.right.charging), weight())
            addView(battery(context.getString(R.string.case_), status.case.percent, status.case.charging), weight())
        })
    }

    private fun battery(name: String, percent: Int?, charging: Boolean) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        addView(label(percent?.let { "$it%${if (charging) " ⚡" else ""}" } ?: "–", 26f, Color.BLACK))
        addView(label(name, 13f, 0xFF6B6B6B.toInt()))
    }

    private fun label(text: String, size: Float, color: Int) = TextView(context).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
    }

    private fun weight() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    private val Int.dp get() = (this * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val DISMISS_AFTER_MS = 6_000L
    }
}
