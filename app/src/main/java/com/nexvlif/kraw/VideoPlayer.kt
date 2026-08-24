package com.nexvlif.kraw

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(videoUri: Uri) {
    val context = LocalContext.current

    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    var hudText by remember { mutableStateOf("") }
    var isHudVisible by remember { mutableStateOf(false) }

    fun showHud(text: String) {
        hudText = text
        isHudVisible = true
    }

    var totalDragX by remember { mutableFloatStateOf(0f) }
    var totalDragY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isHudVisible, hudText) {
        if (isHudVisible && hudText != "Pause" && hudText != "2.0x speed") {
            delay(2000)
            isHudVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                if (view.player != exoPlayer) {
                    view.player = exoPlayer
                    exoPlayer.addListener(object : Player.Listener {
                        override fun onVideoSizeChanged(videoSize: VideoSize) {
                            view.post { view.requestLayout() }
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            tryAwaitRelease()
                            exoPlayer.setPlaybackSpeed(1.0f)
                            if (hudText == "2.0x speed") {
                                isHudVisible = false
                            }
                        },
                        onLongPress = {
                            exoPlayer.setPlaybackSpeed(2.0f)
                            showHud("2.0x speed")
                        },
                        onTap = {
                            if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                                showHud("Pause")
                            } else {
                                exoPlayer.play()
                                showHud("Play")
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            totalDragX = 0f
                            totalDragY = 0f
                        },
                        onDragEnd = {
                            isHudVisible = false
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            totalDragX += dragAmount.x
                            totalDragY += dragAmount.y

                            val isHorizontal = abs(totalDragX) > abs(totalDragY)

                            if (isHorizontal) {
                                if (abs(totalDragX) > 30f) {
                                    val seekDeltaMs = (totalDragX * 100).toLong()
                                    val targetPosition = (exoPlayer.currentPosition + seekDeltaMs)
                                        .coerceIn(0, exoPlayer.duration)

                                    exoPlayer.seekTo(targetPosition)

                                    val seconds = targetPosition / 1000
                                    val formatTime =
                                        String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
                                    val seekDif =
                                        if (seekDeltaMs > 0) "+${seekDeltaMs / 1000}s" else "${seekDeltaMs / 1000}s"
                                    showHud("Seek: $formatTime ($seekDif)")

                                    totalDragX = 0f
                                }
                            } else {
                                val screenWidth = size.width
                                val touchX = change.position.x

                                if (touchX < (screenWidth / 2)) {
                                    val activity = context as? Activity
                                    activity?.window?.let { window ->
                                        val layoutParams = window.attributes
                                        var currentBrightness = layoutParams.screenBrightness
                                        if (currentBrightness < 0) currentBrightness = 0.5f

                                        val changeVal = -dragAmount.y / 1000f
                                        val newBrightness =
                                            (currentBrightness + changeVal).coerceIn(0.0f, 1.0f)
                                        layoutParams.screenBrightness = newBrightness
                                        window.attributes = layoutParams

                                        showHud("Brightness: ${(newBrightness * 100).toInt()}%")
                                    }
                                } else {
                                    val maxVolume =
                                        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    val currentVolume =
                                        audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

                                    if (abs(totalDragY) > 20f) {
                                        val step = if (totalDragY < 0) 1 else -1
                                        val newVolume =
                                            (currentVolume + step).coerceIn(0, maxVolume)
                                        audioManager.setStreamVolume(
                                            AudioManager.STREAM_MUSIC,
                                            newVolume,
                                            0
                                        )

                                        val volPercent =
                                            (newVolume.toFloat() / maxVolume * 100).toInt()
                                        showHud("Volume: $volPercent%")
                                        totalDragY = 0f
                                    }
                                }
                            }
                        }
                    )
                }
        )

        AnimatedVisibility(
            visible = isHudVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(8.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = hudText,
                    color = Color.White,
                    fontSize = 18.sp
                )
            }
        }
    }
}
