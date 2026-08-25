package com.nexvlif.kraw

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun WavySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Box(modifier = modifier.fillMaxWidth().height(32.dp)) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxSize(),
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent
            )
        )
        Canvas(modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.Center)) {
            val width = size.width
            val progressWidth = if (valueRange.endInclusive > 0) (value / valueRange.endInclusive) * width else 0f

            drawLine(
                color = color.copy(alpha = 0.2f),
                start = Offset(progressWidth, size.height / 2),
                end = Offset(width, size.height / 2),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )

            val path = Path()
            path.moveTo(0f, size.height / 2)
            val waveLength = 32.dp.toPx()
            val waveHeight = 4.dp.toPx()
            
            var x = 0f
            while (x < progressWidth) {
                val y = size.height / 2 + sin(x / waveLength * 2 * PI.toFloat() + phase) * waveHeight
                path.lineTo(x, y)
                x += 2f
            }
            path.lineTo(progressWidth, size.height / 2)
            
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}
