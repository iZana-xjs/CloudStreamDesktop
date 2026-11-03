package com.lagradost.cloudstream4.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream4.ui.theme.ShiopaStyle
import kotlin.math.cos
import kotlin.math.sin

/**
 * Original line icons for the desktop shell. Drawn with Compose Canvas so the
 * shell does not depend on an icon font (or any third-party asset) and every
 * glyph shares the same stroke weight and optical size.
 */
enum class ShiopaIcon {
    Home, Explore, Watchlist, Downloads, History, Extensions, Settings,
    Search, User, ChevronLeft, ChevronRight, ChevronDown, Play, Pause,
    VideoRewind, VideoForward, Volume, Subtitles, Playlist, Fullscreen,
    Plus, Check, Close
}

@Composable
fun ShiopaIcon(
    icon: ShiopaIcon,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    tint: Color = ShiopaStyle.TextSecondary,
    strokeWidth: Dp = 1.6.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val extent = this.size.minDimension
        val stroke = Stroke(
            width = strokeWidth.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        fun p(x: Float, y: Float): Offset =
            Offset(x / 20f * extent, y / 20f * extent)

        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            drawLine(tint, p(x1, y1), p(x2, y2), strokeWidth.toPx(), StrokeCap.Round)
        }

        fun path(block: Path.() -> Unit) {
            drawPath(Path().apply(block), tint, style = stroke)
        }

        when (icon) {
            ShiopaIcon.Home -> {
                path {
                    moveTo(p(2.9f, 8.5f).x, p(2.9f, 8.5f).y)
                    lineTo(p(10f, 2.6f).x, p(10f, 2.6f).y)
                    lineTo(p(17.1f, 8.5f).x, p(17.1f, 8.5f).y)
                }
                path {
                    moveTo(p(4.8f, 7.6f).x, p(4.8f, 7.6f).y)
                    lineTo(p(4.8f, 17.2f).x, p(4.8f, 17.2f).y)
                    lineTo(p(15.2f, 17.2f).x, p(15.2f, 17.2f).y)
                    lineTo(p(15.2f, 7.6f).x, p(15.2f, 7.6f).y)
                }
                line(10f, 17.2f, 10f, 11.8f)
            }

            ShiopaIcon.Explore -> {
                drawCircle(
                    color = tint,
                    radius = extent * 0.345f,
                    center = p(10f, 10f),
                    style = stroke
                )
                path {
                    moveTo(p(12.9f, 7.1f).x, p(12.9f, 7.1f).y)
                    lineTo(p(8.7f, 11.3f).x, p(8.7f, 11.3f).y)
                    lineTo(p(7.1f, 12.9f).x, p(7.1f, 12.9f).y)
                    lineTo(p(11.3f, 8.7f).x, p(11.3f, 8.7f).y)
                    close()
                }
            }

            ShiopaIcon.Watchlist -> {
                path {
                    moveTo(p(5.6f, 3.1f).x, p(5.6f, 3.1f).y)
                    lineTo(p(14.4f, 3.1f).x, p(14.4f, 3.1f).y)
                    lineTo(p(14.4f, 17.4f).x, p(14.4f, 17.4f).y)
                    lineTo(p(10f, 13.7f).x, p(10f, 13.7f).y)
                    lineTo(p(5.6f, 17.4f).x, p(5.6f, 17.4f).y)
                    close()
                }
            }

            ShiopaIcon.Downloads -> {
                line(10f, 2.9f, 10f, 12.6f)
                path {
                    moveTo(p(5.9f, 8.5f).x, p(5.9f, 8.5f).y)
                    lineTo(p(10f, 12.7f).x, p(10f, 12.7f).y)
                    lineTo(p(14.1f, 8.5f).x, p(14.1f, 8.5f).y)
                }
                path {
                    moveTo(p(3.9f, 14.1f).x, p(3.9f, 14.1f).y)
                    lineTo(p(3.9f, 17.1f).x, p(3.9f, 17.1f).y)
                    lineTo(p(16.1f, 17.1f).x, p(16.1f, 17.1f).y)
                    lineTo(p(16.1f, 14.1f).x, p(16.1f, 14.1f).y)
                }
            }

            ShiopaIcon.History -> {
                drawCircle(
                    color = tint,
                    radius = extent * 0.355f,
                    center = p(10f, 10f),
                    style = stroke
                )
                line(10f, 5.9f, 10f, 10.2f)
                line(10f, 10.2f, 13.2f, 11.9f)
            }

            ShiopaIcon.Extensions -> {
                val cell = 5.6f / 20f * extent
                val radius = CornerRadius(extent * 0.045f, extent * 0.045f)
                listOf(3.1f to 3.1f, 11.3f to 3.1f, 3.1f to 11.3f, 11.3f to 11.3f)
                    .forEach { (x, y) ->
                        drawRoundRect(
                            color = tint,
                            topLeft = p(x, y),
                            size = Size(cell, cell),
                            cornerRadius = radius,
                            style = stroke
                        )
                    }
            }

            ShiopaIcon.Settings -> {
                drawCircle(
                    color = tint,
                    radius = extent * 0.155f,
                    center = p(10f, 10f),
                    style = stroke
                )
                for (index in 0 until 8) {
                    val angle = Math.toRadians((index * 45.0) - 90.0)
                    val start = p(
                        10f + 5.4f * cos(angle).toFloat(),
                        10f + 5.4f * sin(angle).toFloat()
                    )
                    val end = p(
                        10f + 7.4f * cos(angle).toFloat(),
                        10f + 7.4f * sin(angle).toFloat()
                    )
                    drawLine(tint, start, end, strokeWidth.toPx(), StrokeCap.Round)
                }
            }

            ShiopaIcon.Search -> {
                drawCircle(
                    color = tint,
                    radius = extent * 0.275f,
                    center = p(8.6f, 8.6f),
                    style = stroke
                )
                line(12.7f, 12.7f, 17f, 17f)
            }

            ShiopaIcon.User -> {
                drawCircle(
                    color = tint,
                    radius = extent * 0.16f,
                    center = p(10f, 6.9f),
                    style = stroke
                )
                path {
                    moveTo(p(3.9f, 17.2f).x, p(3.9f, 17.2f).y)
                    cubicTo(
                        p(4.5f, 13.0f).x, p(4.5f, 13.0f).y,
                        p(15.5f, 13.0f).x, p(15.5f, 13.0f).y,
                        p(16.1f, 17.2f).x, p(16.1f, 17.2f).y
                    )
                }
            }

            ShiopaIcon.ChevronLeft -> path {
                moveTo(p(12.2f, 4.8f).x, p(12.2f, 4.8f).y)
                lineTo(p(7f, 10f).x, p(7f, 10f).y)
                lineTo(p(12.2f, 15.2f).x, p(12.2f, 15.2f).y)
            }

            ShiopaIcon.ChevronRight -> path {
                moveTo(p(7.8f, 4.8f).x, p(7.8f, 4.8f).y)
                lineTo(p(13f, 10f).x, p(13f, 10f).y)
                lineTo(p(7.8f, 15.2f).x, p(7.8f, 15.2f).y)
            }

            ShiopaIcon.ChevronDown -> path {
                moveTo(p(5.2f, 8.2f).x, p(5.2f, 8.2f).y)
                lineTo(p(10f, 13f).x, p(10f, 13f).y)
                lineTo(p(14.8f, 8.2f).x, p(14.8f, 8.2f).y)
            }

            ShiopaIcon.Play -> {
                drawPath(
                    Path().apply {
                        moveTo(p(6.4f, 3.4f).x, p(6.4f, 3.4f).y)
                        lineTo(p(16f, 10f).x, p(16f, 10f).y)
                        lineTo(p(6.4f, 16.6f).x, p(6.4f, 16.6f).y)
                        close()
                    },
                    tint
                )
            }

            ShiopaIcon.Pause -> {
                val barWidth = extent * 0.22f
                val barHeight = extent * 0.68f
                val top = extent * 0.16f
                drawRoundRect(tint, Offset(extent * 0.25f, top), Size(barWidth, barHeight), CornerRadius(extent * 0.04f))
                drawRoundRect(tint, Offset(extent * 0.58f, top), Size(barWidth, barHeight), CornerRadius(extent * 0.04f))
            }

            ShiopaIcon.VideoRewind, ShiopaIcon.VideoForward -> {
                val backward = icon == ShiopaIcon.VideoRewind
                fun triangle(points: List<Offset>) {
                    drawPath(Path().apply {
                        moveTo(points[0].x, points[0].y)
                        lineTo(points[1].x, points[1].y)
                        lineTo(points[2].x, points[2].y)
                        close()
                    }, tint)
                }
                if (backward) {
                    drawRoundRect(tint, Offset(p(2f, 5f).x, p(2f, 5f).y), Size(extent * 0.09f, extent * 0.5f), CornerRadius(extent * 0.04f))
                    triangle(listOf(p(10f, 4f), p(4f, 10f), p(10f, 16f)))
                    triangle(listOf(p(17f, 4f), p(11f, 10f), p(17f, 16f)))
                } else {
                    drawRoundRect(tint, Offset(p(18f, 5f).x, p(18f, 5f).y), Size(extent * 0.09f, extent * 0.5f), CornerRadius(extent * 0.04f))
                    triangle(listOf(p(3f, 4f), p(9f, 10f), p(3f, 16f)))
                    triangle(listOf(p(10f, 4f), p(16f, 10f), p(10f, 16f)))
                }
            }

            ShiopaIcon.Volume -> {
                drawPath(Path().apply {
                    moveTo(p(2.8f, 8f).x, p(2.8f, 8f).y)
                    lineTo(p(6.1f, 8f).x, p(6.1f, 8f).y)
                    lineTo(p(10.7f, 4.3f).x, p(10.7f, 4.3f).y)
                    lineTo(p(10.7f, 15.7f).x, p(10.7f, 15.7f).y)
                    lineTo(p(6.1f, 12f).x, p(6.1f, 12f).y)
                    lineTo(p(2.8f, 12f).x, p(2.8f, 12f).y)
                    close()
                }, tint)
                path {
                    moveTo(p(13f, 7f).x, p(13f, 7f).y)
                    cubicTo(p(15.5f, 8.4f).x, p(15.5f, 8.4f).y, p(15.5f, 11.6f).x, p(15.5f, 11.6f).y, p(13f, 13f).x, p(13f, 13f).y)
                }
                path {
                    moveTo(p(15f, 4.8f).x, p(15f, 4.8f).y)
                    cubicTo(p(19f, 7.4f).x, p(19f, 7.4f).y, p(19f, 12.6f).x, p(19f, 12.6f).y, p(15f, 15.2f).x, p(15f, 15.2f).y)
                }
            }

            ShiopaIcon.Subtitles -> {
                drawRoundRect(tint, Offset(p(2f, 4f).x, p(2f, 4f).y), Size(extent * 0.8f, extent * 0.58f), CornerRadius(extent * 0.09f), style = stroke)
                line(5f, 9f, 9f, 9f)
                line(11f, 9f, 15f, 9f)
                line(5f, 12f, 8f, 12f)
                line(11f, 12f, 14f, 12f)
            }

            ShiopaIcon.Playlist -> {
                for (y in listOf(5f, 10f, 15f)) drawCircle(tint, extent * 0.075f, p(4f, y))
                line(8f, 5f, 17f, 5f)
                line(8f, 10f, 17f, 10f)
                line(8f, 15f, 17f, 15f)
            }

            ShiopaIcon.Fullscreen -> {
                path { moveTo(p(8f, 3f).x, p(8f, 3f).y); lineTo(p(3f, 3f).x, p(3f, 3f).y); lineTo(p(3f, 8f).x, p(3f, 8f).y) }
                path { moveTo(p(12f, 3f).x, p(12f, 3f).y); lineTo(p(17f, 3f).x, p(17f, 3f).y); lineTo(p(17f, 8f).x, p(17f, 8f).y) }
                path { moveTo(p(3f, 12f).x, p(3f, 12f).y); lineTo(p(3f, 17f).x, p(3f, 17f).y); lineTo(p(8f, 17f).x, p(8f, 17f).y) }
                path { moveTo(p(17f, 12f).x, p(17f, 12f).y); lineTo(p(17f, 17f).x, p(17f, 17f).y); lineTo(p(12f, 17f).x, p(12f, 17f).y) }
            }

            ShiopaIcon.Plus -> {
                line(10f, 4.2f, 10f, 15.8f)
                line(4.2f, 10f, 15.8f, 10f)
            }

            ShiopaIcon.Check -> path {
                moveTo(p(4.4f, 10.5f).x, p(4.4f, 10.5f).y)
                lineTo(p(8.2f, 14.2f).x, p(8.2f, 14.2f).y)
                lineTo(p(15.6f, 6f).x, p(15.6f, 6f).y)
            }

            ShiopaIcon.Close -> {
                line(5.2f, 5.2f, 14.8f, 14.8f)
                line(14.8f, 5.2f, 5.2f, 14.8f)
            }
        }
    }
}

/** Original CloudStream mark used in the navigation rail and account dialog. */
@Composable
fun ShiopaBrandMark(
    size: Dp = 30.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(
                Brush.linearGradient(
                    listOf(ShiopaStyle.AccentMain, ShiopaStyle.AccentMainBright)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        ShiopaIcon(
            icon = ShiopaIcon.Play,
            size = size * 0.42f,
            tint = Color.White,
            strokeWidth = 0.dp
        )
    }
}
