package io.github.juns_git.android.familystockgate.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.juns_git.android.familystockgate.data.model.ChartPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import io.github.juns_git.android.familystockgate.data.model.TradeType
import io.github.juns_git.android.familystockgate.data.model.UserRole
import io.github.juns_git.android.familystockgate.ui.theme.StockDown
import io.github.juns_git.android.familystockgate.ui.theme.StockUp
import io.github.juns_git.android.familystockgate.ui.viewmodel.AppViewModel
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import io.github.juns_git.android.familystockgate.data.model.TradeStatus

private enum class MarkerKind { BUY_ONLY, SELL_ONLY, MIXED }

private data class TradeMarker(
    val date: String,
    val kind: MarkerKind,
    val avgPrice: Long,
    val memo: String,           // 대표 메모 (첫 번째 비어있지 않은 것) — 차트 라벨 표시용
    val allMemos: List<String>, // 전체 메모 목록 — 팝업 상세보기용
    val completedAt: Long
)

// [Frame 4~5] Trade & Details
// source: "holdings" → 매수+매도 모두 활성 | "watchlist" → 매수만 활성
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeScreen(
    viewModel: AppViewModel,
    innerPadding: PaddingValues,
    stockTicker: String,
    stockName: String,
    source: String,           // "holdings" | "watchlist"
    onBack: () -> Unit
) {
    val role           by viewModel.debugRole.collectAsState()
    val holdings       by viewModel.holdings.collectAsState()
    val watchlist      by viewModel.watchlist.collectAsState()
    val availableCash  by viewModel.availableCash.collectAsState()
    val commissionRate by viewModel.commissionRate.collectAsState()
    val taxRate        by viewModel.taxRate.collectAsState()
    val errorMessage   by viewModel.errorMessage.collectAsState()
    val chartData          by viewModel.stockChartData.collectAsState()
    val chartDisplayOffset by viewModel.chartDisplayOffset.collectAsState()
    val isChartLoading     by viewModel.isChartLoading.collectAsState()
    val tradeRequests  by viewModel.tradeRequests.collectAsState()

    val markerSdf = remember { java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.KOREA) }
    val tradeMarkers: List<TradeMarker> = remember(tradeRequests, stockTicker) {
        tradeRequests
            .filter { req ->
                req.stockTicker == stockTicker &&
                (req.type == TradeType.BUY || req.type == TradeType.SELL) &&
                (req.status == TradeStatus.FILLED || req.status == TradeStatus.PARTIAL_FILLED) &&
                req.completedAt > 0L
            }
            .groupBy { req -> markerSdf.format(java.util.Date(req.completedAt)) }
            .map { (date, trades) ->
                val buyCount  = trades.count { it.type == TradeType.BUY }
                val sellCount = trades.count { it.type == TradeType.SELL }
                val kind = when {
                    buyCount > 0 && sellCount > 0 -> MarkerKind.MIXED
                    buyCount > 0                  -> MarkerKind.BUY_ONLY
                    else                          -> MarkerKind.SELL_ONLY
                }
                val totalQty  = trades.sumOf { it.filledQuantity.toLong() }.coerceAtLeast(1L)
                val avgPrice  = trades.sumOf { it.filledPrice * it.filledQuantity.toLong() } / totalQty
                val allMemos  = trades.mapNotNull { it.memo.takeIf { m -> m.isNotBlank() } }
                val reprMemo  = allMemos.firstOrNull() ?: ""
                val lastAt    = trades.maxOf { it.completedAt }
                TradeMarker(date, kind, avgPrice, reprMemo, allMemos, lastAt)
            }
    }

    val chartPeriods = remember { listOf("2w" to 21, "1m" to 45, "2m" to 75, "3m" to 100, "6m" to 200, "1y" to 400) }
    var selectedPeriodDays by remember { mutableIntStateOf(75) }

    LaunchedEffect(stockTicker, selectedPeriodDays) { viewModel.fetchStockChartHistory(stockTicker, stockName, selectedPeriodDays) }

    val holdingItem = holdings.find { it.stock.ticker == stockTicker }
    val currentPrice = holdingItem?.stock?.currentPrice
        ?: watchlist.find { it.ticker == stockTicker }?.currentPrice
        ?: 0L

    val isFromHoldings = source == "holdings"
    val holdingQty = holdingItem?.quantity ?: 0

    // 매수/매도 선택 — 관심 종목 진입 시 매도 비활성
    var tradeType by remember { mutableStateOf(TradeType.BUY) }

    var quantityInt  by remember { mutableIntStateOf(0) }
    // 내부 Long 상태로 관리 → 표시 시 천 단위 콤마 포맷팅
    var priceValue   by remember { mutableLongStateOf(currentPrice) }
    var memo         by remember { mutableStateOf("") }
    var showDividendDialog by remember { mutableStateOf(false) }

    val pricePerShare = priceValue
    val totalAmount = quantityInt.toLong() * pricePerShare

    // 수수료·세금 합산 요율 (예: 0.015+0.18 → 0.00195)
    val totalFeeRate = (commissionRate + taxRate) / 100.0

    // 수량 상한: 매수는 수수료·세금 포함 실부담가 기준으로 역산
    // costPerShareWithFee = 주당 최종 부담액(반올림), maxBuyQty = 잔고로 살 수 있는 최대 정수 수량
    val costPerShareWithFee = if (pricePerShare > 0)
        Math.round(pricePerShare.toDouble() * (1.0 + totalFeeRate)) else 0L
    val maxBuyQty = if (costPerShareWithFee > 0) (availableCash / costPerShareWithFee).toInt() else 0
    val maxQty = if (tradeType == TradeType.BUY) maxBuyQty else holdingQty

    // 수수료·세금 포함 실부담액 / 실수령액 (매수 초과 여부 판정 기준)
    val totalWithFee  = Math.round(totalAmount.toDouble() * (1.0 + totalFeeRate))
    val netAfterFee   = Math.round(totalAmount.toDouble() * (1.0 - totalFeeRate))

    // 수량 유효성 (매수: 수수료·세금 포함 총 부담액 기준)
    val quantityErrorMsg: String? = when {
        quantityInt <= 0 -> null
        tradeType == TradeType.BUY && pricePerShare > 0 && totalWithFee > availableCash ->
            "예수금 초과 — 수수료·세금 포함 ₩${"%,d".format(totalWithFee)} 필요 (최대 ${maxBuyQty}주)"
        tradeType == TradeType.SELL && quantityInt > holdingQty ->
            "보유 수량 초과 (최대 ${holdingQty}주)"
        else -> null
    }

    val isMemoSufficient = memo.trim().length >= 4
    val isFormValid = quantityInt > 0 && pricePerShare > 0 && isMemoSufficient && quantityErrorMsg == null

    // 매수/매도 별 메모 가이드 텍스트 (역할 + 거래 종류 분기)
    val memoGuide = when {
        role == UserRole.PARENT && tradeType == TradeType.BUY ->
            "이 종목을 매수하는 이유와 투자 판단 근거를 메모하세요. 장부에 기록됩니다."
        role == UserRole.PARENT && tradeType == TradeType.SELL ->
            "이 종목을 매도하는 이유와 수익/손실 판단을 메모하세요. 장부에 기록됩니다."
        tradeType == TradeType.BUY ->
            "이 주식을 지금 사야 하는 이유를 부모님께 설명해 주세요."
        else ->
            "이 주식을 지금 팔아서 이익/손실을 확정하려는 이유를 부모님께 설명해 주세요."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(stockName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "$stockTicker · ${if (isFromHoldings) "보유 종목" else "관심 종목"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ── 종목 정보 + 30일 차트 ────────────────────────────
            val priceChange = holdingItem?.stock?.changeRate
                ?: watchlist.find { it.ticker == stockTicker }?.changeRate
                ?: 0.0
            val chartLineColor = when {
                chartData.size >= 2 -> if (chartData.last().price >= chartData.first().price) StockUp else StockDown
                else                -> if (priceChange >= 0) StockUp else StockDown
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    // ── 종목명 / 현재가 행 ─────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stockName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stockTicker,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (currentPrice > 0) "₩${"%,d".format(currentPrice)}" else "-",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = chartLineColor
                            )
                            if (isFromHoldings && holdingItem != null) {
                                Text(
                                    text = "${if (holdingItem.profitRate >= 0) "+" else ""}${"%.2f".format(holdingItem.profitRate)}% · ${holdingQty}주",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (holdingItem.profitRate >= 0) StockUp else StockDown
                                )
                            }
                        }
                    }
                    // ── 기간 버튼(왼쪽) + MA 배지(오른쪽) 동일 행 ──────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 기간 버튼 — 왼쪽 정렬
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            chartPeriods.forEach { (label, days) ->
                                val isSelected = days == selectedPeriodDays
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .background(
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                    else Color.Transparent,
                                            shape = MaterialTheme.shapes.extraSmall
                                        )
                                        .clickable { selectedPeriodDays = days }
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                        // MA 배지 — 오른쪽 정렬
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "MA20",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF34D399),
                                modifier = Modifier
                                    .background(
                                        Color(0xFF34D399).copy(alpha = 0.15f),
                                        MaterialTheme.shapes.extraSmall
                                    )
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                            Text(
                                text = "MA5",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFBBF24),
                                modifier = Modifier
                                    .background(
                                        Color(0xFFFBBF24).copy(alpha = 0.15f),
                                        MaterialTheme.shapes.extraSmall
                                    )
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxSize()) {
                        when {
                            isChartLoading -> CircularProgressIndicator(
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.Center),
                                strokeWidth = 2.dp
                            )
                            chartData.size >= 2 -> StockLineChart(
                                data          = chartData,
                                displayOffset = chartDisplayOffset,
                                lineColor     = chartLineColor,
                                tradeMarkers  = tradeMarkers,
                                stockName     = stockName,
                                modifier      = Modifier.fillMaxSize()
                            )
                            else -> Text(
                                text = "차트 데이터를 불러올 수 없습니다",
                                modifier = Modifier.align(Alignment.Center),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // ── 매수 / 매도 선택 칩 + 우측 배당 버튼 ────────────
            // 관심 종목 진입 시 매도 칩 비활성화 (원천 차단)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(TradeType.BUY, TradeType.SELL).forEach { type ->
                    val isSellFromWatchlist = type == TradeType.SELL && !isFromHoldings
                    FilterChip(
                        selected = tradeType == type,
                        onClick = {
                            if (!isSellFromWatchlist) {
                                tradeType = type
                                quantityInt = 0  // 모드 전환 시 수량 초기화
                            }
                        },
                        label = { Text(if (type == TradeType.BUY) "매수" else "매도") },
                        enabled = !isSellFromWatchlist,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                if (!isFromHoldings) {
                    Text(
                        text = "매도 불가 (미보유 종목)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                if (isFromHoldings) {
                    OutlinedButton(
                        onClick = { showDividendDialog = true },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("배당", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // ── 가격 입력: [−] [필드] [+] (KRX 코스피 호가단위 스텝) ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // [−] 버튼: 0원 미만으로 내려가지 않도록 coerceAtLeast(0) 하한선 적용
                OutlinedButton(
                    onClick = {
                        val step = kospiPriceStep(priceValue)
                        priceValue = (priceValue - step).coerceAtLeast(0L)
                    },
                    modifier = Modifier.size(48.dp),
                    contentPadding = PaddingValues(0.dp),
                    enabled = priceValue > 0L   // 0원에서는 비활성화
                ) { Text("−", style = MaterialTheme.typography.titleMedium) }

                // 가격 TextField: 내부 Long → 천 단위 콤마 포맷으로 표시
                // onValueChange: 콤마 포함 입력을 숫자만 추출하여 Long으로 파싱
                OutlinedTextField(
                    value = if (priceValue > 0L) "%,d".format(priceValue) else "",
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() }
                        // 빈 문자열이면 0, 아니면 파싱 (파싱 실패 시 기존 값 유지)
                        priceValue = digits.toLongOrNull() ?: 0L
                    },
                    label = { Text("가격 (원)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    suffix = { Text("원") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                // [+] 버튼
                OutlinedButton(
                    onClick = {
                        if (priceValue == 0L) {
                            // 0원일 때: 현재 시장가로 점프
                            if (currentPrice > 0L) {
                                priceValue = currentPrice
                            } else {
                                // 시장가 데이터 없음 → 최소 호가단위 1원으로 시작 (방어 로그)
                                android.util.Log.w("TradeScreen", "currentPrice=0, defaulting to 1")
                                priceValue = 1L
                            }
                        } else {
                            val step = kospiPriceStep(priceValue)
                            priceValue = priceValue + step
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Text("+", style = MaterialTheme.typography.titleMedium) }
            }

            // 현재 호가단위 안내 (가격이 0이면 표시 안 함)
            Text(
                text = if (priceValue > 0L)
                    "현재 호가단위: ${"%,d".format(kospiPriceStep(priceValue))}원"
                else
                    "[+] 클릭 시 현재 시장가(₩${"%,d".format(currentPrice)})로 자동 설정",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            // ── 수량 입력: [-] [수량] [+] [최대] ────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // [-] 버튼
                OutlinedButton(
                    onClick = { if (quantityInt > 0) quantityInt-- },
                    modifier = Modifier.size(48.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("−", style = MaterialTheme.typography.titleMedium)
                }

                // 수량 입력 필드
                OutlinedTextField(
                    value = if (quantityInt == 0) "" else quantityInt.toString(),
                    onValueChange = { raw ->
                        val v = raw.filter { it.isDigit() }.toIntOrNull() ?: 0
                        quantityInt = v.coerceIn(0, if (maxQty > 0) maxQty else Int.MAX_VALUE)
                    },
                    label = { Text("수량") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    suffix = { Text("주") },
                    isError = quantityErrorMsg != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                // [+] 버튼
                OutlinedButton(
                    onClick = { if (quantityInt < maxQty) quantityInt++ },
                    modifier = Modifier.size(48.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("+", style = MaterialTheme.typography.titleMedium)
                }

                // [최대] 버튼
                OutlinedButton(
                    onClick = { quantityInt = maxQty },
                    enabled = maxQty > 0
                ) {
                    Text("최대")
                }
            }

            // 수량 제약 안내 / 에러 메시지
            val constraintHint = when {
                quantityErrorMsg != null -> quantityErrorMsg
                tradeType == TradeType.BUY && pricePerShare > 0 ->
                    "최대 ${maxBuyQty}주 구매 가능 (잔고 ₩${"%,d".format(availableCash)})"
                tradeType == TradeType.SELL ->
                    "보유 수량: ${holdingQty}주"
                else -> null
            }
            if (constraintHint != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = constraintHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (quantityErrorMsg != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (totalAmount > 0) {
                Spacer(Modifier.height(4.dp))
                val displayAmount = if (tradeType == TradeType.BUY) totalWithFee else netAfterFee
                Text(
                    text = "기준가 ₩${"%,d".format(totalAmount)} · 수수료/세금 포함 ₩${"%,d".format(displayAmount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(16.dp))

            // ── 투자 메모 (매수/매도 별 가이드 텍스트 분기) ──────
            Text(memoGuide, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = memo,
                onValueChange = { memo = it },
                label = { Text("투자 메모 (최소 4자 이상)") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
                isError = memo.isNotEmpty() && !isMemoSufficient,
                supportingText = {
                    Text(
                        text = "${memo.trim().length}자 / 4자 이상 필요",
                        color = if (isMemoSufficient) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
            Spacer(Modifier.height(24.dp))

            // ── 역할별 하단 버튼 ──────────────────────────────────
            val typeLabel = if (tradeType == TradeType.BUY) "매수" else "매도"

            // 공통 에러 메시지 표시 (체결 실패 등)
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // 역할 무관 동일 워크플로: submitTradeRequest → LedgerScreen에서 날짜 입력 체결
            val buttonLabel = if (role == UserRole.PARENT)
                "$typeLabel 요청 등록 (장부에서 체결 날짜 입력)"
            else
                "부모님께 $typeLabel 승인 요청 보내기"

            Button(
                onClick = {
                    viewModel.submitTradeRequest(
                        stockName = stockName,
                        ticker    = stockTicker,
                        quantity  = quantityInt,
                        price     = pricePerShare,
                        memo      = memo,
                        type      = tradeType
                    )
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled  = isFormValid
            ) { Text(buttonLabel) }
            Spacer(Modifier.height(8.dp))
        }
    }

    // ── 배당 등록 다이얼로그 ───────────────────────────────────
    if (showDividendDialog) {
        DividendDialog(
            stockName = stockName,
            onConfirm = { amount, timestamp ->
                viewModel.addDividend(stockName, stockTicker, amount, timestamp)
                showDividendDialog = false
                onBack()
            },
            onDismiss = { showDividendDialog = false }
        )
    }
}

@Composable
private fun StockLineChart(
    data: List<ChartPoint>,
    displayOffset: Int = 0,          // data 앞부분 MA 버퍼 인덱스 — 화면엔 drop(displayOffset)만 표시
    lineColor: Color,
    tradeMarkers: List<TradeMarker> = emptyList(),
    stockName: String = "",
    modifier: Modifier = Modifier
) {
    // 표시용 데이터: 버퍼를 제외한 실제 기간
    val off         = displayOffset.coerceIn(0, (data.size - 2).coerceAtLeast(0))
    val displayData = if (off > 0) data.drop(off) else data
    if (displayData.size < 2) return

    val prices  = displayData.map { it.price }
    val volumes = displayData.map { it.volume }
    val minP    = prices.minOrNull() ?: return
    val maxP    = prices.maxOrNull() ?: return
    if (minP == maxP) return
    val maxVol  = volumes.maxOrNull() ?: 0L

    // MA는 버퍼 포함 전체 데이터로 계산 → displayOffset 이후 인덱스가 화면 좌측 끝부터 non-null
    val allPrices = remember(data) { data.map { it.price } }
    val ma5  = remember(data) {
        allPrices.mapIndexed { i, _ ->
            if (i < 4)  null else allPrices.subList(i - 4,  i + 1).average().toFloat()
        }
    }
    val ma20 = remember(data) {
        allPrices.mapIndexed { i, _ ->
            if (i < 19) null else allPrices.subList(i - 19, i + 1).average().toFloat()
        }
    }

    var selectedMarker by remember { mutableStateOf<TradeMarker?>(null) }
    var showVolumeInfo by remember { mutableStateOf(false) }

    val maxLabel        = "₩${"%,d".format(maxP.toLong())}"
    val minLabel        = "₩${"%,d".format(minP.toLong())}"
    val labelColor      = MaterialTheme.colorScheme.onSurfaceVariant
    val markerLabelArgb = MaterialTheme.colorScheme.onSurface.toArgb()
    val labelStyle      = MaterialTheme.typography.labelSmall
    val surfaceBg       = MaterialTheme.colorScheme.surface

    BoxWithConstraints(modifier = modifier) {
        val priceAreaEnd = maxHeight * 0.80f   // Dp: bottom edge of price region

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(tradeMarkers, displayData) {
                    detectTapGestures { tap ->
                        if (displayData.size < 2) return@detectTapGestures
                        val sw     = size.width.toFloat()
                        val sh     = size.height.toFloat()
                        val padV   = 4f
                        val totalH = sh - padV * 2
                        val priceH = totalH * 0.80f
                        val priceTop = padV
                        val stepX  = sw / (displayData.size - 1)
                        val rangeP = maxP - minP
                        fun xOf(i: Int) = i * stepX
                        fun yOfPrice(p: Float) = priceTop + priceH * (1f - (p - minP) / rangeP)
                        val hitR = 28.dp.toPx()
                        for (marker in tradeMarkers) {
                            val idx = displayData.indexOfFirst { it.date >= marker.date }
                                .let { if (it < 0) displayData.lastIndex else it }
                            val mx = xOf(idx); val my = yOfPrice(displayData[idx].price)
                            val dx = tap.x - mx; val dy = tap.y - my
                            if (dx * dx + dy * dy <= hitR * hitR) {
                                selectedMarker = marker
                                return@detectTapGestures
                            }
                        }
                    }
                }
        ) {
            val padV     = 4f
            val totalH   = size.height - padV * 2
            val priceH   = totalH * 0.80f
            val volH     = totalH * 0.20f
            val priceTop = padV
            val priceBot = padV + priceH
            val volBot   = padV + totalH
            val stepX    = size.width / (displayData.size - 1)
            val rangeP   = maxP - minP

            fun xOf(i: Int)        = i * stepX
            fun yOfPrice(p: Float) = priceTop + priceH * (1f - (p - minP) / rangeP)

            // ── Volume bars (bottom 20%) ─────────────────────────────────
            if (maxVol > 0L) {
                val barW = (stepX * 0.65f).coerceAtLeast(1.5f)
                displayData.forEachIndexed { i, pt ->
                    if (pt.volume > 0L) {
                        val barH = (pt.volume.toFloat() / maxVol) * volH * 0.88f
                        drawRect(
                            color   = Color(0xFF9E9E9E).copy(alpha = 0.55f),
                            topLeft = Offset(xOf(i) - barW / 2f, volBot - barH),
                            size    = Size(barW, barH)
                        )
                    }
                }
            }

            // ── Price area gradient fill ─────────────────────────────────
            val fillPath = Path().apply {
                moveTo(xOf(0), yOfPrice(prices[0]))
                for (i in 1 until prices.size) lineTo(xOf(i), yOfPrice(prices[i]))
                lineTo(xOf(prices.size - 1), priceBot)
                lineTo(xOf(0), priceBot)
                close()
            }
            drawPath(fillPath, brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.25f), lineColor.copy(alpha = 0f)),
                startY = priceTop, endY = priceBot))

            // ── MA20 line — 전체 데이터 기준 계산, off부터 화면 인덱스로 매핑 ──
            val ma20Path = Path(); var ma20Started = false
            for (i in off until ma20.size) {
                val v = ma20[i] ?: continue
                val j  = i - off          // displayData 내 인덱스
                val px = xOf(j); val py = yOfPrice(v)
                if (!ma20Started) { ma20Path.moveTo(px, py); ma20Started = true }
                else ma20Path.lineTo(px, py)
            }
            if (ma20Started) drawPath(ma20Path, color = Color(0xFF34D399),
                style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

            // ── MA5 line ────────────────────────────────────────────────
            val ma5Path = Path(); var ma5Started = false
            for (i in off until ma5.size) {
                val v = ma5[i] ?: continue
                val j  = i - off
                val px = xOf(j); val py = yOfPrice(v)
                if (!ma5Started) { ma5Path.moveTo(px, py); ma5Started = true }
                else ma5Path.lineTo(px, py)
            }
            if (ma5Started) drawPath(ma5Path, color = Color(0xFFFBBF24),
                style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

            // ── Price line (topmost) ─────────────────────────────────────
            val linePath = Path().apply {
                moveTo(xOf(0), yOfPrice(prices[0]))
                for (i in 1 until prices.size) lineTo(xOf(i), yOfPrice(prices[i]))
            }
            drawPath(linePath, color = lineColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

            // ── Trade markers (within price area only) ───────────────────
            if (tradeMarkers.isEmpty()) return@Canvas

            val iH  = 16.dp.toPx();  val hH  =  5.dp.toPx()
            val hW  =  2.dp.toPx();  val sW  =  0.8.dp.toPx()
            val lPd =  3.dp.toPx();  val psz =  8.dp.toPx()
            val msz =  7.5.dp.toPx(); val bH =  psz + msz + 5f
            val ePd =  4.dp.toPx()

            val n = tradeMarkers.size
            val mxArr = FloatArray(n); val myArr = FloatArray(n); val lyArr = FloatArray(n)
            tradeMarkers.forEachIndexed { i, m ->
                val idx = displayData.indexOfFirst { it.date >= m.date }
                    .let { if (it < 0) displayData.lastIndex else it }
                mxArr[i] = xOf(idx)
                myArr[i] = yOfPrice(displayData[idx].price)
                lyArr[i] = when (m.kind) {
                    MarkerKind.BUY_ONLY  -> myArr[i] - iH - lPd - bH / 2f
                    MarkerKind.SELL_ONLY -> myArr[i] + iH + lPd + bH / 2f
                    MarkerKind.MIXED     -> myArr[i] - iH * 0.55f - lPd - bH / 2f
                }
            }

            if (n > 5) {
                // 거래 수 많을 때: 수직 라인만 표시 (원 표시 없음)
                tradeMarkers.forEachIndexed { i, m ->
                    val lc = when (m.kind) {
                        MarkerKind.BUY_ONLY  -> StockUp
                        MarkerKind.SELL_ONLY -> StockDown
                        MarkerKind.MIXED     -> Color(0xFF9E9E9E)
                    }
                    drawLine(lc.copy(alpha = 0.5f),
                        start = Offset(mxArr[i], priceTop), end = Offset(mxArr[i], priceBot),
                        strokeWidth = 1.5.dp.toPx())
                }
            } else {
                // 라벨 겹침 방지: 다중 패스 + 라벨 폭 고려 x-근접도 판정
                val lyMin      = priceTop + bH / 2f + 2f
                val lyMax      = priceBot - bH / 2f - 2f
                val labelHalfW = 90f   // 가격 라벨 추정 반폭(px)
                val minYSep    = bH + 4f
                for (i in 0 until n) lyArr[i] = lyArr[i].coerceIn(lyMin, lyMax)
                repeat(8) {
                    val ordered = (0 until n).sortedBy { lyArr[it] }
                    for (k in 1 until ordered.size) {
                        val a  = ordered[k - 1]; val b = ordered[k]
                        val xA = mxArr[a].coerceIn(ePd + labelHalfW, size.width - ePd - labelHalfW)
                        val xB = mxArr[b].coerceIn(ePd + labelHalfW, size.width - ePd - labelHalfW)
                        if (Math.abs(xA - xB) < labelHalfW * 2f) {
                            val gap = lyArr[b] - lyArr[a]
                            if (gap < minYSep) {
                                val shift = (minYSep - gap) / 2f
                                lyArr[a] -= shift; lyArr[b] += shift
                            }
                        }
                    }
                    for (i in 0 until n) lyArr[i] = lyArr[i].coerceIn(lyMin, lyMax)
                }

                tradeMarkers.forEachIndexed { i, m ->
                    val mx = mxArr[i]; val my = myArr[i]; val ly = lyArr[i]
                    when (m.kind) {
                        MarkerKind.BUY_ONLY  -> drawUpArrow(mx, my, iH, hH, hW, sW, StockUp)
                        MarkerKind.SELL_ONLY -> drawDownArrow(mx, my, iH, hH, hW, sW, StockDown)
                        MarkerKind.MIXED     -> {
                            val mH = iH * 0.55f; val mHH = mH * 0.35f
                            drawUpArrow(mx, my, mH, mHH, hW, sW, StockUp)
                            drawDownArrow(mx, my, mH, mHH, hW, sW, StockDown)
                        }
                    }
                    drawIntoCanvas { canvas ->
                        val priceLabel = "₩${"%,d".format(m.avgPrice)}"
                        val memoLabel  = if (m.memo.length > 5) "${m.memo.take(5)}…" else m.memo
                        val prPaint = android.graphics.Paint().apply {
                            isAntiAlias = true; color = markerLabelArgb
                            textAlign = android.graphics.Paint.Align.CENTER
                            typeface = android.graphics.Typeface.DEFAULT_BOLD; textSize = psz
                        }
                        val mPaint = android.graphics.Paint().apply {
                            isAntiAlias = true; color = markerLabelArgb
                            textAlign = android.graphics.Paint.Align.CENTER
                            typeface = android.graphics.Typeface.DEFAULT; textSize = msz
                        }
                        val maxHW = maxOf(prPaint.measureText(priceLabel),
                                          mPaint.measureText(memoLabel)) / 2f
                        val safeX = mx.coerceIn(ePd + maxHW, size.width - ePd - maxHW)
                        canvas.nativeCanvas.apply {
                            drawText(priceLabel, safeX, ly - msz * 0.5f - 1f, prPaint)
                            drawText(memoLabel,  safeX, ly + psz * 0.5f + 1f, mPaint)
                        }
                    }
                }
            }
        }

        // ── Y-axis max label (top of price area) ────────────────────────
        Text(
            text     = maxLabel,
            modifier = Modifier
                .align(Alignment.TopStart)
                .background(surfaceBg.copy(alpha = 0.6f), MaterialTheme.shapes.extraSmall)
                .padding(horizontal = 3.dp, vertical = 1.dp),
            style = labelStyle, color = labelColor, maxLines = 1
        )
        // ── Y-axis min label (bottom of price area = 80% mark) ──────────
        Text(
            text     = minLabel,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(y = priceAreaEnd - 16.dp)
                .background(surfaceBg.copy(alpha = 0.6f), MaterialTheme.shapes.extraSmall)
                .padding(horizontal = 3.dp, vertical = 1.dp),
            style = labelStyle, color = labelColor, maxLines = 1
        )
        // ── Volume label — tappable educational popup ────────────────────
        Text(
            text     = "📊 거래량",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 4.dp, bottom = 4.dp)
                .background(surfaceBg.copy(alpha = 0.55f), MaterialTheme.shapes.extraSmall)
                .clickable { showVolumeInfo = true }
                .padding(horizontal = 3.dp, vertical = 1.dp),
            style = labelStyle, color = labelColor, maxLines = 1
        )
    }

    // ── Trade marker detail popup (모든 메모 표시) ─────────────────────
    selectedMarker?.let { marker ->
        val popupSdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA) }
        val dateStr  = popupSdf.format(Date(marker.completedAt))
        val memoText = when {
            marker.allMemos.isEmpty()    -> "(메모 없음)"
            marker.allMemos.size == 1    -> marker.allMemos[0]
            else -> marker.allMemos.mapIndexed { i, m -> "${i + 1}. $m" }.joinToString("\n")
        }
        AlertDialog(
            onDismissRequest = { selectedMarker = null },
            title = { Text("📋 $stockName 투자 메모 다시보기") },
            text = {
                Text(
                    text  = "체결 일시: $dateStr\n체결 평균가: ₩${"%,d".format(marker.avgPrice)}\n\n✍️ 자녀의 실전 기록:\n$memoText",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = { Button(onClick = { selectedMarker = null }) { Text("확인") } }
        )
    }

    // ── 거래량 교육용 팝업 ─────────────────────────────────────────────────
    if (showVolumeInfo) {
        AlertDialog(
            onDismissRequest = { showVolumeInfo = false },
            title = { Text("📊 거래량이란?") },
            text = {
                Text(
                    text = "거래량은 하루 동안 사고 팔린 주식의 총 수량이에요.\n\n" +
                           "📈 거래량이 많을수록\n" +
                           "• 그날 많은 사람이 주식을 사고팔았다는 뜻이에요.\n" +
                           "• 주가가 크게 움직이는 날에는 보통 거래량도 많아요.\n\n" +
                           "📉 거래량이 적을수록\n" +
                           "• 별로 관심받지 못한 조용한 날이에요.\n\n" +
                           "💡 투자 팁\n" +
                           "주가가 오르면서 거래량도 많아지면 강한 상승 신호일 수 있어요!",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = { showVolumeInfo = false }) { Text("알겠어요!") }
            }
        )
    }
}

// ── 1:4 커스텀 화살표 DrawScope 확장 ─────────────────────────────────────────

private fun DrawScope.drawUpArrow(
    cx: Float, anchorY: Float,
    iH: Float, hH: Float, hW: Float, sW: Float,
    color: Color
) {
    val tipY  = anchorY - iH
    val neckY = anchorY - iH + hH
    drawPath(Path().apply {
        moveTo(cx, tipY)
        lineTo(cx + hW, neckY);  lineTo(cx + sW, neckY)
        lineTo(cx + sW, anchorY); lineTo(cx - sW, anchorY)
        lineTo(cx - sW, neckY);  lineTo(cx - hW, neckY)
        close()
    }, color = color)
}

private fun DrawScope.drawDownArrow(
    cx: Float, anchorY: Float,
    iH: Float, hH: Float, hW: Float, sW: Float,
    color: Color
) {
    val tipY  = anchorY + iH
    val neckY = anchorY + iH - hH
    drawPath(Path().apply {
        moveTo(cx, tipY)
        lineTo(cx + hW, neckY);  lineTo(cx + sW, neckY)
        lineTo(cx + sW, anchorY); lineTo(cx - sW, anchorY)
        lineTo(cx - sW, neckY);  lineTo(cx - hW, neckY)
        close()
    }, color = color)
}

/**
 * KRX(한국거래소) 코스피 호가단위 — 최신 7단계 기준 (미만 조건)
 *
 * 2,000 미만       → 1원
 * 2,000~5,000     → 5원
 * 5,000~20,000    → 10원
 * 20,000~50,000   → 50원
 * 50,000~200,000  → 100원
 * 200,000~500,000 → 500원
 * 500,000 이상    → 1,000원
 */
private fun kospiPriceStep(price: Long): Long = when {
    price < 2_000L      -> 1L
    price < 5_000L      -> 5L
    price < 20_000L     -> 10L
    price < 50_000L     -> 50L
    price < 200_000L    -> 100L
    price < 500_000L    -> 500L
    else                -> 1_000L
}

@Composable
private fun DividendDialog(
    stockName: String,
    onConfirm: (amount: Long, timestamp: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val todayStr   = remember { SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date()) }
    var amountText by remember { mutableStateOf("") }
    var dateText   by remember { mutableStateOf(todayStr) }
    val amount = amountText.toLongOrNull() ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("배당 수입 등록") },
        text = {
            Column {
                Text(
                    "$stockName 배당금을 입력하세요. 세금 처리 후 실수령 총액을 입력합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                    label = { Text("배당 총액 (원)") },
                    singleLine = true,
                    suffix = { Text("원") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = if (amount > 0) ({
                        Text(
                            "₩${"%,d".format(amount)} 예수금에 추가됩니다.",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }) else null
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("수령 일시") },
                    placeholder = { Text("YYYY-MM-DD") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val timestamp = runCatching {
                        SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).parse(dateText)?.time
                    }.getOrNull() ?: System.currentTimeMillis()
                    onConfirm(amount, timestamp)
                },
                enabled = amount > 0 && dateText.length == 10
            ) { Text("배당 등록") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("취소") }
        }
    )
}
