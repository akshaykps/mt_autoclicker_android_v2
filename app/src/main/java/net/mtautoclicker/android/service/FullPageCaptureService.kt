package net.mtautoclicker.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
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
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.mtautoclicker.android.MainActivity
import net.mtautoclicker.android.R
import net.mtautoclicker.android.engine.FullPageCaptureHub
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Holds MediaProjection + VirtualDisplay and runs scroll-stitch capture when asked.
 */
class FullPageCaptureService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var captureJob: Job? = null

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var screenWidth = 0
    private var screenHeight = 0
    private var densityDpi = 0
    private var statusBarH = 0
    private var navBarH = 0

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> ensureProjectionSession()
            ACTION_CAPTURE -> startCapture()
            ACTION_STOP -> {
                captureJob?.cancel()
                // End session from notification close — tear down overlay without recursion.
                stopService(Intent(this, ScreenshotOverlayService::class.java))
                stopSelf()
            }        }
        return START_STICKY
    }

    override fun onDestroy() {
        captureJob?.cancel()
        scope.cancel()
        releaseDisplay()
        mediaProjection?.stop()
        mediaProjection = null
        FullPageCaptureHub.clearProjectionConsent()
        super.onDestroy()
    }

    private fun ensureProjectionSession() {
        if (mediaProjection != null) {
            startForegroundNotification()
            return
        }
        val resultCode = FullPageCaptureHub.projectionResultCode
        val data = FullPageCaptureHub.projectionData
        if (resultCode == 0 || data == null) {
            FullPageCaptureHub.setError("Missing screen capture permission")
            stopSelf()
            return
        }

        startForegroundNotification()

        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = mgr.getMediaProjection(resultCode, data) ?: run {
            FullPageCaptureHub.setError("Could not start screen capture")
            stopSelf()
            return
        }
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        }, mainHandler)
        mediaProjection = projection
        setupVirtualDisplay(projection)
        FullPageCaptureHub.resetToReady()
    }

    private fun startForegroundNotification() {
        ensureChannel()
        postReadyNotification()
    }

    private fun postReadyNotification() {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopPi = PendingIntent.getService(
            this,
            1,
            Intent(this, FullPageCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val views = android.widget.RemoteViews(packageName, R.layout.notification_screenshot_ready).apply {
            setTextViewText(R.id.notif_ss_ready_title, "Screenshot ready")
            setTextViewText(R.id.notif_ss_ready_body, "Open any scrollable screen → tap Snapshot")
            setOnClickPendingIntent(R.id.notif_ss_ready_close, stopPi)
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_screenshot)
            .setContentTitle("Screenshot ready")
            .setContentText("Open any scrollable screen → tap Snapshot on the float bar")
            .setCustomContentView(views)
            .setCustomBigContentView(views)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setColor(0xFF06B6D4.toInt())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun postCapturingNotification(frameCount: Int, saving: Boolean = false) {
        ensureChannel()
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = if (saving) "Saving screenshot…" else "Capturing full page…"
        val body = if (saving) {
            "Almost done — stitching frames"
        } else {
            "Scrolling & stitching — don’t touch the screen"
        }
        val views = android.widget.RemoteViews(packageName, R.layout.notification_screenshot_capturing).apply {
            setTextViewText(R.id.notif_ss_cap_title, title)
            setTextViewText(R.id.notif_ss_cap_body, body)
            setTextViewText(
                R.id.notif_ss_cap_frames,
                if (saving) "…" else "$frameCount",
            )
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_screenshot)
            .setContentTitle(title)
            .setContentText(body)
            .setCustomContentView(views)
            .setCustomBigContentView(views)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setColor(0xFF06B6D4.toInt())
            .build()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun setupVirtualDisplay(projection: MediaProjection) {
        val metrics = resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        densityDpi = metrics.densityDpi
        statusBarH = statusBarHeightPx()
        navBarH = navigationBarHeightPx()

        imageReader = ImageReader.newInstance(
            screenWidth,
            screenHeight,
            PixelFormat.RGBA_8888,
            /* maxImages */ 4,
        )
        virtualDisplay = projection.createVirtualDisplay(
            "mt_fullpage_capture",
            screenWidth,
            screenHeight,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            mainHandler,
        )
    }

    private fun releaseDisplay() {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { imageReader?.close() }
        imageReader = null
    }

    private fun startCapture() {
        if (captureJob?.isActive == true) {
            Toast.makeText(this, "Already capturing…", Toast.LENGTH_SHORT).show()
            return
        }
        if (mediaProjection == null || imageReader == null) {
            ensureProjectionSession()
            if (mediaProjection == null) return
        }
        captureJob = scope.launch(Dispatchers.Default) {
            try {
                FullPageCaptureHub.setCapturing(0)
                withContext(Dispatchers.Main) {
                    ScreenshotOverlayService.setHidden(this@FullPageCaptureService, true)
                    ScreenshotOverlayService.showLiveCapture(this@FullPageCaptureService, true)
                    postCapturingNotification(0)
                }
                delay(350)

                val frames = mutableListOf<Bitmap>()
                var unchangedStreak = 0
                var previous: Bitmap? = null

                var index = 0
                while (index < MAX_FRAMES && isActive) {
                    // Hide live pill briefly so it isn’t baked into the stitch.
                    withContext(Dispatchers.Main) {
                        ScreenshotOverlayService.setLiveCaptureVisible(this@FullPageCaptureService, false)
                    }
                    delay(90)
                    var frame = grabFrame()
                    if (frame == null) {
                        delay(140)
                        frame = grabFrame()
                    }
                    withContext(Dispatchers.Main) {
                        ScreenshotOverlayService.setLiveCaptureVisible(this@FullPageCaptureService, true)
                    }
                    if (frame == null) {
                        index++
                        continue
                    }

                    val cropped = cropChrome(frame)
                    frame.recycle()

                    val prev = previous
                    val delta = if (prev != null) contentDelta(prev, cropped) else 1f
                    if (prev != null && delta < END_DELTA_THRESHOLD) {
                        // Page barely/didn't move — don't keep near-duplicate frames.
                        unchangedStreak++
                        cropped.recycle()
                        if (unchangedStreak >= END_UNCHANGED_STREAK && frames.size >= MIN_FRAMES_BEFORE_END) {
                            break
                        }
                    } else {
                        unchangedStreak = 0
                        frames += cropped
                        previous = cropped
                        FullPageCaptureHub.setFrameCount(frames.size)
                        withContext(Dispatchers.Main) {
                            ScreenshotOverlayService.updateLiveCaptureFrames(
                                this@FullPageCaptureService,
                                frames.size,
                            )
                            postCapturingNotification(frames.size)
                        }
                    }

                    if (index >= MAX_FRAMES - 1) break
                    scrollPageDown()
                    // Wait for fling + image decode / lazy-load on web pages.
                    delay(SCROLL_SETTLE_MS)
                    index++
                }

                if (frames.isEmpty()) {
                    FullPageCaptureHub.setError("No frames captured")
                    return@launch
                }

                FullPageCaptureHub.setSaving()
                withContext(Dispatchers.Main) {
                    postCapturingNotification(frames.size, saving = true)
                    ScreenshotOverlayService.updateLiveCaptureLabel(
                        this@FullPageCaptureService,
                        "Saving…",
                    )
                }
                val frameCount = frames.size
                val stitched = stitchFrames(frames)
                frames.forEach { if (!it.isRecycled) it.recycle() }

                val path = withContext(Dispatchers.IO) { saveBitmap(stitched) }
                stitched.recycle()

                withContext(Dispatchers.Main) {
                    FullPageCaptureHub.setDone(path)
                    Toast.makeText(
                        this@FullPageCaptureService,
                        "Saved to Gallery → Pictures → MT Auto Clicker ($frameCount frames)",
                        Toast.LENGTH_LONG,
                    ).show()
                    showSavedNotification(path, frameCount)
                }
                delay(1600)
            } catch (t: Throwable) {
                FullPageCaptureHub.setError(t.message ?: "Capture failed")
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@FullPageCaptureService,
                        "Capture failed: ${t.message}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    ScreenshotOverlayService.showLiveCapture(this@FullPageCaptureService, false)
                    ScreenshotOverlayService.setHidden(this@FullPageCaptureService, false)
                    FullPageCaptureHub.resetToReady()
                    if (mediaProjection != null) {
                        postReadyNotification()
                    }
                }
            }
        }
    }

    /**
     * Only use [ImageReader.acquireLatestImage] (never mix with acquireNextImage).
     * Mixing the two with maxImages=2 commonly throws:
     * "maxImages (2) has already been acquired".
     */
    private suspend fun grabFrame(): Bitmap? {
        val reader = imageReader ?: return null
        repeat(16) { attempt ->
            try {
                val image = reader.acquireLatestImage()
                if (image != null) {
                    return try {
                        imageToBitmap(image)
                    } finally {
                        image.close()
                    }
                }
            } catch (_: IllegalStateException) {
                // A previous Image wasn't closed yet (OEM race). Force-close backlog once.
                forceDrainImages(reader)
            }
            delay(if (attempt < 3) 40L else 55L)
        }
        return null
    }

    private fun forceDrainImages(reader: ImageReader) {
        repeat(8) {
            val img = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return
            runCatching { img.close() }
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * screenWidth
        val bitmap = Bitmap.createBitmap(
            screenWidth + rowPadding / pixelStride,
            screenHeight,
            Bitmap.Config.ARGB_8888,
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return if (bitmap.width == screenWidth) {
            bitmap
        } else {
            val cropped = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
            bitmap.recycle()
            cropped
        }
    }

    private fun cropChrome(src: Bitmap): Bitmap {
        // Light system chrome only — sticky app UI (composers / toolbars) is handled in stitch.
        val top = statusBarH.coerceIn(0, src.height / 5)
        val bottomPad = navBarH.coerceIn(0, src.height / 5)
        val h = (src.height - top - bottomPad).coerceAtLeast(src.height / 2)
        return Bitmap.createBitmap(src, 0, top, src.width, h)
    }

    private suspend fun scrollPageDown() {
        val service = MtAccessibilityService.instance ?: return
        val x = screenWidth / 2f
        // Shorter, steadier swipe → more overlap between frames → fewer mid-image cuts.
        val y1 = screenHeight * 0.58f
        val y2 = screenHeight * 0.26f
        service.dispatchSwipe(x, y1, x, y2, 520L, timeoutMs = 1_000L)
    }

    /**
     * 0 = identical, 1 = totally different.
     * Emphasizes the lower content band (where new chat/web content appears after a scroll).
     */
    private fun contentDelta(prev: Bitmap, next: Bitmap): Float {
        if (prev.width != next.width || prev.height != next.height) return 1f
        val bands = listOf(
            // lower band — primary signal for "did we scroll?"
            (prev.height * 0.48f).toInt() to (prev.height * 0.82f).toInt(),
            // mid band — catches webpage article movement
            (prev.height * 0.28f).toInt() to (prev.height * 0.48f).toInt(),
        )
        var totalChecked = 0
        var totalMismatch = 0
        val stepX = max(1, prev.width / 40)
        for ((y0raw, y1raw) in bands) {
            val y0 = y0raw.coerceIn(0, prev.height - 2)
            val y1 = y1raw.coerceAtLeast(y0 + 8).coerceAtMost(prev.height)
            val stepY = max(1, (y1 - y0) / 28)
            var y = y0
            while (y < y1) {
                var x = 0
                while (x < prev.width) {
                    val pa = prev.getPixel(x, y)
                    val pb = next.getPixel(x, y)
                    val dr = abs(((pa shr 16) and 0xFF) - ((pb shr 16) and 0xFF))
                    val dg = abs(((pa shr 8) and 0xFF) - ((pb shr 8) and 0xFF))
                    val db = abs((pa and 0xFF) - (pb and 0xFF))
                    if (dr + dg + db > 36) totalMismatch++
                    totalChecked++
                    x += stepX
                }
                y += stepY
            }
        }
        if (totalChecked == 0) return 1f
        return totalMismatch.toFloat() / totalChecked
    }

    /**
     * Strip sticky app chrome (toolbars / chat composers) and append only new content.
     * First frame keeps the top app bar; last frame keeps the bottom composer once.
     * Middle frames keep only the scrolling content band.
     */
    private fun stitchFrames(frames: List<Bitmap>): Bitmap {
        if (frames.size == 1) return frames[0].copy(Bitmap.Config.ARGB_8888, false)

        val prepared = prepareContentSlices(frames)
        try {
            if (prepared.size == 1) {
                return prepared[0].copy(Bitmap.Config.ARGB_8888, false)
            }

            val width = prepared.first().width
            val overlaps = IntArray(prepared.size) { 0 }
            for (i in 1 until prepared.size) {
                overlaps[i] = findBestOverlap(prepared[i - 1], prepared[i])
            }

            var totalHeight = prepared.first().height
            for (i in 1 until prepared.size) {
                totalHeight += (prepared[i].height - overlaps[i]).coerceAtLeast(1)
            }

            val scale = if (totalHeight > MAX_OUTPUT_HEIGHT) {
                MAX_OUTPUT_HEIGHT.toFloat() / totalHeight
            } else {
                1f
            }
            val outW = (width * scale).toInt().coerceAtLeast(1)
            val outH = (totalHeight * scale).toInt().coerceAtLeast(1)
            val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(out)
            var y = 0f

            prepared.forEachIndexed { index, frame ->
                val slice = if (index == 0) {
                    frame
                } else {
                    val cut = overlaps[index].coerceIn(0, frame.height - 1)
                    val h = (frame.height - cut).coerceAtLeast(1)
                    Bitmap.createBitmap(frame, 0, cut, frame.width, h)
                }
                val dw = (slice.width * scale).toInt().coerceAtLeast(1)
                val dh = (slice.height * scale).toInt().coerceAtLeast(1)
                val drawn = if (abs(scale - 1f) > 0.001f) {
                    Bitmap.createScaledBitmap(slice, dw, dh, true)
                } else {
                    slice
                }
                canvas.drawBitmap(drawn, 0f, y, null)
                y += drawn.height
                if (drawn !== slice && drawn !== frame) drawn.recycle()
                if (slice !== frame) slice.recycle()
            }
            return out
        } finally {
            prepared.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    /**
     * First: keep header, drop sticky bottom (composer).
     * Middle: drop sticky top + bottom.
     * Last: drop sticky top, keep bottom composer once.
     */
    private fun prepareContentSlices(frames: List<Bitmap>): List<Bitmap> {
        val h = frames.first().height
        val topSticky = max(statusBarH + (h * 0.02f).toInt(), (h * 0.11f).toInt())
            .coerceIn(statusBarH.coerceAtLeast(1), h / 3)
        val bottomSticky = max(navBarH + (h * 0.08f).toInt(), (h * 0.17f).toInt())
            .coerceIn(navBarH.coerceAtLeast(1), h / 3)

        return frames.mapIndexed { index, frame ->
            val top = when {
                index == 0 -> 0
                else -> topSticky.coerceAtMost(frame.height / 3)
            }
            val bottom = when {
                index == frames.lastIndex -> 0
                else -> bottomSticky.coerceAtMost(frame.height / 3)
            }
            val contentH = (frame.height - top - bottom).coerceAtLeast(frame.height / 3)
            Bitmap.createBitmap(frame, 0, top, frame.width, contentH)
        }
    }

    /** Find how many top rows of [next] already appear at the bottom of [prev]. */
    private fun findBestOverlap(prev: Bitmap, next: Bitmap): Int {
        if (prev.width != next.width) {
            return (next.height * 0.30f).toInt().coerceAtLeast(12)
        }
        // Prefer generous overlap so webpage images aren't sliced mid-frame.
        val minOverlap = (next.height * 0.12f).toInt().coerceAtLeast(16)
        val maxOverlap = (next.height * 0.62f).toInt().coerceAtMost(next.height - 4)
            .coerceAtLeast(minOverlap)
        val sampleH = min(56, min(prev.height, next.height) / 5).coerceAtLeast(16)

        var bestOverlap = (next.height * 0.32f).toInt().coerceIn(minOverlap, maxOverlap)
        var bestScore = Float.MAX_VALUE

        var overlap = minOverlap
        while (overlap <= maxOverlap) {
            if (overlap > prev.height - 2) break
            val prevStart = (prev.height - overlap).coerceAtLeast(0)
            val compareH = min(sampleH, overlap)
            // Score two horizontal bands for stabler seams on image-heavy pages.
            val scoreTop = bandDiff(prev, prevStart, next, 0, compareH / 2)
            val scoreMid = bandDiff(
                prev,
                prevStart + compareH / 3,
                next,
                compareH / 3,
                compareH / 2,
            )
            val score = (scoreTop * 0.55f) + (scoreMid * 0.45f)
            // Slight preference for larger overlap (safer for images) when scores are close.
            val adjusted = score - (overlap / next.height.toFloat()) * 0.02f
            if (adjusted < bestScore) {
                bestScore = adjusted
                bestOverlap = overlap
            }
            overlap += 2
        }
        // Weak match → fall back to a large safe overlap instead of a risky thin cut.
        if (bestScore > 0.12f) {
            return (next.height * 0.36f).toInt().coerceIn(minOverlap, maxOverlap)
        }
        return bestOverlap
    }

    private fun bandDiff(
        a: Bitmap,
        aStartY: Int,
        b: Bitmap,
        bStartY: Int,
        height: Int,
    ): Float {
        val stepX = max(1, a.width / 28)
        val stepY = max(1, height / 12)
        var checked = 0
        var mismatch = 0
        var y = 0
        while (y < height) {
            val ay = aStartY + y
            val by = bStartY + y
            if (ay >= a.height || by >= b.height) break
            var x = 0
            while (x < a.width) {
                val pa = a.getPixel(x, ay)
                val pb = b.getPixel(x, by)
                val dr = abs(((pa shr 16) and 0xFF) - ((pb shr 16) and 0xFF))
                val dg = abs(((pa shr 8) and 0xFF) - ((pb shr 8) and 0xFF))
                val db = abs((pa and 0xFF) - (pb and 0xFF))
                if (dr + dg + db > 48) mismatch++
                checked++
                x += stepX
            }
            y += stepY
        }
        if (checked == 0) return 1f
        return mismatch.toFloat() / checked
    }

    private fun saveBitmap(bitmap: Bitmap): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val name = "MT_FullPage_$stamp.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MT Auto Clicker")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not create MediaStore entry")
            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    error("PNG compress failed")
                }
            } ?: error("Could not open output stream")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri.toString()
        }

        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "MT Auto Clicker",
        )
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, name)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file.absolutePath
    }

    private fun showSavedNotification(path: String, frameCount: Int) {
        ensureSavedChannel()
        val uri = if (path.startsWith("content://")) {
            android.net.Uri.parse(path)
        } else {
            FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                File(path),
            )
        }
        val open = PendingIntent.getActivity(
            this,
            2,
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/png")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, SAVED_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_screenshot)
            .setContentTitle("Screenshot saved")
            .setContentText("Pictures → MT Auto Clicker · $frameCount frames · tap to open")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(0xFF06B6D4.toInt())
            .build()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(SAVED_NOTIFICATION_ID, notification)
    }

    private fun statusBarHeightPx(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else (24 * resources.displayMetrics.density).toInt()
    }

    private fun navigationBarHeightPx(): Int {
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Full-page screenshot", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows when screenshot capture is ready or running"
                setShowBadge(false)
            },
        )
    }

    private fun ensureSavedChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(SAVED_CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                SAVED_CHANNEL_ID,
                "Screenshot saved",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Tap to open your full-page screenshot"
            },
        )
    }

    companion object {
        const val ACTION_START = "net.mtautoclicker.android.FULLPAGE_START"
        const val ACTION_CAPTURE = "net.mtautoclicker.android.FULLPAGE_CAPTURE"
        const val ACTION_STOP = "net.mtautoclicker.android.FULLPAGE_STOP"
        private const val CHANNEL_ID = "mt_fullpage_capture_v2"
        private const val SAVED_CHANNEL_ID = "mt_fullpage_saved_v2"
        private const val NOTIFICATION_ID = 1101
        private const val SAVED_NOTIFICATION_ID = 1102
        private const val MAX_FRAMES = 36
        private const val MIN_FRAMES_BEFORE_END = 2
        private const val END_UNCHANGED_STREAK = 2
        /** Below this content-delta, treat frames as "didn't scroll" and stop soon. */
        private const val END_DELTA_THRESHOLD = 0.028f
        private const val MAX_OUTPUT_HEIGHT = 22000
        private const val SCROLL_SETTLE_MS = 980L

        fun start(context: Context) {
            val intent = Intent(context, FullPageCaptureService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun capture(context: Context) {
            context.startService(
                Intent(context, FullPageCaptureService::class.java).setAction(ACTION_CAPTURE),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FullPageCaptureService::class.java))
        }
    }
}
