package io.github.juns_git.familystockgate.ui.screens

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.juns_git.familystockgate.data.model.TradeType
import io.github.juns_git.familystockgate.data.model.UserRole
import io.github.juns_git.familystockgate.ui.viewmodel.AppViewModel

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

    val holdingItem = holdings.find { it.stock.ticker == stockTicker }
    val currentPrice = holdingItem?.stock?.currentPrice
        ?: watchlist.find { it.ticker == stockTicker }?.currentPrice
        ?: 0L

    val isFromHoldings = source == "holdings"
    val holdingQty = holdingItem?.quantity ?: 0

    // 매수/매도 선택 — 관심 종목 진입 시 매도 비활성
    var tradeType by remember { mutableStateOf(TradeType.BUY) }

    var quantityInt by remember { mutableIntStateOf(0) }
    var price by remember { mutableStateOf(currentPrice.toString()) }
    var memo by remember { mutableStateOf("") }

    val pricePerShare = price.toLongOrNull() ?: 0L
    val totalAmount = quantityInt.toLong() * pricePerShare

    // 수량 상한 계산
    val maxBuyQty = if (pricePerShare > 0) (availableCash / pricePerShare).toInt() else 0
    val maxQty = if (tradeType == TradeType.BUY) maxBuyQty else holdingQty

    // 수량 유효성
    val quantityErrorMsg: String? = when {
        quantityInt <= 0 -> null  // 아직 미입력 — 에러 표시 안 함
        tradeType == TradeType.BUY && pricePerShare > 0 && totalAmount > availableCash ->
            "예수금 초과 (최대 ${maxBuyQty}주 / 잔고 ₩${"%,d".format(availableCash)})"
        tradeType == TradeType.SELL && quantityInt > holdingQty ->
            "보유 수량 초과 (최대 ${holdingQty}주)"
        else -> null
    }

    // 투자 메모 — 최소 10자 (요건 완화)
    val isMemoSufficient = memo.trim().length >= 10
    val isFormValid = quantityInt > 0 && pricePerShare > 0 && isMemoSufficient && quantityErrorMsg == null

    // 매수/매도 별 메모 가이드 텍스트
    val memoGuide = if (tradeType == TradeType.BUY)
        "이 주식을 지금 사야 하는 이유를 부모님께 설명해 주세요."
    else
        "이 주식을 지금 팔아서 이익/손실을 확정하려는 이유를 부모님께 설명해 주세요."

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

            // ── 매수 / 매도 선택 칩 ──────────────────────────────
            // 관심 종목 진입 시 매도 칩 비활성화 (원천 차단)
            Row {
                TradeType.entries.forEach { type ->
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // ── 가격 입력 ─────────────────────────────────────────
            OutlinedTextField(
                value = price,
                onValueChange = { price = it.filter { c -> c.isDigit() } },
                label = { Text("가격 (원)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                suffix = { Text("원") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
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
                Text(
                    text = "총 거래 금액: ₩${"%,d".format(totalAmount)}",
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

            if (role == UserRole.CHILD) {
                Button(
                    onClick = {
                        viewModel.submitTradeRequest(
                            stockName = stockName,
                            ticker = stockTicker,
                            quantity = quantityInt,
                            price = pricePerShare,
                            memo = memo,
                            type = tradeType
                        )
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = isFormValid
                ) {
                    Text("부모님께 $typeLabel 승인 요청 보내기")
                }
            } else {
                Button(
                    onClick = {
                        // TODO: viewModel.executeTradeDirectly(stockName, stockTicker, quantityInt, pricePerShare, tradeType)
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = quantityInt > 0 && pricePerShare > 0
                ) {
                    Text("즉시 체결 및 거래 등록")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
