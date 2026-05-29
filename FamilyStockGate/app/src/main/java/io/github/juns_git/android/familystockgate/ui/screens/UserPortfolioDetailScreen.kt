package io.github.juns_git.android.familystockgate.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.juns_git.android.familystockgate.data.model.HoldingItem
import io.github.juns_git.android.familystockgate.data.model.LeaderboardEntry
import io.github.juns_git.android.familystockgate.data.model.TradeRequest
import io.github.juns_git.android.familystockgate.data.model.TradeStatus
import io.github.juns_git.android.familystockgate.data.model.TradeType
import io.github.juns_git.android.familystockgate.ui.viewmodel.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// [Frame 8-1] 유저 자산 포트폴리오 상세 화면
@Composable
fun UserPortfolioDetailScreen(
    viewModel: AppViewModel,
    innerPadding: PaddingValues,
    targetUid: String,
    nickname: String,
    onBack: () -> Unit
) {
    LaunchedEffect(targetUid) { viewModel.fetchTargetUserPortfolio(targetUid) }

    val leaderboard    by viewModel.leaderboard.collectAsState()
    val memberBalances by viewModel.memberBalances.collectAsState()
    val memberHoldings by viewModel.memberHoldings.collectAsState()
    val allRequests    by viewModel.tradeRequests.collectAsState()

    val entry    = leaderboard.find { it.childUid == targetUid }
    val balance  = memberBalances[targetUid] ?: 0L
    val holdings = memberHoldings[targetUid] ?: emptyList()

    val completedStatuses = setOf(
        TradeStatus.FILLED, TradeStatus.PARTIAL_FILLED, TradeStatus.UNFILLED
    )
    val allTransactions = allRequests
        .filter { it.childUid == targetUid && it.status in completedStatuses }
        .sortedByDescending { req ->
            if (req.completedAt > 0L) req.completedAt else req.timestamp
        }

    // ── 보유 종목 선택 필터 ─────────────────────────────────────────────────
    var selectedTicker by remember { mutableStateOf<String?>(null) }

    val filteredTransactions = if (selectedTicker != null)
        allTransactions.filter { it.stockTicker == selectedTicker }
    else
        allTransactions

    // 거래 기록이 전혀 없는 보유 종목 → 초기 설정 카드로 합성 표시
    val holdingsWithNoTx = holdings
        .filter { holding ->
            allRequests.none { req ->
                req.childUid == targetUid && req.stockTicker == holding.stock.ticker
            }
        }
        .let { list ->
            if (selectedTicker != null) list.filter { it.stock.ticker == selectedTicker } else list
        }

    val selectedName    = holdings.find { it.stock.ticker == selectedTicker }?.stock?.name ?: selectedTicker
    val rightPanelCount = filteredTransactions.size + holdingsWithNoTx.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        // ── 상단 헤더 바 ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
            }
            Text(
                text = "${nickname}의 투자 현황",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        HorizontalDivider()

        // ── 대시보드 카드 ──────────────────────────────────────────────────────
        DashboardCard(entry = entry, balance = balance)

        // ── 좌우 2분할 리스트 ─────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // 좌측 50% — 보유 종목 현황 (탭 → 우측 거래 기록 필터)
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = "보유 종목 (${holdings.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (selectedTicker != null) "다시 탭하면 전체보기" else "탭하면 거래 기록 필터",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                }
                if (holdings.isEmpty()) {
                    item {
                        Text(
                            text = "보유 종목 없음",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(holdings, key = { it.stock.ticker }) { holding ->
                        HoldingCard(
                            holding    = holding,
                            isSelected = holding.stock.ticker == selectedTicker,
                            onClick    = {
                                selectedTicker =
                                    if (selectedTicker == holding.stock.ticker) null
                                    else holding.stock.ticker
                            }
                        )
                    }
                }
            }

            VerticalDivider()

            // 우측 50% — 거래 기록 타임라인 (필터 + 초기 설정 합성)
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = if (selectedTicker != null)
                            "${selectedName} (${rightPanelCount})"
                        else
                            "거래 기록 (${rightPanelCount})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                }
                if (filteredTransactions.isEmpty() && holdingsWithNoTx.isEmpty()) {
                    item {
                        Text(
                            text = if (selectedTicker != null) "해당 종목 거래 기록 없음"
                                   else "거래 기록 없음",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(filteredTransactions, key = { it.requestId }) { tx ->
                        TransactionCard(tx)
                    }
                    // 거래 기록 없이 보유 중인 종목 → 초기 설정 카드
                    items(holdingsWithNoTx, key = { "init_${it.stock.ticker}" }) { holding ->
                        InitialSetupCard(holding)
                    }
                }
            }
        }
    }
}

