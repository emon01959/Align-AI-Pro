package com.emon.alignaipro

import android.app.*
import android.app.Activity
import android.content.Intent
import android.content.pm.ServiceInfo
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
    private var busy = false
    private lateinit var worker: Handler
    private lateinit var thread: HandlerThread
    private val prefs by lazy { getSharedPreferences("align_ai_settings", MODE_PRIVATE) }
    private val detector by lazy {
        ObjectDetection.getClient(
            ObjectDetectorOptions.Builder().setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableMultipleObjects().enableClassification().build()
        )
    }

    override fun onCreate() {
        super.onCreate()
        thread = HandlerThread("AlignFrameWorker").also { it.start() }
        worker = Handler(thread.looper)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) startCapture(intent, startId)
        return START_NOT_STICKY
    }

    private fun startCapture(intent: Intent, startId: Int) {
        try {
            // Android 14+ requires this service to enter foreground with the mediaProjection type.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(NOTIFICATION_ID, notification())
            }
            val code = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            val data = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_DATA)
            if (code != Activity.RESULT_OK || data == null) {
                setError("Screen capture permission was not granted.")
                stopSelf(startId)
                return
            }
            val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = mgr.getMediaProjection(code, data)
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
            reader?.setOnImageAvailableListener({ source ->
                val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                if (!busy) {
                    busy = true
                    val bitmap = imageToBitmap(image)
                    image.close()
                    if (bitmap != null) analyse(bitmap) else busy = false
                } else image.close()
            }, worker)

            // Register the callback before creating the virtual display (required by newer Android releases).
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopCaptureResources() }
            }, worker)

            virtualDisplay = projection?.createVirtualDisplay(
                "AlignAIProCapture", width, height, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader?.surface, null, worker
            )
            if (virtualDisplay == null) throw IllegalStateException("Android did not create the capture display.")
            prefs.edit().putBoolean("capture_running", true).putString("capture_error", "").apply()
        } catch (e: Exception) {
            setError("Capture could not start: ${e.javaClass.simpleName}: ${e.message ?: "unknown error"}")
            stopCaptureResources()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
    }

    private fun imageToBitmap(image: Image): Bitmap? = try {
        val plane = image.planes[0]
        val rowPadding = plane.rowStride - plane.pixelStride * image.width
        val padded = Bitmap.createBitmap(image.width + rowPadding / plane.pixelStride, image.height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(plane.buffer)
        Bitmap.createBitmap(padded, 0, 0, image.width, image.height).also { padded.recycle() }
    } catch (_: Exception) { null }

    private fun analyse(bitmap: Bitmap) {
        try {
            detector.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { found ->
                    prefs.edit().putInt("frames_seen", prefs.getInt("frames_seen", 0) + 1)
                        .putInt("objects_seen", found.size).apply()
                }
                .addOnFailureListener { e -> setError("Frame analysis error: ${e.localizedMessage ?: "unknown"}") }
                .addOnCompleteListener { bitmap.recycle(); busy = false }
        } catch (e: Exception) {
            bitmap.recycle()
            busy = false
            setError("Frame processing error: ${e.localizedMessage ?: "unknown"}")
        }
    }

    private fun setError(message: String) {
        prefs.edit().putBoolean("capture_running", false).putString("capture_error", message).apply()
    }

    private fun stopCaptureResources() {
        virtualDisplay?.release(); virtualDisplay = null
        reader?.setOnImageAvailableListener(null, null)
        reader?.close(); reader = null
        projection?.let { p ->
            try { p.stop() } catch (_: Exception) { }
        }
        projection = null
        prefs.edit().putBoolean("capture_running", false).apply()
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
        stopCaptureResources()
        if (::thread.isInitialized) thread.quitSafely()
        try { detector.close() } catch (_: Exception) { }
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
