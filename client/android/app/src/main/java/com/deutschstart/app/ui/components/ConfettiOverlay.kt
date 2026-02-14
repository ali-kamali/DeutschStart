package com.deutschstart.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.isActive
import java.util.Random

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var color: Color,
    var rotation: Float,
    var rotationSpeed: Float,
    var size: Float,
    var alpha: Float = 1f
)

@Composable
fun ConfettiOverlay(
    modifier: Modifier = Modifier,
    trigger: Boolean,
) {
    if (!trigger) return

    val particles = remember { mutableStateListOf<Particle>() }
    val random = remember { Random() }

    // Get actual screen width in pixels
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }

        LaunchedEffect(trigger, widthPx) {
            particles.clear()
            repeat(50) {
                particles.add(
                    Particle(
                        x = random.nextFloat() * widthPx,
                        y = -50f,
                        vx = (random.nextFloat() - 0.5f) * 15f,
                        vy = random.nextFloat() * 15f + 10f,
                        color = Color(
                            random.nextInt(256),
                            random.nextInt(256),
                            random.nextInt(256)
                        ),
                        rotation = random.nextFloat() * 360f,
                        rotationSpeed = (random.nextFloat() - 0.5f) * 10f,
                        size = random.nextFloat() * 20f + 10f
                    )
                )
            }

            var timeLeft = 3000L // 3 seconds total

            while (isActive && timeLeft > 0) {
                withFrameMillis { _ ->
                    particles.forEach { p ->
                        p.x += p.vx
                        p.y += p.vy
                        p.vy += 0.5f // Gravity
                        p.rotation += p.rotationSpeed

                        // Fade out in last second
                        if (timeLeft < 1000) {
                            p.alpha = (timeLeft / 1000f).coerceIn(0f, 1f)
                        }
                    }
                }
                timeLeft -= 16L
            }
            particles.clear()
        }

        if (particles.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                particles.forEach { p ->
                    if (p.x in -50f..size.width + 50f && p.y < size.height + 50) {
                        withTransform({
                            translate(p.x, p.y)
                            rotate(p.rotation)
                        }) {
                            drawRect(
                                color = p.color.copy(alpha = p.alpha),
                                topLeft = Offset(-p.size / 2, -p.size / 2),
                                size = androidx.compose.ui.geometry.Size(p.size, p.size)
                            )
                        }
                    }
                }
            }
        }
    }
}
