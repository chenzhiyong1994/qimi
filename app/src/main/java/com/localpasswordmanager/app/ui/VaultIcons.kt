package com.localpasswordmanager.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

enum class VaultIconKind {
    Lock, Shield, Search, Add, ChevronRight, ArrowLeft, Eye, EyeOff,
    Copy, More, Check, Key, Info, Close, User, Globe, Note,
}

/** 本地图形，以 24 dp 线框绘制；有文字标签的按钮传入 null，避免重复朗读。 */
@Composable
fun VaultIcon(
    kind: VaultIconKind,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val accessible = if (contentDescription == null) modifier else modifier.semantics {
        this.contentDescription = contentDescription
    }
    Canvas(accessible.size(24.dp)) {
        withTransform({ scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) }) {
            glyph(kind, tint)
        }
    }
}

private fun DrawScope.glyph(kind: VaultIconKind, color: Color) {
    val stroke = Stroke(width = 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(
        color, Offset(x1, y1), Offset(x2, y2), strokeWidth = stroke.width, cap = StrokeCap.Round,
    )
    fun path(block: Path.() -> Unit) = drawPath(Path().apply(block), color, style = stroke)
    fun circle(x: Float, y: Float, radius: Float) = drawCircle(color, radius, Offset(x, y), style = stroke)
    fun roundRect(x: Float, y: Float, width: Float, height: Float, radius: Float = 2f) = drawRoundRect(
        color, Offset(x, y), Size(width, height), CornerRadius(radius), style = stroke,
    )

    when (kind) {
        VaultIconKind.Lock -> {
            roundRect(5f, 10f, 14f, 11f, 2.5f)
            path { moveTo(8f, 10f); lineTo(8f, 7f); cubicTo(8f, 1.7f, 16f, 1.7f, 16f, 7f); lineTo(16f, 10f) }
            line(12f, 14f, 12f, 17f)
        }
        VaultIconKind.Shield -> {
            path {
                moveTo(12f, 3f); lineTo(19f, 6f); lineTo(19f, 11f)
                cubicTo(19f, 16f, 16f, 19f, 12f, 21f)
                cubicTo(8f, 19f, 5f, 16f, 5f, 11f); lineTo(5f, 6f); close()
            }
            path { moveTo(8.5f, 11.8f); lineTo(11f, 14.3f); lineTo(15.5f, 9.8f) }
        }
        VaultIconKind.Search -> { circle(10.5f, 10.5f, 6.5f); line(15.3f, 15.3f, 20f, 20f) }
        VaultIconKind.Add -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
        VaultIconKind.ChevronRight -> path { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }
        VaultIconKind.ArrowLeft -> {
            line(19f, 12f, 5f, 12f)
            path { moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f) }
        }
        VaultIconKind.Eye, VaultIconKind.EyeOff -> {
            path {
                moveTo(2.5f, 12f)
                cubicTo(7.5f, 4.6f, 16.5f, 4.6f, 21.5f, 12f)
                cubicTo(16.5f, 19.4f, 7.5f, 19.4f, 2.5f, 12f); close()
            }
            circle(12f, 12f, 2.8f)
            if (kind == VaultIconKind.EyeOff) line(3f, 3f, 21f, 21f)
        }
        VaultIconKind.Copy -> {
            roundRect(8f, 8f, 12f, 13f)
            path { moveTo(15f, 4f); lineTo(6f, 4f); cubicTo(4.9f, 4f, 4f, 4.9f, 4f, 6f); lineTo(4f, 15f) }
        }
        VaultIconKind.More -> {
            drawCircle(color, 1.5f, Offset(5f, 12f))
            drawCircle(color, 1.5f, Offset(12f, 12f))
            drawCircle(color, 1.5f, Offset(19f, 12f))
        }
        VaultIconKind.Check -> path { moveTo(4f, 12f); lineTo(9f, 17f); lineTo(20f, 6f) }
        VaultIconKind.Key -> {
            circle(8.5f, 8.5f, 5f)
            line(12f, 12f, 20.5f, 20.5f)
            line(16f, 16f, 19f, 13f)
            line(18.5f, 18.5f, 21.5f, 15.5f)
        }
        VaultIconKind.Info -> {
            circle(12f, 12f, 9f)
            drawCircle(color, 1f, Offset(12f, 7.5f))
            line(12f, 11f, 12f, 17f)
        }
        VaultIconKind.Close -> { line(6f, 6f, 18f, 18f); line(6f, 18f, 18f, 6f) }
        VaultIconKind.User -> {
            circle(12f, 7.5f, 3.8f)
            path { moveTo(4.5f, 21f); lineTo(4.5f, 19f); cubicTo(4.5f, 12.1f, 19.5f, 12.1f, 19.5f, 19f); lineTo(19.5f, 21f) }
        }
        VaultIconKind.Globe -> {
            circle(12f, 12f, 9f)
            line(3f, 12f, 21f, 12f)
            path { moveTo(12f, 3f); cubicTo(5.8f, 8f, 5.8f, 16f, 12f, 21f); cubicTo(18.2f, 16f, 18.2f, 8f, 12f, 3f); close() }
        }
        VaultIconKind.Note -> {
            path { moveTo(6f, 3f); lineTo(14f, 3f); lineTo(19f, 8f); lineTo(19f, 21f); lineTo(5f, 21f); lineTo(5f, 4f); quadraticTo(5f, 3f, 6f, 3f); close() }
            path { moveTo(14f, 3f); lineTo(14f, 8f); lineTo(19f, 8f) }
            line(8f, 12f, 16f, 12f)
            line(8f, 16f, 14f, 16f)
        }
    }
}
