package com.darkxvenom.airbeats.recognition

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.imageLoader
import coil.request.ImageRequest
import com.darkxvenom.airbeats.MainActivity
import com.darkxvenom.airbeats.R
import com.darkxvenom.airbeats.shazamkit.Shazam
import com.darkxvenom.airbeats.shazamkit.ShazamSignatureGenerator
import com.darkxvenom.airbeats.shazamkit.models.RecognitionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Floating bottom overlay pill for quick music recognition over other apps (e.g. Instagram, TikTok).
 * Matches the native assistant bottom pill design and keeps underlying media playing.
 */
class MusicRecognitionOverlayManager private constructor(private val context: Context) {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var overlayView: View? = null
    private var isOverlayShowing = false

    private var actionIconView: ImageView? = null
    private var coverArtView: ImageView? = null
    private var iconFrameView: FrameLayout? = null
    private var statusTextView: TextView? = null
    private var titleTextView: TextView? = null
    private var artistTextView: TextView? = null
    private var playButtonView: TextView? = null
    private var closeButtonView: TextView? = null
    private var glowBar: View? = null

    private var glowAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null
    private var activeJob: Job? = null
    private var currentResult: RecognitionResult? = null

    private val autoDismissRunnable = Runnable { hide() }

    companion object {
        @Volatile
        private var instance: MusicRecognitionOverlayManager? = null

        fun getInstance(context: Context): MusicRecognitionOverlayManager {
            return instance ?: synchronized(this) {
                instance ?: MusicRecognitionOverlayManager(context.applicationContext).also {
                    instance = it
                }
            }
        }

        fun canDrawOverlays(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }
    }

    /**
     * Starts listening and shows the floating bottom pill overlay.
     */
    fun startListening() {
        if (!canDrawOverlays(context)) {
            Timber.w("Cannot draw overlays: permission not granted")
            return
        }

        mainHandler.post {
            ensureOverlayCreated()
            bindListeningUi()
        }

        activeJob?.cancel()
        activeJob = scope.launch {
            runRecognitionPipeline()
        }
    }

    private fun bindListeningUi() {
        scheduleAutoDismiss(15000L)
        currentResult = null

        statusTextView?.text = context.getString(R.string.music_recognition_overlay_listening)
        statusTextView?.setTextColor(Color.parseColor("#00E5FF"))

        titleTextView?.text = context.getString(R.string.music_recognition_overlay_sub_listening)
        titleTextView?.setTextColor(Color.WHITE)
        titleTextView?.visibility = View.VISIBLE

        artistTextView?.visibility = View.GONE
        playButtonView?.visibility = View.GONE

        actionIconView?.visibility = View.VISIBLE
        actionIconView?.setImageResource(R.drawable.mic)
        actionIconView?.setColorFilter(Color.parseColor("#00E5FF"))

        coverArtView?.visibility = View.GONE
        coverArtView?.setImageDrawable(null)

        startPulseAnimation()
    }

    private fun bindProcessingUi() {
        mainHandler.post {
            statusTextView?.text = context.getString(R.string.music_recognition_overlay_identifying)
            statusTextView?.setTextColor(Color.parseColor("#B388FF"))

            titleTextView?.text = context.getString(R.string.music_recognition_overlay_sub_processing)
            actionIconView?.setImageResource(R.drawable.graphic_eq)
            actionIconView?.setColorFilter(Color.parseColor("#B388FF"))
        }
    }

