package com.nexvlif.kraw

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(videoUri: Uri, onBack: () -> Unit, isInPip: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current

    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = true
        }
    }

    var playbackPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isPlayingState by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    LaunchedEffect(exoPlayer) {
        while (true) {
            playbackPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            isPlayingState = exoPlayer.isPlaying
            delay(500)
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
            val activity = context as? Activity
            activity?.window?.let { window ->
                val controller = WindowCompat.getInsetsController(window, view)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    var hudText by remember { mutableStateOf("") }
    var isHudVisible by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }

    fun showHud(text: String) {
        hudText = text
        isHudVisible = true
    }

    LaunchedEffect(isHudVisible, isInPip, isLocked) {
        val activity = context as? Activity
        activity?.window?.let { window ->
            val controller = WindowCompat.getInsetsController(window, view)
            if (isInPip || (isLocked && !isHudVisible)) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else if (isHudVisible) {
                controller.show(WindowInsetsCompat.Type.statusBars()) // Only show status bar
                controller.hide(WindowInsetsCompat.Type.navigationBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    LaunchedEffect(isHudVisible, isPlayingState, isLocked, hudText) {
        if (!isLocked && isHudVisible && isPlayingState && hudText != "2.0x speed") {
            delay(4000)
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
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
                playerView.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isInPip, isLocked) {
                    if (isInPip) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            if (isLocked) {
                                isHudVisible = !isHudVisible
                                return@detectTapGestures
                            }
                            tryAwaitRelease()
                            exoPlayer.setPlaybackSpeed(1.0f)
                            playbackSpeed = 1f
                            if (hudText == "2.0x speed") {
                                isHudVisible = false
                                hudText = ""
                            }
                        },
                        onLongPress = {
                            if (isLocked) return@detectTapGestures
                            exoPlayer.setPlaybackSpeed(2.0f)
                            playbackSpeed = 2f
                            showHud("2.0x speed")
                        },
                        onTap = { 
                            if (hudText != "2.0x speed") {
                                isHudVisible = !isHudVisible 
                            }
                        },
                        onDoubleTap = { offset ->
                            if (isLocked) return@detectTapGestures
                            val width = size.width
                            if (offset.x < width * 0.35f) {
                                exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0L))
                                showHud("-10s")
                            } else if (offset.x > width * 0.65f) {
                                exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration))
                                showHud("+10s")
                            }
                        }
                    )
                }
                .pointerInput(isInPip, isLocked) {
                    if (isInPip || isLocked) return@pointerInput
                    var totalDragY = 0f
                    detectDragGestures(
                        onDragStart = { totalDragY = 0f },
                        onDragEnd = { if (!hudText.contains("speed")) isHudVisible = false },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            totalDragY += dragAmount.y
                            val width = size.width
                            val touchX = change.position.x
                            if (touchX < width / 2) {
                                val activity = context as? Activity
                                activity?.window?.let { window ->
                                    val lp = window.attributes
                                    var current = lp.screenBrightness
                                    if (current < 0) current = 0.5f
                                    val next = (current - dragAmount.y / 1000f).coerceIn(0f, 1f)
                                    lp.screenBrightness = next
                                    window.attributes = lp
                                    showHud("Brightness: ${(next * 100).toInt()}%")
                                }
                            } else {
                                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                if (abs(totalDragY) > 30f) {
                                    val delta = if (dragAmount.y < 0) 1 else -1
                                    val next = (current + delta).coerceIn(0, max)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                                    showHud("Volume: ${(next.toFloat() / max * 100).toInt()}%")
                                    totalDragY = 0f
                                }
                            }
                        }
                    )
                }
        )

        AnimatedVisibility(
            visible = isHudVisible && !isInPip && hudText != "2.0x speed",
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.withAlpha(0.15f))
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White)
                    }

                    Row {
                        IconButton(
                            onClick = {
                                resizeMode = when (resizeMode) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                                val modeName = when (resizeMode) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Fit"
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
                                    else -> "Fill"
                                }
                                showHud("Aspect: $modeName")
                            },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clip(CircleShape)
                                .background(Color.White.withAlpha(0.15f))
                        ) {
                            Icon(Icons.Rounded.AspectRatio, "Aspect Ratio", tint = Color.White)
                        }

                        IconButton(
                            onClick = { isLocked = !isLocked },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isLocked) MaterialTheme.colorScheme.primary else Color.White.withAlpha(0.15f))
                        ) {
                            Icon(
                                if (isLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                "Lock",
                                tint = Color.White
                            )
                        }
                    }
                }

                if (!isLocked) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(32.dp)
                    ) {
                        IconButton(
                            onClick = { exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0L)) },
                            modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.White.withAlpha(0.1f))
                        ) {
                            Icon(Icons.Rounded.Replay10, null, tint = Color.White, modifier = Modifier.size(32.dp))
                        }

                        IconButton(
                            onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
                            modifier = Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                if (isPlayingState) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(52.dp)
                            )
                        }

                        IconButton(
                            onClick = { exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration)) },
                            modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.White.withAlpha(0.1f))
                        ) {
                            Icon(Icons.Rounded.Forward10, null, tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                }

                if (!isLocked) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 32.dp),
                        shape = RoundedCornerShape(28.dp),
                        color = Color.White.withAlpha(0.15f),
                        tonalElevation = 0.dp
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    formatTime(playbackPosition),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                
                                TextButton(
                                    onClick = {
                                        playbackSpeed = when (playbackSpeed) {
                                            1f -> 1.25f
                                            1.25f -> 1.5f
                                            1.5f -> 2f
                                            2f -> 0.5f
                                            else -> 1f
                                        }
                                        exoPlayer.setPlaybackSpeed(playbackSpeed)
                                    }
                                ) {
                                    Text(
                                        "${playbackSpeed}x",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }

                                Text(
                                    formatTime(duration),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                            
                            WavySlider(
                                value = playbackPosition.toFloat(),
                                onValueChange = { exoPlayer.seekTo(it.toLong()) },
                                valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isHudVisible && hudText.isNotEmpty() && !hudText.contains("Pause") && !hudText.contains("Play"),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                modifier = Modifier.padding(top = 100.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                tonalElevation = 4.dp
            ) {
                Text(
                    text = hudText,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

private fun Color.withAlpha(alpha: Float): Color = this.copy(alpha = alpha)

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}