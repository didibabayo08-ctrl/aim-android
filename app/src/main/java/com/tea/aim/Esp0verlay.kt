package com.tea.aim

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import java.util.concurrent.CopyOnWriteArrayList

class EspOverlay : Service() {

    private lateinit var wm: WindowManager
    private lateinit var view: OverlayView

    companion object {
        val boxes = CopyOnWriteArrayList<Detection>()
        var target: Detection? = null
        var crosshairX = 0f
        var crosshairY = 0f
        var screenW = 0
        var screenH = 0
        var active = false
        var showEsp = true
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        active = true
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        view = OverlayView(this)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        wm.addView(view, lp)
    }

    override fun onDestroy() {
        active = false
        if (::view.isInitialized) wm.removeView(view)
        super.onDestroy()
    }

    class OverlayView(ctx: Context) : View(ctx) {
        private val strokeBox = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 4f; color = Color.GREEN
        }
        private val strokeTarget = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 5f; color = Color.RED
        }
        private val fillBox = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL; color = Color.argb(50, 0, 255, 0)
        }
        private val fillTarget = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL; color = Color.argb(60, 255, 0, 0)
        }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 30f; isFakeBoldText = true
        }
        private val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.CYAN
        }
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val tgt = target
            if (showEsp) {
                for (d in boxes) {
                    rect.set(d.left, d.top, d.right, d.bottom)
                    if (tgt != null && d === tgt) {
                        canvas.drawRect(rect, fillTarget)
                        canvas.drawRect(rect, strokeTarget)
                    } else {
                        canvas.drawRect(rect, fillBox)
                        canvas.drawRect(rect, strokeBox)
                    }
                    canvas.drawText(
                        "${d.label} ${(d.confidence * 100).toInt()}%",
                        d.left, d.top - 8f, text
                    )
                }
            }

            val cx = width * 0.5f
            val cy = height * 0.5f
            canvas.drawLine(cx - 22f, cy, cx + 22f, cy, cross)
            canvas.drawLine(cx, cy - 22f, cx, cy + 22f, cross)
            canvas.drawCircle(cx, cy, 14f, cross)

            postInvalidateOnAnimation()
        }
    }
}
