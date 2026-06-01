package io.github.juns_git.android.familystockgate.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ── Full-screen themed background ────────────────────────────────────────────

@Composable
fun ThemedBackground(modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    Canvas(modifier = modifier) {
        when (theme) {
            AppTheme.BEAR_BLUE  -> drawBearBackground()
            AppTheme.BUNNY_PINK -> drawBunnyBackground()
            AppTheme.MODERN     -> drawRect(Color(0xFFF8FAFC))
            AppTheme.DARK       -> drawDarkBackground()
        }
    }
}

private fun DrawScope.drawDarkBackground() {
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFF0F172A), Color(0xFF1A2540)),
            start  = Offset(0f, 0f),
            end    = Offset(0f, size.height)
        )
    )
}

private fun DrawScope.drawBearBackground() {
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFFD4EDFC), Color(0xFFF4EEE3)),
            start  = Offset(0f, 0f),
            end    = Offset(size.width * 0.6f, size.height)
        )
    )
    drawFluffyCloud(cx = size.width * 0.22f, cy = size.height * 0.09f, scale = 1.5f, alpha = 0.28f)
    drawFluffyCloud(cx = size.width * 0.78f, cy = size.height * 0.17f, scale = 1.1f, alpha = 0.22f)
    drawFluffyCloud(cx = size.width * 0.48f, cy = size.height * 0.33f, scale = 0.85f, alpha = 0.18f)
}

private fun DrawScope.drawFluffyCloud(cx: Float, cy: Float, scale: Float, alpha: Float) {
    val b = 40f * scale
    val c = Color.White
    // Overlapping circles forming a puffy cloud silhouette
    drawCircle(c.copy(alpha = alpha), b,          Offset(cx,           cy))
    drawCircle(c.copy(alpha = alpha), b * 0.70f,  Offset(cx - b * 0.80f, cy + b * 0.14f))
    drawCircle(c.copy(alpha = alpha), b * 0.78f,  Offset(cx + b * 0.82f, cy + b * 0.12f))
    drawCircle(c.copy(alpha = alpha), b * 0.58f,  Offset(cx - b * 0.36f, cy - b * 0.42f))
    drawCircle(c.copy(alpha = alpha), b * 0.62f,  Offset(cx + b * 0.40f, cy - b * 0.36f))
    // Flat bottom to sit like a cloud
    drawCircle(c.copy(alpha = alpha), b * 0.52f,  Offset(cx - b * 1.16f, cy + b * 0.24f))
    drawCircle(c.copy(alpha = alpha), b * 0.52f,  Offset(cx + b * 1.18f, cy + b * 0.22f))
}

private fun DrawScope.drawBunnyBackground() {
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFFFDE8E8), Color(0xFFFAE8FF)),
            start  = Offset(0f, 0f),
            end    = Offset(size.width, size.height)
        )
    )
    // Scattered 4-pointed sparkle stars
    data class StarSpec(val x: Float, val y: Float, val s: Float, val yellow: Boolean)
    listOf(
        StarSpec(size.width * 0.07f, size.height * 0.06f, 11f, true),
        StarSpec(size.width * 0.89f, size.height * 0.09f, 14f, false),
        StarSpec(size.width * 0.14f, size.height * 0.27f,  8f, false),
        StarSpec(size.width * 0.83f, size.height * 0.31f, 10f, true),
        StarSpec(size.width * 0.50f, size.height * 0.11f,  9f, true),
        StarSpec(size.width * 0.93f, size.height * 0.53f, 12f, false),
        StarSpec(size.width * 0.04f, size.height * 0.59f,  9f, true),
        StarSpec(size.width * 0.68f, size.height * 0.71f, 11f, false),
        StarSpec(size.width * 0.30f, size.height * 0.85f,  8f, true),
    ).forEach { (x, y, s, yellow) ->
        drawSparkleStar(
            center = Offset(x, y),
            size   = s,
            color  = if (yellow) Color(0xFFFEF08A) else Color.White,
            alpha  = 0.55f
        )
    }
}

private fun DrawScope.drawSparkleStar(center: Offset, size: Float, color: Color, alpha: Float) {
    val col = color.copy(alpha = alpha)
    val q   = size * 0.38f
    // 4-pointed diamond body
    val path = Path().apply {
        moveTo(center.x,         center.y - size)
        lineTo(center.x + q,     center.y - q)
        lineTo(center.x + size,  center.y)
        lineTo(center.x + q,     center.y + q)
        lineTo(center.x,         center.y + size)
        lineTo(center.x - q,     center.y + q)
        lineTo(center.x - size,  center.y)
        lineTo(center.x - q,     center.y - q)
        close()
    }
    drawPath(path, col)
    // Cross-hair sparkle lines
    val sw = (size * 0.14f).coerceAtLeast(1.2f)
    val ext = size * 1.45f
    drawLine(col, Offset(center.x, center.y - ext), Offset(center.x, center.y + ext), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(col, Offset(center.x - ext, center.y), Offset(center.x + ext, center.y), strokeWidth = sw, cap = StrokeCap.Round)
}

// ── Character Badge ───────────────────────────────────────────────────────────

/**
 * Renders the correct character badge for the active theme:
 * MODERN = AccountCircle icon  |  BEAR_BLUE = 아기 곰  |  BUNNY_PINK = 쫑긋 토끼
 */
@Composable
fun CharacterBadge(size: Dp = 48.dp, modifier: Modifier = Modifier) {
    when (LocalAppTheme.current) {
        AppTheme.BEAR_BLUE  -> TeddyBearBadge(size, modifier)
        AppTheme.BUNNY_PINK -> BunnyBadge(size, modifier)
        else ->
            Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector        = Icons.Default.AccountCircle,
                    contentDescription = "프로필",
                    modifier           = Modifier.fillMaxSize(),
                    tint               = MaterialTheme.colorScheme.primary
                )
            }
    }
}

