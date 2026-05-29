package io.github.juns_git.android.familystockgate.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import io.github.juns_git.android.familystockgate.data.model.TradeType
import io.github.juns_git.android.familystockgate.data.model.UserRole
import io.github.juns_git.android.familystockgate.ui.viewmodel.AppViewModel

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
    val role by viewModel.debugRole.collectAsState()
    val holdings by viewModel.holdings.collectAsState()
    val watchlist by viewModel.watchlist.collectAsState()
    val availableCash by viewModel.availableCash.collectAsState()
    val commissionRate by viewModel.commissionRate.collectAsState()
    val taxRate by viewModel.taxRate.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

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

    // 투자 메모 — 최소 10자 (요건 완화)
    val isMemoSufficient = memo.trim().length >= 10
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
            // ── 종목 정보 + 차트 영역 ────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stockName, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (currentPrice > 0) "현재가: ₩${"%,d".format(currentPrice)}"
                                   else "현재가: -",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (isFromHoldings && holdingItem != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "보유 ${holdingQty}주 · 평균 ₩${"%,d".format(holdingItem.avgPrice)} · " +
                                        "${if (holdingItem.profitRate >= 0) "+" else ""}${"%.2f".format(holdingItem.profitRate)}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (holdingItem.profitRate >= 0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "[가격 라인 차트 영역]",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                label = { Text("투자 메모 (최소 10자 이상)") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
                isError = memo.isNotEmpty() && !isMemoSufficient,
                supportingText = {
                    Text(
                        text = "${memo.trim().length}자 / 10자 이상 필요",
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
