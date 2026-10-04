package com.tea.aim

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class ScreenCaptureService : Service() {

    private var projection: MediaProjection? = null
    private var vDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    private lateinit var detector: DetectorEngine
    private lateinit var aim: AimController
    private val selector = TargetSelector()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val busy = AtomicBoolean(false)

    private var screenW = 0
    private var screenH = 0

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        const val CH_ID = "aim_ch"
        const val NOTIF_ID = 7777
        var running = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        detector = DetectorEngine(this)
        aim = AimController(this)
        aim.load()
        thread = HandlerThread("cap").also { it.start() }
        handler = Handler(thread!!.looper)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotif())

        val code = intent?.getIntExtra(EXTRA_CODE, -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)

        if (code == -1 || data == null) { stopSelf(); return START_NOT_STICKY }

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(code, data)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
        }, handler)

        val metrics = DisplayMetrics()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)

        screenW = metrics.widthPixels
        screenH = metrics.heightPixels
        EspOverlay.screenW = screenW
        EspOverlay.screenH = screenH

        val capW = screenW / 2
        val capH = screenH / 2

        reader = ImageReader.newInstance(capW, capH, PixelFormat.RGBA_8888, 2)

        vDisplay = projection?.createVirtualDisplay(
            "cap",
            capW, capH, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader?.surface, null, handler
        )

        reader?.setOnImageAvailableListener({ r ->
            if (busy.getAndSet(true)) {
                r.acquireLatestImage()?.close()
                return@setOnImageAvailableListener
            }
            val img = r.acquireLatestImage() ?: run {
                busy.set(false); return@setOnImageAvailableListener
            }
            scope.launch {
                try { process(img) }
                catch (_: Throwable) {}
                finally { img.close(); busy.set(false) }
            }
        }, handler)

        running = true
        return START_STICKY
    }

    private fun process(image: Image) {
        val bmp = imageToBitmap(image)
        val dets = detector.detect(bmp)

        EspOverlay.boxes.clear()
        EspOverlay.boxes.addAll(dets)

        if (!aim.enabled) { EspOverlay.target = null; return }
        if (!TouchInjector.ready) { EspOverlay.target = null; return }

        selector.fov = aim.fov
        val tgt = selector.pick(dets, screenW, screenH) ?: run {
            EspOverlay.target = null
            return
        }

        EspOverlay.target = tgt

        val scaleX = screenW.toFloat() / bmp.width
        val scaleY = screenH.toFloat() / bmp.height
        val (ax, ay) = selector.aimPoint(tgt, bmp.width, bmp.height)
        val tx = ax * scaleX
        val ty = ay * scaleY

        val locked = aim.isLocked(tgt, screenW, screenH)

        if (!locked) {
            val (dx, dy) = aim.computeDelta(screenW, screenH, tx, ty)
            TouchInjector.instance?.dragWithMagnitude(dx, dy)
        } else if (aim.autoFire && aim.shouldFire(System.currentTimeMillis(), true)) {
            val cx = screenW * 0.5f
            val cy = screenH * 0.5f
            TouchInjector.instance?.tap(cx, cy + 260f)
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buf: ByteBuffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride   = plane.rowStride
        val rowPad      = rowStride - pixelStride * image.width

        val bmp = Bitmap.createBitmap(
            image.width + rowPad / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bmp.copyPixelsFromBuffer(buf)
        val cropped = Bitmap.createBitmap(bmp, 0, 0, image.width, image.height)
        bmp.recycle()
        return cropped
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CH_ID, "AIM Capture", NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    private fun buildNotif(): Notification =
        NotificationCompat.Builder(this, CH_ID)
            .setContentTitle("AIM active")
            .setContentText("Screen capture running")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        running = false
        scope.cancel()
        reader?.close()
        vDisplay?.release()
        projection?.stop()
        detector.close()
        thread?.quitSafely()
        super.onDestroy()
    }
}