    private fun bindSuccessUi(result: RecognitionResult) {
        mainHandler.post {
            currentResult = result
            stopPulseAnimation()
            vibrateSuccess()

            statusTextView?.text = context.getString(R.string.music_recognition_overlay_identified)
            statusTextView?.setTextColor(Color.parseColor("#00E676"))

            titleTextView?.text = result.title
            titleTextView?.setTextColor(Color.WHITE)
            titleTextView?.visibility = View.VISIBLE

            artistTextView?.text = result.artist
            artistTextView?.setTextColor(Color.parseColor("#B0B0C0"))
            artistTextView?.visibility = View.VISIBLE

            playButtonView?.visibility = View.VISIBLE

            // Load album art if available
            val coverUrl = result.coverArtHqUrl ?: result.coverArtUrl
            if (!coverUrl.isNullOrBlank()) {
                val request = ImageRequest.Builder(context)
                    .data(coverUrl)
                    .target { drawable ->
                        coverArtView?.setImageDrawable(drawable)
                        coverArtView?.visibility = View.VISIBLE
                        actionIconView?.visibility = View.GONE
                    }
                    .build()
                context.imageLoader.enqueue(request)
            } else {
                actionIconView?.setImageResource(R.drawable.music_note)
                actionIconView?.setColorFilter(Color.parseColor("#00E676"))
                actionIconView?.visibility = View.VISIBLE
                coverArtView?.visibility = View.GONE
            }

            scheduleAutoDismiss(10000L)
        }
    }

    private fun bindNoMatchUi(message: String? = null) {
        mainHandler.post {
            currentResult = null
            stopPulseAnimation()

            statusTextView?.text = context.getString(R.string.music_recognition_overlay_no_match)
            statusTextView?.setTextColor(Color.parseColor("#FFAB40"))

            titleTextView?.text = message ?: context.getString(R.string.music_recognition_tap_to_listen)
            titleTextView?.setTextColor(Color.parseColor("#E0E0E0"))

            artistTextView?.visibility = View.GONE
            playButtonView?.visibility = View.GONE

            actionIconView?.setImageResource(R.drawable.refresh)
            actionIconView?.setColorFilter(Color.parseColor("#FFAB40"))
            actionIconView?.visibility = View.VISIBLE
            coverArtView?.visibility = View.GONE

            scheduleAutoDismiss(6000L)
        }
    }

    private fun bindErrorUi(errorMsg: String) {
        mainHandler.post {
            currentResult = null
            stopPulseAnimation()

            statusTextView?.text = context.getString(R.string.music_recognition_recognition_failed)
            statusTextView?.setTextColor(Color.parseColor("#FF5252"))

            titleTextView?.text = errorMsg
            titleTextView?.setTextColor(Color.parseColor("#E0E0E0"))

            artistTextView?.visibility = View.GONE
            playButtonView?.visibility = View.GONE

            actionIconView?.setImageResource(R.drawable.refresh)
            actionIconView?.setColorFilter(Color.parseColor("#FF5252"))
            actionIconView?.visibility = View.VISIBLE
            coverArtView?.visibility = View.GONE

            scheduleAutoDismiss(5000L)
        }
    }

    private suspend fun runRecognitionPipeline() {
        val samples = recordMicPcm16Mono(sampleRateHz = 16000, recordMs = 4200L)
        if (samples.isEmpty()) {
            bindErrorUi("Microphone unavailable or silent")
            return
        }

        bindProcessingUi()

        val signature = withContext(Dispatchers.Default) {
            ShazamSignatureGenerator().apply {
                feedPcm16Mono(samples)
            }.nextSignatureOrNull()
        }

        if (signature == null) {
            bindNoMatchUi(context.getString(R.string.music_recognition_signature_failed))
            return
        }

        val result = Shazam.recognize(signature.uri, signature.sampleDurationMs)
        result.fold(
            onSuccess = { res ->
                bindSuccessUi(res)
            },
            onFailure = { err ->
                val msg = err.message?.trim().orEmpty()
                if (msg.contains("no match", ignoreCase = true) || msg.contains("404")) {
                    bindNoMatchUi()
                } else {
                    bindErrorUi(msg.ifEmpty { "Connection or recognition failed" })
                }
            }
        )
    }

    private suspend fun recordMicPcm16Mono(sampleRateHz: Int, recordMs: Long): ShortArray =
        withContext(Dispatchers.IO) {
            val channel = AudioFormat.CHANNEL_IN_MONO
            val encoding = AudioFormat.ENCODING_PCM_16BIT

            try {
                val minBuffer = AudioRecord.getMinBufferSize(sampleRateHz, channel, encoding)
                    .coerceAtLeast(4096)
                val record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRateHz,
                    channel,
                    encoding,
                    minBuffer
                )

                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    return@withContext ShortArray(0)
                }

