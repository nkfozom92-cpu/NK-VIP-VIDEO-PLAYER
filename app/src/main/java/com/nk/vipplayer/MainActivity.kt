package com.nk.vipplayer

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
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
    private lateinit var status: TextView
    private lateinit var videoList: LinearLayout
    private lateinit var countText: TextView

    private val PICK_VIDEO = 1001
    private val PERMISSION_REQUEST = 2001

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

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(7), dp(4), dp(5))
        }

        val heading = TextView(this).apply {
            text = "MY VIDEOS"
            textSize = 17f
            setTextColor(Color.WHITE)
        }

        header.addView(
            heading,
            LinearLayout.LayoutParams(0, dp(38), 1f)
        )

        countText = TextView(this).apply {
            text = "0 Videos"
            textSize = 12f
            setTextColor(Color.LTGRAY)
        }
        header.addView(countText)
        page.addView(header)

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

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val refresh = Button(this).apply {
            text = "REFRESH"
            setOnClickListener { checkPermission() }
        }

        val browse = Button(this).apply {
            text = "BROWSE FILES"
            setOnClickListener { openPicker() }
        }

        buttons.addView(
            refresh,
            LinearLayout.LayoutParams(0, dp(48), 1f)
        )

        buttons.addView(
            browse,
            LinearLayout.LayoutParams(0, dp(48), 1f)
        )

        page.addView(buttons)

        val stop = Button(this).apply {
            text = "STOP PLAYER"
            setOnClickListener {
                player?.stop()
                status.text = "Playback stopped"
            }
        }

        page.addView(
            stop,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(45)
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
        videoList.removeAllViews()

        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        val columns = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE
        )

        var total = 0

        try {
            contentResolver.query(
                uri,
                columns,
                null,
                null,
                MediaStore.Video.Media.DATE_ADDED + " DESC"
            )?.use { cursor ->

                val idCol = cursor.getColumnIndexOrThrow(
                    MediaStore.Video.Media._ID
                )

                val nameCol = cursor.getColumnIndexOrThrow(
                    MediaStore.Video.Media.DISPLAY_NAME
                )

                val durationCol = cursor.getColumnIndexOrThrow(
                    MediaStore.Video.Media.DURATION
                )

                val sizeCol = cursor.getColumnIndexOrThrow(
                    MediaStore.Video.Media.SIZE
                )

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Video"
                    val duration = cursor.getLong(durationCol)
                    val size = cursor.getLong(sizeCol)

                    val videoUri =
                        ContentUris.withAppendedId(uri, id)

                    addVideo(
                        videoUri,
                        name,
                        duration,
                        size,
                        id
                    )

                    total++
                }
            }

            countText.text = "$total Videos"

            if (total == 0) {
                val empty = TextView(this).apply {
                    text = "No videos found on your device"
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setTextColor(Color.LTGRAY)
                    setPadding(0, dp(35), 0, dp(35))
                }
                videoList.addView(empty)
                status.text = "No videos found"
            } else {
                status.text = "Tap any video to play"
            }

        } catch (e: Exception) {
            status.text = "Error: ${e.message}"
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
