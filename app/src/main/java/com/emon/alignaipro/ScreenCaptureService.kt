package com.emon.alignaipro

import android.app.*
import android.app.Activity
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

class ScreenCaptureService : Service() {
    companion object {
        const val ACTION_START = "com.emon.alignaipro.START_CAPTURE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "result_data"
        private const val CHANNEL = "align_capture"
        private const val NOTIFICATION_ID = 1101
    }
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var detector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder().setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects().enableClassification().build()
    )
    private var busy = false
    private lateinit var worker: Handler
    private lateinit var thread: HandlerThread

    override fun onCreate() {
        super.onCreate()
        thread = HandlerThread("AlignFrameWorker").also { it.start() }
        worker = Handler(thread.looper)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) startCapture(intent)
        return START_NOT_STICKY
    }

    private fun startCapture(intent: Intent) {
        startForeground(NOTIFICATION_ID, notification())
        val code = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val data = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_DATA)
        if (code != Activity.RESULT_OK || data == null) { stopSelf(); return }
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mgr.getMediaProjection(code, data)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader?.setOnImageAvailableListener({ source ->
            val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
            if (!busy) {
                busy = true
                val bitmap = imageToBitmap(image)
                image.close()
                if (bitmap != null) analyse(bitmap) else busy = false
            } else image.close()
        }, worker)
        virtualDisplay = projection?.createVirtualDisplay(
            "AlignAIProCapture", width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader?.surface, null, worker
        )
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopCapture() }
        }, worker)
        getSharedPreferences("align_ai_settings", MODE_PRIVATE).edit().putBoolean("capture_running", true).apply()
    }

    private fun imageToBitmap(image: Image): Bitmap? = try {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val padded = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(buffer)
        Bitmap.createBitmap(padded, 0, 0, image.width, image.height).also { padded.recycle() }
    } catch (_: Exception) { null }

    private fun analyse(bitmap: Bitmap) {
        val input = InputImage.fromBitmap(bitmap, 0)
        detector.process(input)
            .addOnSuccessListener { found ->
                getSharedPreferences("align_ai_settings", MODE_PRIVATE).edit()
                    .putInt("frames_seen", getSharedPreferences("align_ai_settings", MODE_PRIVATE).getInt("frames_seen", 0) + 1)
                    .putInt("objects_seen", found.size).apply()
            }
            .addOnCompleteListener { bitmap.recycle(); busy = false }
    }

    private fun stopCapture() {
        virtualDisplay?.release(); virtualDisplay = null
        reader?.close(); reader = null
        projection?.stop(); projection = null
        getSharedPreferences("align_ai_settings", MODE_PRIVATE).edit().putBoolean("capture_running", false).apply()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "Screen capture", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
    private fun notification(): Notification {
        val pending = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setContentTitle("Align AI Pro")
            .setContentText("Screen analysis is active").setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pending).setOngoing(true).build()
    }
    override fun onDestroy() {
        virtualDisplay?.release(); reader?.close(); projection?.stop()
        detector.close()
        thread.quitSafely()
        getSharedPreferences("align_ai_settings", MODE_PRIVATE).edit().putBoolean("capture_running", false).apply()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