                val totalSamples = ((recordMs / 1000.0) * sampleRateHz).toInt().coerceAtLeast(sampleRateHz)
                val output = ShortArray(totalSamples)
                val buffer = ShortArray(minBuffer / 2)

                try {
                    record.startRecording()
                    if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                        return@withContext ShortArray(0)
                    }

                    var written = 0
                    while (written < output.size && isActive) {
                        val read = record.read(buffer, 0, minOf(buffer.size, output.size - written))
                        if (read > 0) {
                            System.arraycopy(buffer, 0, output, written, read)
                            written += read
                        } else if (read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_BAD_VALUE) {
                            break
                        }
                    }

                    if (written <= 0) ShortArray(0) else output.copyOf(written)
                } finally {
                    runCatching {
                        if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            record.stop()
                        }
                    }
                    runCatching { record.release() }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error recording audio for music recognition")
                ShortArray(0)
            }
        }

    @SuppressLint("ClickableViewAccessibility")
    private fun ensureOverlayCreated() {
        if (overlayView != null && isOverlayShowing) return

        try {
            val root = FrameLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            }

            // Main bottom pill container card with glassmorphic styling
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor("#F5161622"))
                    cornerRadii = floatArrayOf(
                        dpToPx(28f), dpToPx(28f), // top-left
                        dpToPx(28f), dpToPx(28f), // top-right
                        0f, 0f, 0f, 0f
                    )
                    setStroke(dpToPx(1f).toInt(), Color.parseColor("#3500E5FF"))
                }
                background = bg
                setPadding(dpToPx(18f).toInt(), dpToPx(10f).toInt(), dpToPx(18f).toInt(), dpToPx(22f).toInt())
                elevation = dpToPx(14f)
            }

            // Top animated rainbow/cyan glow shimmer bar
            val glow = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(3.5f).toInt()
                ).apply {
                    bottomMargin = dpToPx(10f).toInt()
                }
                val glowGrad = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(
                        Color.parseColor("#00E5FF"), // Cyan
                        Color.parseColor("#7C4DFF"), // Purple
                        Color.parseColor("#FF4081"), // Pink
                        Color.parseColor("#2979FF"), // Blue
                        Color.parseColor("#00E5FF")  // Loop
                    )
                ).apply {
                    cornerRadius = dpToPx(2f)
                }
                background = glowGrad
            }
            glowBar = glow
            card.addView(glow)

            // Content Header Row
            val headerRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            // Left icon / artwork frame
            val iconFrame = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(42f).toInt(), dpToPx(42f).toInt()).apply {
                    marginEnd = dpToPx(12f).toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#262638"))
                }
            }
            iconFrameView = iconFrame

            val iconView = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(dpToPx(22f).toInt(), dpToPx(22f).toInt()).apply {
                    gravity = Gravity.CENTER
                }
                setImageResource(R.drawable.mic)
                setColorFilter(Color.parseColor("#00E5FF"))
            }
            actionIconView = iconView
            iconFrame.addView(iconView)

            val coverArt = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                visibility = View.GONE
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, dpToPx(10f))
                    }
                }
            }
            coverArtView = coverArt
            iconFrame.addView(coverArt)

            iconFrame.setOnClickListener {
                if (currentResult == null) {
                    startListening()
                } else {
                    launchAirBeatsWithCurrentResult()
                }
            }
            headerRow.addView(iconFrame)

            // Text column (Status + Title + Artist)
            val textCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    if (currentResult != null) {
                        launchAirBeatsWithCurrentResult()
                    } else {
                        startListening()
                    }
                }
            }

            val statusTv = TextView(context).apply {
                text = context.getString(R.string.music_recognition_overlay_listening)
                setTextColor(Color.parseColor("#00E5FF"))
                textSize = 12f
                paint.isFakeBoldText = true
            }
            statusTextView = statusTv
            textCol.addView(statusTv)

            val titleTv = TextView(context).apply {
                setTextColor(Color.WHITE)
                textSize = 14.5f
                paint.isFakeBoldText = true
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            titleTextView = titleTv
            textCol.addView(titleTv)

            val artistTv = TextView(context).apply {
                setTextColor(Color.parseColor("#B0B0C0"))
                textSize = 12f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                visibility = View.GONE
            }
            artistTextView = artistTv
            textCol.addView(artistTv)

            headerRow.addView(textCol)

            // Actions row (Play Button + Close "✕")
            val actionsRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            // Play button pill
            val playBtn = TextView(context).apply {
                text = "▶  " + context.getString(R.string.music_recognition_overlay_play)
                setTextColor(Color.parseColor("#00E5FF"))
                textSize = 12.5f
                paint.isFakeBoldText = true
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#2000E5FF"))
                    cornerRadius = dpToPx(16f)
                    setStroke(dpToPx(1f).toInt(), Color.parseColor("#6000E5FF"))
                }
                setPadding(dpToPx(12f).toInt(), dpToPx(6f).toInt(), dpToPx(12f).toInt(), dpToPx(6f).toInt())
                visibility = View.GONE
                setOnClickListener { launchAirBeatsWithCurrentResult() }
            }
            playButtonView = playBtn
            actionsRow.addView(playBtn)

            // Close button (✕)
            val closeBtn = TextView(context).apply {
                text = "✕"
                setTextColor(Color.parseColor("#707080"))
                textSize = 16f
                setPadding(dpToPx(10f).toInt(), dpToPx(6f).toInt(), dpToPx(4f).toInt(), dpToPx(6f).toInt())
                setOnClickListener { hide() }
            }
            closeButtonView = closeBtn
            actionsRow.addView(closeBtn)

            headerRow.addView(actionsRow)
            card.addView(headerRow)
            root.addView(card)

            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.FILL_HORIZONTAL
                x = 0
                y = 0
                windowAnimations = android.R.style.Animation_Toast
            }

            windowManager.addView(root, layoutParams)
            overlayView = root
            isOverlayShowing = true

            startGlowAnimation()
        } catch (e: Exception) {
            Timber.e(e, "Error adding music recognition overlay view")
        }
    }

    private fun launchAirBeatsWithCurrentResult() {
        val res = currentResult ?: return
        val query = "${res.title} ${res.artist}".trim()

        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_SEARCH
            putExtra("search_query", query)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        context.startActivity(intent)
        hide()
    }

    private fun startGlowAnimation() {
        glowAnimator?.cancel()
        glowAnimator = ValueAnimator.ofFloat(0.55f, 1.0f).apply {
            duration = 1000
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                glowBar?.alpha = anim.animatedValue as Float
            }
            start()
        }
    }

    private fun startPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(1.0f, 1.14f).apply {
            duration = 600
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val scale = anim.animatedValue as Float
                iconFrameView?.scaleX = scale
                iconFrameView?.scaleY = scale
            }
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        iconFrameView?.scaleX = 1f
        iconFrameView?.scaleY = 1f
    }

    private fun vibrateSuccess() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(100L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(100L)
            }
        } catch (_: Exception) {}
    }

    private fun scheduleAutoDismiss(delayMs: Long) {
        mainHandler.removeCallbacks(autoDismissRunnable)
        mainHandler.postDelayed(autoDismissRunnable, delayMs)
    }

    /**
     * Hides and removes the floating bottom overlay.
     */
    fun hide() {
        mainHandler.removeCallbacks(autoDismissRunnable)
        activeJob?.cancel()
        activeJob = null

        mainHandler.post {
            try {
                glowAnimator?.cancel()
                glowAnimator = null
                stopPulseAnimation()

                if (overlayView != null && isOverlayShowing) {
                    windowManager.removeView(overlayView)
                    overlayView = null
                    isOverlayShowing = false
                }
            } catch (e: Exception) {
                Timber.e(e, "Error removing music recognition overlay view")
            }
        }
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        )
    }
}
