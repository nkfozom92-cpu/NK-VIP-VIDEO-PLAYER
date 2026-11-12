package com.nk.vipplayer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

class MainActivity : Activity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var status: TextView
    private val PICK_VIDEO = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(10, 12, 22)
        window.navigationBarColor = android.graphics.Color.BLACK

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 24, 18, 18)
            setBackgroundColor(android.graphics.Color.rgb(10, 12, 22))
        }

        val title = TextView(this).apply {
            text = "NK VIP VIDEO PLAYER"
            textSize = 22f
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 18)
        }
        page.addView(title)

        status = TextView(this).apply {
            text = "Choose a video to start"
            textSize = 14f
            setTextColor(android.graphics.Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 12)
        }
        page.addView(status)

        playerView = PlayerView(this).apply {
            useController = true
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        page.addView(playerView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val choose = Button(this).apply {
            text = "SELECT VIDEO"
            setOnClickListener { openVideoPicker() }
        }
        page.addView(choose, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val stop = Button(this).apply {
            text = "STOP PLAYER"
            setOnClickListener {
                player?.stop()
                status.text = "Playback stopped"
            }
        }
        page.addView(stop, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        setContentView(page)
    }

    private fun openVideoPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, PICK_VIDEO)
    }

    @Deprecated("Deprecated by Android, retained for broad compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_VIDEO && resultCode == RESULT_OK) {
            val uri: Uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) { }
            playVideo(uri)
        }
    }

    private fun playVideo(uri: Uri) {
        if (player == null) {
            player = ExoPlayer.Builder(this).build()
            playerView.player = player
            player?.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    status.text = when (playbackState) {
                        Player.STATE_BUFFERING -> "Loading video…"
                        Player.STATE_READY -> "Ready to play"
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
