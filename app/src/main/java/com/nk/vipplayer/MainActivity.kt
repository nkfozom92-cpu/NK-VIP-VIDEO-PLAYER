package com.nk.vipplayer

import android.Manifest
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.ComponentName
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.util.Rational
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.GestureDetector
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.*
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

class MainActivity : Activity() {

    private var player: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var pendingVideoUri: Uri? = null
    private var backgroundAudioEnabled = false
    private lateinit var audioButton: TextView
    private lateinit var playerView: PlayerView
    private lateinit var homePage: LinearLayout
    private lateinit var status: TextView
    private lateinit var videoList: LinearLayout
    private lateinit var countText: TextView
    private var isZoomed = false
    private var isLandscapeMode = false
    private var originalScreenBrightness = -1f
    private val allVideos = mutableListOf<VideoItem>()
    private val favorites = mutableSetOf<String>()
    private var currentTab = "ALL"

    private val PICK_VIDEO = 1001
    private val PERMISSION_REQUEST = 2001

    data class VideoItem(
        val uri: Uri,
        val name: String,
        val duration: Long,
        val size: Long,
        val id: Long
    )

    private fun dp(v: Int) =
        (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        originalScreenBrightness = window.attributes.screenBrightness

        window.statusBarColor = Color.rgb(12, 12, 16)
        window.navigationBarColor = Color.BLACK

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(5))
            setBackgroundColor(Color.rgb(12, 12, 16))
        }

        homePage = page

        val title = TextView(this).apply {
            text = "▶  NK VIP VIDEO PLAYER"
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(235, 255, 248))
            typeface = android.graphics.Typeface.create(
                "sans-serif-black", android.graphics.Typeface.BOLD
            )
            letterSpacing = 0.08f
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.rgb(10, 35, 33),
                    Color.rgb(17, 22, 32),
                    Color.rgb(45, 31, 12)
                )
            ).apply {
                cornerRadius = dp(13).toFloat()
                setStroke(dp(1), Color.rgb(65, 225, 165))
            }
            elevation = dp(4).toFloat()
        }
        page.addView(title)

        status = TextView(this).apply {
            text = "Your Video Player"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(4), 0, dp(7))
        }
        page.addView(status)

        playerView = PlayerView(this).apply {
            useController = true
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setBackgroundColor(Color.BLACK)
        }

        page.addView(
            playerView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(170)
            )
        )

        val rotateButton = TextView(this).apply {
            tag = "nk_overlay_control"
            text = "↻ ROTATE"
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(9), dp(12), dp(9))
            setBackgroundColor(Color.rgb(45, 45, 55))
            elevation = dp(6).toFloat()

            setOnClickListener {
                isLandscapeMode = !isLandscapeMode

                requestedOrientation = if (isLandscapeMode) {
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }

                applyVideoScreenMode()
            }
        }

        playerView.addView(
            rotateButton,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            )
        )





        val pipButton = TextView(this).apply {
            tag = "nk_overlay_control"
            text = "▣ POP-UP"
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(25, 105, 82))
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.rgb(65, 225, 165))
            }
            elevation = dp(6).toFloat()

            setOnClickListener {
                if (player == null || player?.isPlaying != true) {
                    Toast.makeText(
                        this@MainActivity,
                        "আগে একটি ভিডিও চালু করুন",
                        Toast.LENGTH_SHORT
                    ).show()
                } else if (Build.VERSION.SDK_INT >= 26) {
                    val params = PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build()
                    enterPictureInPictureMode(params)
                } else if (Build.VERSION.SDK_INT >= 24) {
                    @Suppress("DEPRECATION")
                    enterPictureInPictureMode()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Android 7.0 বা নতুন সংস্করণ প্রয়োজন",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        playerView.addView(
            pipButton,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END
            ).apply {
                setMargins(0, 0, dp(12), dp(12))
            }
        )


        audioButton = TextView(this).apply {
            tag = "nk_overlay_control"
            text = "♫ AUDIO"
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(20, 115, 82))
                cornerRadius = dp(9).toFloat()
                setStroke(dp(1), Color.rgb(65, 225, 165))
            }
            elevation = dp(6).toFloat()
            visibility = View.GONE
            setOnClickListener {
                if (player == null) {
                    Toast.makeText(this@MainActivity, "ভিডিও প্রস্তুত হচ্ছে", Toast.LENGTH_SHORT).show()
                } else if (Build.VERSION.SDK_INT >= 26) {
                    backgroundAudioEnabled = true
                    val params = PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build()
                    enterPictureInPictureMode(params)
                } else {
                    Toast.makeText(this@MainActivity, "Floating video-এর জন্য Android 8.0+ প্রয়োজন", Toast.LENGTH_LONG).show()
                }
            }
        }

        playerView.addView(
            audioButton,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            ).apply { setMargins(dp(12), dp(12), 0, 0) }
        )

        setupPlayerGestures()

        favorites.addAll(
            getSharedPreferences("NK_FAVORITES", MODE_PRIVATE)
                .getStringSet("videos", emptySet()) ?: emptySet()
        )

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
        }

        val allTab = Button(this).apply {
            text = "ALL VIDEOS"
            setOnClickListener {
                currentTab = "ALL"
                renderVideos()
            }
        }

        val favTab = Button(this).apply {
            text = "♥ FAVORITES"
            setOnClickListener {
                currentTab = "FAVORITES"
                renderVideos()
            }
        }

        tabs.addView(allTab, LinearLayout.LayoutParams(0, dp(46), 1f))
        tabs.addView(favTab, LinearLayout.LayoutParams(0, dp(46), 1f))
        page.addView(tabs)

        countText = TextView(this).apply {
            text = "0 Videos"
            textSize = 12f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.END
        }
        page.addView(countText)

        val scroll = ScrollView(this)

        videoList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(5), dp(5), dp(5))
        }

        scroll.addView(videoList)

        page.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        page.setOnApplyWindowInsetsListener { view, insets ->
            val topInset = if (Build.VERSION.SDK_INT >= 30) {
                insets.getInsets(android.view.WindowInsets.Type.statusBars()).top
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetTop
            }
            view.setPadding(dp(12), dp(8) + topInset, dp(12), dp(5))
            insets
        }

        setContentView(page)
        setupMediaController()
        checkPermission()
    }



    private fun applyVideoScreenMode() {
        if (!::homePage.isInitialized || !::playerView.isInitialized) return
        if (::audioButton.isInitialized) {
            audioButton.visibility = if (isLandscapeMode) View.VISIBLE else View.GONE
        }

        val params = playerView.layoutParams as LinearLayout.LayoutParams

        if (isLandscapeMode) {
            for (i in 0 until homePage.childCount) {
                val child = homePage.getChildAt(i)
                child.visibility =
                    if (child === playerView) View.VISIBLE else View.GONE
            }

            homePage.setPadding(0, 0, 0, 0)
            params.width = LinearLayout.LayoutParams.MATCH_PARENT
            params.height = LinearLayout.LayoutParams.MATCH_PARENT

            if (Build.VERSION.SDK_INT >= 30) {
                window.insetsController?.hide(
                    android.view.WindowInsets.Type.statusBars() or
                    android.view.WindowInsets.Type.navigationBars()
                )
                window.insetsController?.systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }
        } else {
            for (i in 0 until homePage.childCount) {
                homePage.getChildAt(i).visibility = View.VISIBLE
            }

            window.attributes = window.attributes.apply {
                screenBrightness = originalScreenBrightness
            }

            homePage.setPadding(dp(12), dp(8), dp(12), dp(5))
            params.width = LinearLayout.LayoutParams.MATCH_PARENT
            params.height = dp(170)

            if (Build.VERSION.SDK_INT >= 30) {
                window.insetsController?.show(
                    android.view.WindowInsets.Type.statusBars() or
                    android.view.WindowInsets.Type.navigationBars()
                )
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
        }

        playerView.layoutParams = params
    }

    override fun onConfigurationChanged(
        newConfig: android.content.res.Configuration
    ) {
        super.onConfigurationChanged(newConfig)
        if (Build.VERSION.SDK_INT < 24 || !isInPictureInPictureMode) {
            applyVideoScreenMode()
        }
    }



    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(
            isInPictureInPictureMode, newConfig
        )
        if (::playerView.isInitialized) {
            for (i in 0 until playerView.childCount) {
                val child = playerView.getChildAt(i)
                if (child.tag == "nk_overlay_control") {
                    child.visibility =
                        if (isInPictureInPictureMode) View.GONE else View.VISIBLE
                }
            }
        }
    }

    private fun setupPlayerGestures() {
        var startSpan = 0f
        var startX = 0f
        var startY = 0f
        var startBrightness = 0.5f
        var startVolume = 0
        var swipeType = 0
        var swipeChanged = false

        val audioManager =
            getSystemService(AUDIO_SERVICE) as AudioManager

        val scaleDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

                override fun onScaleBegin(
                    detector: ScaleGestureDetector
                ): Boolean {
                    startSpan = detector.currentSpan
                    return true
                }

                override fun onScale(
                    detector: ScaleGestureDetector
                ): Boolean {
                    val current = detector.currentSpan

                    if (!isZoomed && current > startSpan * 1.15f) {
                        isZoomed = true
                        playerView.resizeMode =
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else if (isZoomed && current < startSpan * 0.85f) {
                        isZoomed = false
                        playerView.resizeMode =
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }

                    return true
                }
            }
        )

        val gestureDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {

                override fun onDown(e: MotionEvent): Boolean = true

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    val currentPlayer = player ?: return true
                    val position = currentPlayer.currentPosition
                    val duration = currentPlayer.duration

                    if (e.x < playerView.width / 2f) {
                        currentPlayer.seekTo(
                            (position - 10_000L).coerceAtLeast(0L)
                        )
                        Toast.makeText(
                            this@MainActivity,
                            "⏪ 10 Seconds",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        val next = position + 10_000L
                        currentPlayer.seekTo(
                            if (duration > 0) minOf(next, duration) else next
                        )
                        Toast.makeText(
                            this@MainActivity,
                            "⏩ 10 Seconds",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return true
                }
            }
        )

        playerView.setOnTouchListener { _, event ->

            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)

            when (event.actionMasked) {

                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    swipeType = 0
                    swipeChanged = false

                    val brightness = window.attributes.screenBrightness
                    startBrightness =
                        if (brightness in 0f..1f) brightness else 0.5f

                    startVolume = audioManager.getStreamVolume(
                        AudioManager.STREAM_MUSIC
                    )
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isLandscapeMode &&
                        !scaleDetector.isInProgress
                    ) {
                        val deltaY = startY - event.y

                        if (kotlin.math.abs(deltaY) > dp(8)) {
                            if (swipeType == 0) {
                                swipeType = if (
                                    startX < playerView.width / 2f
                                ) 1 else 2
                            }

                            val fraction =
                                deltaY / playerView.height.toFloat()

                            if (swipeType == 1) {
                                val brightness = (
                                    startBrightness + fraction
                                ).coerceIn(0.05f, 1.0f)

                                val params = window.attributes
                                params.screenBrightness = brightness
                                window.attributes = params
                                swipeChanged = true

                            } else if (swipeType == 2) {
                                val maxVolume = audioManager.getStreamMaxVolume(
                                    AudioManager.STREAM_MUSIC
                                )

                                val newVolume = (
                                    startVolume +
                                    fraction * maxVolume * 2f
                                ).toInt().coerceIn(0, maxVolume)

                                audioManager.setStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    newVolume,
                                    0
                                )
                                swipeChanged = true
                            }
                        }
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (swipeChanged) {
                        val message = if (swipeType == 1) {
                            "☀ Brightness adjusted"
                        } else {
                            "🔊 Volume adjusted"
                        }

                        Toast.makeText(
                            this@MainActivity,
                            message,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                MotionEvent.ACTION_CANCEL -> {
                    swipeType = 0
                }
            }

            false
        }
    }

    private fun checkPermission() {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(permission),
                PERMISSION_REQUEST
            )
        } else {
            loadVideos()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        results: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode, permissions, results
        )

        if (requestCode == PERMISSION_REQUEST) {
            if (results.isNotEmpty() &&
                results[0] == PackageManager.PERMISSION_GRANTED
            ) {
                loadVideos()
            } else {
                status.text = "Please allow video permission"
            }
        }
    }

    private fun loadVideos() {
        allVideos.clear()
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        val columns = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE
        )

        try {
            contentResolver.query(
                uri, columns, null, null,
                MediaStore.Video.Media.DATE_ADDED + " DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val videoUri = ContentUris.withAppendedId(uri, id)

                    allVideos.add(
                        VideoItem(
                            videoUri,
                            cursor.getString(nameCol) ?: "Video",
                            cursor.getLong(durationCol),
                            cursor.getLong(sizeCol),
                            id
                        )
                    )
                }
            }
            renderVideos()
            status.text = "Tap any video to play"
        } catch (e: Exception) {
            status.text = "Error: ${e.message}"
        }
    }

    private fun renderVideos() {
        videoList.removeAllViews()

        val shown = if (currentTab == "FAVORITES") {
            allVideos.filter { favorites.contains(it.uri.toString()) }
        } else {
            allVideos
        }

        countText.text = "${shown.size} Videos"

        if (shown.isEmpty()) {
            val empty = TextView(this).apply {
                text = if (currentTab == "FAVORITES")
                    "No favorite videos yet. Tap ☆ on a video to add it."
                else
                    "No videos found on your device"
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(Color.LTGRAY)
                setPadding(dp(12), dp(35), dp(12), dp(35))
            }
            videoList.addView(empty)
            return
        }

        shown.forEach { item ->
            addVideo(item.uri, item.name, item.duration, item.size, item.id)
        }
    }

    private fun addVideo(
        uri: Uri,
        name: String,
        duration: Long,
        size: Long,
        id: Long
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setBackgroundColor(Color.rgb(29, 29, 36))
        }

        val thumbnail = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.BLACK)
        }

        row.addView(
            thumbnail,
            LinearLayout.LayoutParams(dp(115), dp(76))
        )

        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(4), 0)
        }

        val videoName = TextView(this).apply {
            text = name
            textSize = 13f
            maxLines = 2
            setTextColor(Color.WHITE)
        }

        details.addView(videoName)

        val info = TextView(this).apply {
            text = "${formatTime(duration)} • ${formatSize(size)}"
            textSize = 11f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(5), 0, 0)
        }

        details.addView(info)

        val play = TextView(this).apply {
            text = "▶ PLAY VIDEO"
            textSize = 11f
            setTextColor(Color.rgb(60, 220, 145))
            setPadding(0, dp(5), 0, 0)
        }

        details.addView(play)

        row.addView(
            details,
            LinearLayout.LayoutParams(0, dp(80), 1f)
        )

        val favoriteButton = TextView(this).apply {
            text = if (favorites.contains(uri.toString())) "★" else "☆"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(255, 190, 55))
            setPadding(dp(7), 0, dp(7), 0)
            setOnClickListener {
                val key = uri.toString()
                if (favorites.contains(key)) {
                    favorites.remove(key)
                    Toast.makeText(this@MainActivity, "Removed from Favorites", Toast.LENGTH_SHORT).show()
                } else {
                    favorites.add(key)
                    Toast.makeText(this@MainActivity, "Added to Favorites", Toast.LENGTH_SHORT).show()
                }

                getSharedPreferences("NK_FAVORITES", MODE_PRIVATE)
                    .edit()
                    .putStringSet("videos", HashSet(favorites))
                    .apply()

                renderVideos()
            }
        }

        row.addView(
            favoriteButton,
            LinearLayout.LayoutParams(dp(42), dp(76))
        )

        row.setOnClickListener {
            playVideo(uri)
            status.text = "Playing: $name"
        }

        videoList.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(90)
            )
        )

        val divider = View(this).apply {
            setBackgroundColor(Color.rgb(55, 55, 65))
        }

        videoList.addView(
            divider,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            )
        )

        Thread {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= 29) {
                    contentResolver.loadThumbnail(
                        uri,
                        android.util.Size(320, 200),
                        null
                    )
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Video.Thumbnails.getThumbnail(
                        contentResolver,
                        id,
                        MediaStore.Video.Thumbnails.MINI_KIND,
                        null
                    )
                }

                runOnUiThread {
                    if (bitmap != null && !isFinishing) {
                        thumbnail.setImageBitmap(bitmap)
                    }
                }
            } catch (_: Exception) {
            }
        }.start()
    }

    private fun formatTime(ms: Long): String {
        val seconds = ms / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remaining = seconds % 60

        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, remaining)
        } else {
            "%02d:%02d".format(minutes, remaining)
        }
    }

    private fun formatSize(bytes: Long): String {
        return "%.1f MB".format(
            bytes / (1024.0 * 1024.0)
        )
    }

    private fun openPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }

        startActivityForResult(intent, PICK_VIDEO)
    }

    @Deprecated("Deprecated")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_VIDEO && resultCode == RESULT_OK) {
            val uri = data?.data ?: return

            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }

            playVideo(uri)
        }
    }


    private fun setupMediaController() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future

        future.addListener({
            if (isFinishing) {
                MediaController.releaseFuture(future)
                return@addListener
            }
            try {
                player = future.get()
                playerView.player = player
                pendingVideoUri?.let { uri ->
                    pendingVideoUri = null
                    playVideo(uri)
                }
            } catch (e: Exception) {
                status.text = "Playback service error: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun playVideo(uri: Uri) {
        val controller = player
        if (controller == null) {
            pendingVideoUri = uri
            return
        }
        controller.setMediaItem(MediaItem.fromUri(uri))
        controller.prepare()
        controller.playWhenReady = true
    }

    override fun onStop() {
        super.onStop()
        if (!backgroundAudioEnabled &&
            (Build.VERSION.SDK_INT < 24 || !isInPictureInPictureMode)
        ) {
            player?.pause()
        }
    }

    override fun onDestroy() {
        if (::playerView.isInitialized) playerView.player = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        player = null
        super.onDestroy()
    }
}