// ── 🧸 아기 곰 뱃지 ──────────────────────────────────────────────────────────

@Composable
private fun TeddyBearBadge(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val r   = this.size.width / 2f
        val cx  = r;  val cy = r
        val earR    = r * 0.30f
        val faceR   = r * 0.76f
        val earBrown  = Color(0xFFCD853F)
        val earInner  = Color(0xFFFAD7A0)
        val faceTan   = Color(0xFFF5CBA7)
        val snoutTan  = Color(0xFFEDD5AA)
        val darkBrown = Color(0xFF5C3317)

        // Ears
        drawCircle(earBrown, earR, Offset(cx - r * 0.52f, cy - r * 0.62f))
        drawCircle(earBrown, earR, Offset(cx + r * 0.52f, cy - r * 0.62f))
        drawCircle(earInner, earR * 0.52f, Offset(cx - r * 0.52f, cy - r * 0.62f))
        drawCircle(earInner, earR * 0.52f, Offset(cx + r * 0.52f, cy - r * 0.62f))

        // Face
        drawCircle(faceTan, faceR, Offset(cx, cy))

        // Snout
        drawCircle(snoutTan, r * 0.36f, Offset(cx, cy + r * 0.22f))

        // Eyes + shines
        drawCircle(darkBrown, r * 0.095f, Offset(cx - r * 0.30f, cy - r * 0.14f))
        drawCircle(darkBrown, r * 0.095f, Offset(cx + r * 0.30f, cy - r * 0.14f))
        drawCircle(Color.White, r * 0.04f, Offset(cx - r * 0.26f, cy - r * 0.18f))
        drawCircle(Color.White, r * 0.04f, Offset(cx + r * 0.34f, cy - r * 0.18f))

        // Nose (inverted triangle)
        val nw  = r * 0.10f
        val ny0 = cy + r * 0.10f
        val ny1 = cy + r * 0.20f
        drawPath(Path().apply {
            moveTo(cx - nw, ny0); lineTo(cx + nw, ny0); lineTo(cx, ny1); close()
        }, Color(0xFF4A2500))

        // Smile
        drawPath(Path().apply {
            moveTo(cx - r * 0.18f, cy + r * 0.24f)
            quadraticBezierTo(cx, cy + r * 0.36f, cx + r * 0.18f, cy + r * 0.24f)
        }, color = darkBrown, style = Stroke(width = r * 0.046f, cap = StrokeCap.Round))
    }
}

// ── 🐰 쫑긋 토끼 뱃지 ───────────────────────────────────────────────────────

@Composable
private fun BunnyBadge(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w      = this.size.width
        val totalH = this.size.height
        val cx     = w / 2f
        // Face: bottom 65% of canvas; ears: top 50%
        val faceR  = w * 0.34f
        val cy     = totalH * 0.68f
        val earW   = w * 0.175f
        val earH   = totalH * 0.52f
        val earTopY = totalH * 0.02f
        val leftX  = cx - w * 0.24f
        val rightX = cx + w * 0.24f
        val bunnyWhite  = Color(0xFFFFF8FB)
        val earPink     = Color(0xFFFCA5A5)
        val eyePurple   = Color(0xFF6B21A8)

        // Left ear
        drawOval(bunnyWhite,
            topLeft = Offset(leftX - earW / 2f, earTopY),
            size    = Size(earW, earH))
        drawOval(earPink,
            topLeft = Offset(leftX - earW * 0.30f, earTopY + earH * 0.07f),
            size    = Size(earW * 0.60f, earH * 0.76f))

        // Right ear
        drawOval(bunnyWhite,
            topLeft = Offset(rightX - earW / 2f, earTopY),
            size    = Size(earW, earH))
        drawOval(earPink,
            topLeft = Offset(rightX - earW * 0.30f, earTopY + earH * 0.07f),
            size    = Size(earW * 0.60f, earH * 0.76f))

        // Face
        drawCircle(bunnyWhite, faceR, Offset(cx, cy))

        // Eyes + shines
        drawCircle(eyePurple, faceR * 0.11f, Offset(cx - faceR * 0.38f, cy - faceR * 0.18f))
        drawCircle(eyePurple, faceR * 0.11f, Offset(cx + faceR * 0.38f, cy - faceR * 0.18f))
        drawCircle(Color.White, faceR * 0.044f, Offset(cx - faceR * 0.33f, cy - faceR * 0.23f))
        drawCircle(Color.White, faceR * 0.044f, Offset(cx + faceR * 0.43f, cy - faceR * 0.23f))

        // X nose
        val nc  = Offset(cx, cy + faceR * 0.15f)
        val cs  = faceR * 0.13f
        val sw  = faceR * 0.075f
        drawLine(BunnyPrimary, Offset(nc.x - cs, nc.y - cs), Offset(nc.x + cs, nc.y + cs), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(BunnyPrimary, Offset(nc.x + cs, nc.y - cs), Offset(nc.x - cs, nc.y + cs), strokeWidth = sw, cap = StrokeCap.Round)
    }
}