// ── 현재 자산 대시보드 카드 ───────────────────────────────────────────────────

@Composable
private fun DashboardCard(entry: LeaderboardEntry?, balance: Long) {
    val profitRate = entry?.profitRate ?: 0.0
    val isPositive = profitRate >= 0.0
    val rateColor = if (isPositive) MaterialTheme.colorScheme.error
                   else MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "현재 총 자산",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "₩${"%,d".format(entry?.totalAsset ?: balance)}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                val arrow = if (isPositive) "▲" else "▼"
                Text(
                    text = "$arrow ${"%.2f".format(profitRate)}%",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = rateColor
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "예수금 잔액",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "₩${"%,d".format(balance)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                val initialBudget = entry?.initialBudget ?: 0L
                if (initialBudget > 0L) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "기초 자산",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "₩${"%,d".format(initialBudget)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ── 보유 종목 카드 (탭으로 거래 기록 필터) ────────────────────────────────────

@Composable
private fun HoldingCard(holding: HoldingItem, isSelected: Boolean, onClick: () -> Unit) {
    val isProfit = holding.profitRate >= 0
    val rateColor = if (isProfit) MaterialTheme.colorScheme.error
                   else MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = if (isSelected) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) else CardDefaults.cardColors()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = holding.stock.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = holding.stock.ticker,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${holding.quantity}주",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${if (isProfit) "▲" else "▼"} ${"%.2f".format(holding.profitRate)}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = rateColor
                )
            }
            Text(
                text = "평단 ₩${"%,d".format(holding.avgPrice)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "현재 ₩${"%,d".format(holding.stock.currentPrice)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "평가 손익 ₩${"%,d".format(holding.profitLoss)}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = rateColor
            )
        }
    }
}

// ── 초기 설정 카드 (거래 기록 없이 보유 중인 종목) ──────────────────────────────

@Composable
private fun InitialSetupCard(holding: HoldingItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "초기 설정",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = holding.stock.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = holding.stock.ticker,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${holding.quantity}주 @ ₩${"%,d".format(holding.avgPrice)}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ── 거래 기록 카드 ────────────────────────────────────────────────────────────

@Composable
private fun TransactionCard(request: TradeRequest) {
    val dtFmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA) }
    val dateStr = remember(request.completedAt, request.timestamp) {
        val ts = if (request.completedAt > 0L) request.completedAt else request.timestamp
        dtFmt.format(Date(ts))
    }

    val typeLabel = when (request.type) {
        TradeType.BUY        -> "매수"
        TradeType.SELL       -> "매도"
        TradeType.INTEREST   -> "이자"
        TradeType.DIVIDEND   -> "배당"
        TradeType.WITHDRAWAL -> "출금"
    }
    val typeColor = when (request.type) {
        TradeType.BUY  -> MaterialTheme.colorScheme.error
        TradeType.SELL -> MaterialTheme.colorScheme.primary
        else           -> MaterialTheme.colorScheme.secondary
    }
    val statusLabel = when (request.status) {
        TradeStatus.FILLED         -> "체결"
        TradeStatus.PARTIAL_FILLED -> "부분체결"
        TradeStatus.UNFILLED       -> "미체결"
        else                       -> ""
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = dateStr,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = typeColor
                )
                if (statusLabel.isNotEmpty()) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (request.type == TradeType.BUY || request.type == TradeType.SELL) {
                Text(
                    text = request.stockName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            when (request.type) {
                TradeType.INTEREST, TradeType.DIVIDEND, TradeType.WITHDRAWAL -> {
                    Text(
                        text = "₩${"%,d".format(request.filledPrice)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                else -> {
                    if (request.filledQuantity > 0) {
                        Text(
                            text = "${request.filledQuantity}주 @ ₩${"%,d".format(request.filledPrice)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (request.memo.isNotBlank() &&
                request.type != TradeType.INTEREST &&
                request.type != TradeType.DIVIDEND &&
                request.type != TradeType.WITHDRAWAL
            ) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
                Text(
                    text = request.memo,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
