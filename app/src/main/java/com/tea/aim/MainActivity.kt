package com.tea.aim

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.SeekBar
import android.widget.Switch
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var mpm: MediaProjectionManager
    private lateinit var aim: AimController

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data != null) {
            val i = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_CODE, res.resultCode)
                putExtra(ScreenCaptureService.EXTRA_DATA, res.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(i) else startService(i)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        aim = AimController(this).also { it.load() }

        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                ))
            }
        }

        findViewById<Button>(R.id.btnAccess).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (!EspOverlay.active) startService(Intent(this, EspOverlay::class.java))
            captureLauncher.launch(mpm.createScreenCaptureIntent())
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, ScreenCaptureService::class.java))
            stopService(Intent(this, EspOverlay::class.java))
        }

        findViewById<SeekBar>(R.id.sbFov).apply {
            progress = aim.fov.toInt()
            setOnSeekBarChangeListener(simple { p, _ -> aim.fov = p.toFloat(); aim.save() })
        }
        findViewById<SeekBar>(R.id.sbSmooth).apply {
            progress = (aim.smoothness * 100).toInt()
            setOnSeekBarChangeListener(simple { p, _ -> aim.smoothness = p / 100f; aim.save() })
        }
        findViewById<SeekBar>(R.id.sbDelay).apply {
            progress = aim.triggerDelay.toInt()
            setOnSeekBarChangeListener(simple { p, _ -> aim.triggerDelay = p.toLong(); aim.save() })
        }

        findViewById<Switch>(R.id.swEsp).apply {
            isChecked = EspOverlay.showEsp
            setOnCheckedChangeListener { _, b -> EspOverlay.showEsp = b }
        }
        findViewById<Switch>(R.id.swAim).apply {
            isChecked = aim.enabled
            setOnCheckedChangeListener { _, b -> aim.enabled = b; aim.save() }
        }
        findViewById<Switch>(R.id.swAutoFire).apply {
            isChecked = aim.autoFire
            setOnCheckedChangeListener { _, b -> aim.autoFire = b; aim.save() }
        }
    }

    private fun simple(block: (Int, Boolean) -> Unit) =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) = block(p, u)
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        }
}
