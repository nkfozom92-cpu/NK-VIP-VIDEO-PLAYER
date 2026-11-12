package com.nk.vipplayer

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

class MainActivity : Activity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var homePage: LinearLayout
    private lateinit var status: TextView
    private lateinit var videoList: LinearLayout
    private lateinit var countText: TextView
    private var isZoomed = false
    private var isLandscapeMode = false
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

        window.statusBarColor = Color.rgb(12, 12, 16)
        window.navigationBarColor = Color.BLACK

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(5))
            setBackgroundColor(Color.rgb(12, 12, 16))
        }

        homePage = page

        val title = TextView(this).apply {
            text = "NK VIP VIDEO PLAYER"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, dp(5), 0, dp(5))
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
        checkPermission()
    }



    private fun applyVideoScreenMode() {
        if (!::homePage.isInitialized || !::playerView.isInitialized) return

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
        applyVideoScreenMode()
    }

    private fun setupPlayerGestures() {
        var startSpan = 0f

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
                        status.text = "Zoom In"
                    } else if (isZoomed && current < startSpan * 0.85f) {
                        isZoomed = false
                        playerView.resizeMode =
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        status.text = "Zoom Out"
                    }

                    return true
                }
            }
        )

        val gestureDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {

                override fun onDown(e: MotionEvent): Boolean {
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    val currentPlayer = player ?: return true
                    val position = currentPlayer.currentPosition
                    val duration = currentPlayer.duration

                    if (e.x < playerView.width / 2f) {
                        currentPlayer.seekTo(
                            (position - 10_000L).coerceAtLeast(0L)
                        )
                        status.text = "⏪ 10 Seconds"
                    } else {
                        val next = position + 10_000L
                        currentPlayer.seekTo(
                            if (duration > 0) minOf(next, duration)
                            else next
                        )
                        status.text = "⏩ 10 Seconds"
                    }

                    return true
                }
            }
        )

        playerView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
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

    private fun playVideo(uri: Uri) {
        if (player == null) {
            player = ExoPlayer.Builder(this).build()
            playerView.player = player

            player?.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    status.text = when (state) {
                        Player.STATE_BUFFERING -> "Loading video..."
                        Player.STATE_READY -> "Video ready"
                        Player.STATE_ENDED -> "Video finished"
                        else -> "NK VIP Player"
                    }
                }
            })
        }

        player?.setMediaItem(MediaItem.fromUri(uri))
        player?.prepare()
        player?.playWhenReady = true
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
