package com.screenshare

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.Socket

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var outputStream: OutputStream? = null

    companion object {
        const val CHANNEL_ID = "ScreenShareChannel"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
        const val EXTRA_IP = "ip"
        const val EXTRA_PORT = "port"
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Sharing")
            .setContentText("Streaming to PC...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

        startForeground(1, notification)
        isRunning = true

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
        val ip = intent?.getStringExtra(EXTRA_IP) ?: ""
        val port = intent?.getIntExtra(EXTRA_PORT, 5020) ?: 5020

        if (data != null) {
            Thread {
                try {
                    val socket = Socket(ip, port)
                    socket.setTcpNoDelay(true)
                    outputStream = socket.getOutputStream()

                    val projManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    mediaProjection = projManager.getMediaProjection(resultCode, data)
                    startCapture()
                } catch (e: Exception) {
                    stopSelf()
                }
            }.start()
        }

        return START_NOT_STICKY
    }

    private fun startCapture() {
        val WIDTH = 1080
        val HEIGHT = 1920
        val DPI = 320

        imageReader = ImageReader.newInstance(WIDTH, HEIGHT, PixelFormat.RGBA_8888, 2)

        val handlerThread = HandlerThread("ScreenCapture")
        handlerThread.start()
        val handler = Handler(handlerThread.looper)

        virtualDisplay = mediaProjection!!.createVirtualDisplay(
            "ScreenShare", WIDTH, HEIGHT, DPI,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, handler
        )

        imageReader!!.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * WIDTH

                val bitmap = Bitmap.createBitmap(
                    WIDTH + rowPadding / pixelStride, HEIGHT, Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, WIDTH, HEIGHT)

                val baos = ByteArrayOutputStream()
                cropped.compress(Bitmap.CompressFormat.JPEG, 60, baos)
                val jpegBytes = baos.toByteArray()

                val sizeHeader = ByteArray(4)
                sizeHeader[0] = (jpegBytes.size shr 24).toByte()
                sizeHeader[1] = (jpegBytes.size shr 16).toByte()
                sizeHeader[2] = (jpegBytes.size shr 8).toByte()
                sizeHeader[3] = jpegBytes.size.toByte()

                outputStream?.write(sizeHeader)
                outputStream?.write(jpegBytes)
                outputStream?.flush()

                bitmap.recycle()
                cropped.recycle()
            } catch (e: Exception) {
                stopSelf()
            } finally {
                image.close()
            }
        }, handler)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Screen Share", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        isRunning = false
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        outputStream?.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
