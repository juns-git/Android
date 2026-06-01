package io.github.juns_git.android.familystockgate.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import io.github.juns_git.android.familystockgate.ui.viewmodel.AuthState
import kotlin.math.hypot

private const val WEB_CLIENT_ID =
    "578751242407-ojmugfllnskbsqiog6jdd0sgd3jkc5cv.apps.googleusercontent.com"

// [Frame 0] Splash & Login
@Composable
fun SplashLoginScreen(
    authState: AuthState,
    onSignInWithGoogle: (idToken: String) -> Unit,
    innerPadding: PaddingValues
) {
    val context = LocalContext.current
    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }
    var localError by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken  = account.idToken
            if (idToken != null) { localError = null; onSignInWithGoogle(idToken) }
            else localError = "Google ID 토큰을 받지 못했습니다."
        } catch (e: ApiException) {
            localError = "Google 로그인 실패 (코드 ${e.statusCode})"
        }
    }

    val isLoading    = authState is AuthState.Loading
    val displayError = (authState as? AuthState.Error)?.message ?: localError

    Box(modifier = Modifier.fillMaxSize()) {

        // ── 전체화면 프리미엄 다크 배경 ─────────────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 네이비 → 다크차콜 그라데이션 배경
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F172A), Color(0xFF1A2540), Color(0xFF0C1220)),
                    startY = 0f,
                    endY   = size.height
                )
            )
            drawSplashBackground()
        }

        // ── 메인 콘텐츠 ───────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 중앙 로고 심볼
            Canvas(modifier = Modifier.size(180.dp)) { drawFsgLogo() }

            Spacer(Modifier.height(28.dp))

            Text(
                text       = "Family Stock Gate",
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color      = Color.White,
                textAlign  = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text      = "함께 배우는 실전 투자 교육",
                style     = MaterialTheme.typography.bodyMedium,
                color     = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(72.dp))

            if (displayError != null) {
                Text(
                    text      = displayError,
                    style     = MaterialTheme.typography.bodySmall,
                    color     = Color(0xFFF87171),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
            }

            Button(
                onClick = {
                    localError = null
                    // 이전 Google 세션을 먼저 해제해야 계정 선택 피커가 다시 표시된다
                    googleSignInClient.signOut().addOnCompleteListener {
                        launcher.launch(googleSignInClient.signInIntent)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !isLoading,
                colors  = ButtonDefaults.buttonColors(
                    containerColor         = Color(0xFF2563EB),
                    disabledContainerColor = Color(0xFF1E3A5F)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color       = Color.White
                    )
                } else {
                    Text(
                        text       = "Google 계정으로 계속하기",
                        color      = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ── 배경: 은은한 주식 차트 격자 패턴 ─────────────────────────────────────────────
private fun DrawScope.drawSplashBackground() {
    // 수평 격자선
    repeat(8) { i ->
        val y = size.height * (i + 1) / 9f
        drawLine(
            Color.White.copy(alpha = 0.04f),
            Offset(0f, y), Offset(size.width, y),
            strokeWidth = 0.6.dp.toPx()
        )
    }
    // 수직 격자선
    repeat(5) { i ->
        val x = size.width * (i + 1) / 6f
        drawLine(
            Color.White.copy(alpha = 0.025f),
            Offset(x, 0f), Offset(x, size.height),
            strokeWidth = 0.6.dp.toPx()
        )
    }

    val dashStroke = Stroke(
        width = 1.1.dp.toPx(),
        cap   = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
    )

    // 차트 라인 1 — 하단 광폭 우상향 (alpha 0.13)
    drawStockChartLine(
        listOf(
            0.00f to 0.84f, 0.07f to 0.78f, 0.12f to 0.73f, 0.17f to 0.80f,
            0.23f to 0.68f, 0.29f to 0.71f, 0.35f to 0.62f, 0.41f to 0.59f,
            0.47f to 0.64f, 0.53f to 0.55f, 0.60f to 0.49f, 0.66f to 0.52f,
            0.72f to 0.43f, 0.81f to 0.37f, 1.00f to 0.29f
        ),
        color = Color(0xFFE2E8F0).copy(alpha = 0.13f),
        style = dashStroke
    )
    // 차트 라인 2 — 상단 (alpha 0.09)
    drawStockChartLine(
        listOf(
            0.00f to 0.60f, 0.06f to 0.55f, 0.12f to 0.61f, 0.18f to 0.52f,
            0.25f to 0.46f, 0.31f to 0.50f, 0.37f to 0.41f, 0.44f to 0.45f,
            0.51f to 0.37f, 0.57f to 0.33f, 0.64f to 0.37f, 0.71f to 0.29f,
            0.79f to 0.24f, 0.87f to 0.20f, 1.00f to 0.15f
        ),
        color = Color(0xFFE2E8F0).copy(alpha = 0.09f),
        style = dashStroke
    )
    // 차트 라인 3 — 최하단 (alpha 0.07)
    drawStockChartLine(
        listOf(
            0.00f to 0.95f, 0.08f to 0.90f, 0.15f to 0.93f, 0.21f to 0.85f,
            0.29f to 0.88f, 0.37f to 0.78f, 0.44f to 0.81f, 0.51f to 0.73f,
            0.59f to 0.68f, 0.66f to 0.72f, 0.74f to 0.63f, 0.83f to 0.58f,
            1.00f to 0.50f
        ),
        color = Color(0xFFE2E8F0).copy(alpha = 0.07f),
        style = dashStroke
    )
}

private fun DrawScope.drawStockChartLine(
    pts: List<Pair<Float, Float>>,
    color: Color,
    style: Stroke
) {
    val path = Path()
    path.moveTo(pts[0].first * size.width, pts[0].second * size.height)
    pts.drop(1).forEach { (nx, ny) -> path.lineTo(nx * size.width, ny * size.height) }
    drawPath(path, color = color, style = style)
}

// ── 중앙 로고: 집 + 자녀 + 황금 우상향 화살표 ──────────────────────────────────
private fun DrawScope.drawFsgLogo() {
    val w = size.width
    val h = size.height

    // ── 집(House Gate) 실루엣 테두리 ────────────────────────────────────────
    val housePath = Path().apply {
        moveTo(w * 0.50f, h * 0.19f)   // 지붕 꼭대기
        lineTo(w * 0.12f, h * 0.50f)   // 좌측 처마 끝
        lineTo(w * 0.20f, h * 0.50f)   // 좌측 벽 상단
        lineTo(w * 0.20f, h * 0.88f)   // 좌측 벽 하단
        lineTo(w * 0.40f, h * 0.88f)   // 문 좌측 하단
        lineTo(w * 0.40f, h * 0.64f)   // 문 좌측 상단
        lineTo(w * 0.60f, h * 0.64f)   // 문 우측 상단
        lineTo(w * 0.60f, h * 0.88f)   // 문 우측 하단
        lineTo(w * 0.80f, h * 0.88f)   // 우측 벽 하단
        lineTo(w * 0.80f, h * 0.50f)   // 우측 벽 상단
        lineTo(w * 0.88f, h * 0.50f)   // 우측 처마 끝
        close()                          // → 지붕 꼭대기
    }
    // 글로우 레이어
    drawPath(housePath, color = Color(0xFF60A5FA).copy(alpha = 0.06f),
        style = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(housePath, color = Color(0xFF60A5FA).copy(alpha = 0.11f),
        style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    // 클린 아웃라인
    drawPath(housePath, color = Color.White.copy(alpha = 0.93f),
        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

    // ── 자녀 실루엣 배지 (집 내부 좌우 베이) ────────────────────────────────
    val cY    = h * 0.58f
    val headR = w * 0.087f
    val bodyR = w * 0.055f
    val bodyDy = headR * 1.20f

    // 아들 — 하늘색 (좌측 베이: x 20%~40%)
    val sonX = w * 0.30f
    drawCircle(Color(0xFF7DD3FC).copy(alpha = 0.20f), headR * 1.65f, Offset(sonX, cY))
    drawCircle(Color(0xFF38BDF8), headR, Offset(sonX, cY - headR * 0.10f))
    drawCircle(Color(0xFF0284C7).copy(alpha = 0.88f), bodyR, Offset(sonX, cY + bodyDy))

    // 딸 — 연분홍 (우측 베이: x 60%~80%)
    val dtrX = w * 0.70f
    drawCircle(Color(0xFFFDA4AF).copy(alpha = 0.20f), headR * 1.65f, Offset(dtrX, cY))
    drawCircle(Color(0xFFFB7185), headR, Offset(dtrX, cY - headR * 0.10f))
    drawCircle(Color(0xFFE11D48).copy(alpha = 0.88f), bodyR, Offset(dtrX, cY + bodyDy))

    // ── 황금/에메랄드 우상향 화살표 곡선 그래프 ─────────────────────────────
    val start  = Offset(w * 0.04f, h * 0.94f)
    val cp1    = Offset(w * 0.26f, h * 0.79f)
    val cp2    = Offset(w * 0.57f, h * 0.47f)
    val tipEnd = Offset(w * 0.94f, h * 0.09f)

    val curvePath = Path().apply {
        moveTo(start.x, start.y)
        cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, tipEnd.x, tipEnd.y)
    }
    // 글로우 레이어
    drawPath(curvePath, color = Color(0xFFFBBF24).copy(alpha = 0.10f),
        style = Stroke(width = 15.dp.toPx(), cap = StrokeCap.Round))
    drawPath(curvePath, color = Color(0xFFFBBF24).copy(alpha = 0.20f),
        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round))
    // 황금→에메랄드 메인 라인
    drawPath(
        curvePath,
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFFF59E0B), Color(0xFFFBBF24), Color(0xFF34D399)),
            start  = start,
            end    = tipEnd
        ),
        style = Stroke(width = 3.6.dp.toPx(), cap = StrokeCap.Round)
    )

    // 화살표 헤드 (cp2→tipEnd 방향)
    val dx  = tipEnd.x - cp2.x
    val dy  = tipEnd.y - cp2.y
    val mag = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(0.001f)
    val nx = dx / mag;  val ny = dy / mag     // 방향 벡터
    val px = -ny;       val py =  nx           // 수직 벡터
    val aLen  = w * 0.075f
    val aHalf = w * 0.041f
    val bx = tipEnd.x - nx * aLen
    val by = tipEnd.y - ny * aLen
    drawPath(
        Path().apply {
            moveTo(tipEnd.x, tipEnd.y)
            lineTo(bx + px * aHalf, by + py * aHalf)
            lineTo(bx - px * aHalf, by - py * aHalf)
            close()
        },
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFFFBBF24), Color(0xFF34D399)),
            start  = Offset(bx, by),
            end    = tipEnd
        )
    )

    // 가격 노드 도트 (베지어 곡선 위의 주요 가격 포인트)
    listOf(0.18f, 0.35f, 0.52f, 0.68f, 0.83f).forEach { t ->
        val u  = 1f - t
        val npx = u*u*u*start.x + 3*u*u*t*cp1.x + 3*u*t*t*cp2.x + t*t*t*tipEnd.x
        val npy = u*u*u*start.y + 3*u*u*t*cp1.y + 3*u*t*t*cp2.y + t*t*t*tipEnd.y
        val nc  = if (t < 0.5f) Color(0xFFFBBF24) else Color(0xFF34D399)
        drawCircle(nc.copy(alpha = 0.90f), 3.0.dp.toPx(), Offset(npx, npy))
        drawCircle(Color.White.copy(alpha = 0.55f), 1.4.dp.toPx(), Offset(npx, npy))
    }
}
