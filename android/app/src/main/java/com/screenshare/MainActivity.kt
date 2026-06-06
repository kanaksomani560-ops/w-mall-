package com.screenshare

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val REQUEST_CODE = 100
    private val PORT = 5020

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val ipInput = findViewById<EditText>(R.id.ipInput)
        val startBtn = findViewById<Button>(R.id.startBtn)
        val statusText = findViewById<TextView>(R.id.statusText)

        startBtn.setOnClickListener {
            if (!ScreenCaptureService.isRunning) {
                val ip = ipInput.text.toString().trim()
                if (ip.isEmpty()) { statusText.text = "Enter PC IP!"; return@setOnClickListener }
                statusText.text = "Requesting permission..."
                val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                startActivityForResult(mgr.createScreenCaptureIntent(), REQUEST_CODE)
            } else {
                stopService(Intent(this, ScreenCaptureService::class.java))
                startBtn.text = "Start Streaming"
                statusText.text = "Stopped"
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val startBtn = findViewById<Button>(R.id.startBtn)
        val statusText = findViewById<TextView>(R.id.statusText)
        val ipInput = findViewById<EditText>(R.id.ipInput)

        if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenCaptureService.EXTRA_DATA, data)
                putExtra(ScreenCaptureService.EXTRA_IP, ipInput.text.toString().trim())
                putExtra(ScreenCaptureService.EXTRA_PORT, PORT)
            }
            startForegroundService(intent)
            startBtn.text = "Stop"
            statusText.text = "Streaming!"
        }
    }
}
