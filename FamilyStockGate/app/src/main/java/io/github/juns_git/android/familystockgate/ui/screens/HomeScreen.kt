package io.github.juns_git.android.familystockgate.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.juns_git.android.familystockgate.data.model.HoldingItem
import io.github.juns_git.android.familystockgate.data.model.StockItem
import io.github.juns_git.android.familystockgate.data.model.UserRole
import io.github.juns_git.android.familystockgate.ui.theme.AppTheme
import io.github.juns_git.android.familystockgate.ui.theme.LocalAppTheme
import io.github.juns_git.android.familystockgate.ui.theme.StockDown
import io.github.juns_git.android.familystockgate.ui.theme.StockUp
import io.github.juns_git.android.familystockgate.ui.viewmodel.AppViewModel

// [Frame 2] Home Dashboard — 보유 종목 / 관심 종목 탭 분기
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    innerPadding: PaddingValues,
    onNavigateToTrade: (ticker: String, stockName: String, source: String) -> Unit
) {
    val role by viewModel.debugRole.collectAsState()
    val watchlist by viewModel.watchlist.collectAsState()
    val holdings by viewModel.holdings.collectAsState()
    val availableCash by viewModel.availableCash.collectAsState()
    val leaderboard by viewModel.leaderboard.collectAsState()
    val isPriceRefreshing by viewModel.isPriceRefreshing.collectAsState()
    val isManualRefreshing by viewModel.isManualRefreshing.collectAsState()

    val myEntry = leaderboard.find { it.isCurrentUser }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showWithdrawalDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    DisposableEffect(Unit) {
        viewModel.startPriceAutoRefresh()
        onDispose { viewModel.stopPriceAutoRefresh() }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshMessage.collect { message ->
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }

    // 탭 전환 시 해당 탭 종목 가격 즉시 갱신
    LaunchedEffect(selectedTab) {
        viewModel.refreshActiveStockPrices()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── 자산 요약 카드 ────────────────────────────────────
            CashSection(
                role = role,
                availableCash = availableCash,
                initialBudget = myEntry?.initialBudget ?: 0L,
                totalAsset    = myEntry?.totalAsset    ?: availableCash,
                profitRate    = myEntry?.profitRate    ?: 0.0,
                isPriceRefreshing = isPriceRefreshing,
                onRefresh     = { viewModel.refreshActiveStockPrices(isManual = true) },
                onWithdrawalClick = { showWithdrawalDialog = true }
            )

            // ── 보유 / 관심 탭 ────────────────────────────────────
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("보유 종목")
                            if (holdings.isNotEmpty()) {
                                Spacer(Modifier.width(4.dp))
                                Badge { Text("${holdings.size}") }
                            }
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("관심 종목")
                            if (watchlist.isNotEmpty()) {
                                Spacer(Modifier.width(4.dp))
                                Badge { Text("${watchlist.size}") }
                            }
                        }
                    }
                )
            }

            when (selectedTab) {
                0 -> HoldingsTab(
                    holdings = holdings,
                    role = role,
                    onItemClick = { holding ->
                        onNavigateToTrade(holding.stock.ticker, holding.stock.name, "holdings")
                    }
                )
                1 -> WatchlistTab(
                    watchlist = watchlist,
                    onItemClick = { stock ->
                        onNavigateToTrade(stock.ticker, stock.name, "watchlist")
                    },
                    onRemove = { stock ->
                        viewModel.removeFromWatchlist(stock.ticker)
                    }
                )
            }
        }

        if (isManualRefreshing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("업데이트 중", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = innerPadding.calculateBottomPadding())
        )
    }

    if (showWithdrawalDialog) {
        WithdrawalDialog(
            onConfirm = { amount, description, timestamp ->
                viewModel.requestWithdrawal(amount, description, timestamp)
                showWithdrawalDialog = false
            },
            onDismiss = { showWithdrawalDialog = false }
        )
    }
}

// ── 자산 요약 카드 ────────────────────────────────────────────

@Composable
private fun CashSection(
    role: UserRole,
    availableCash: Long,
    initialBudget: Long,
    totalAsset: Long,
    profitRate: Double,
    isPriceRefreshing: Boolean,
    onRefresh: () -> Unit,
    onWithdrawalClick: () -> Unit
) {
    val theme     = LocalAppTheme.current
    val roleLabel = if (role == UserRole.PARENT) "부모" else "자녀"
    val hasBase   = initialBudget > 0
    val isProfit  = profitRate >= 0
    val rateColor = if (isProfit) StockUp else StockDown
    val cardColor = when (theme) {
        AppTheme.BEAR_BLUE, AppTheme.BUNNY_PINK -> MaterialTheme.colorScheme.primaryContainer
        else                                     -> MaterialTheme.colorScheme.surface
    }
    val onCard = when (theme) {
        AppTheme.BEAR_BLUE, AppTheme.BUNNY_PINK -> MaterialTheme.colorScheme.onPrimaryContainer
        else                                     -> MaterialTheme.colorScheme.onSurface
    }
    val subColor  = onCard.copy(alpha = 0.55f)

    val refreshInteractionSource = remember { MutableInteractionSource() }
    val isRefreshPressed by refreshInteractionSource.collectIsPressedAsState()
    val refreshIconScale by animateFloatAsState(
        targetValue = if (isRefreshPressed) 0.65f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "refresh-press-scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {

            // ── 헤더 ────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$roleLabel 모드 · 투자 현황",
                    style = MaterialTheme.typography.labelMedium,
                    color = subColor
                )
                IconButton(
                    onClick  = onRefresh,
                    modifier = Modifier.size(36.dp),
                    interactionSource = refreshInteractionSource
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "가격 새로고침",
                        tint = subColor,
                        modifier = Modifier
                            .size(20.dp)
                            .scale(refreshIconScale)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            // ── 예수금 행 ────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "예수금",
                        style = MaterialTheme.typography.titleSmall,
                        color = subColor
                    )
                    OutlinedButton(
                        onClick = onWithdrawalClick,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("출금요청", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text(
                    text = "₩ ${"%,d".format(availableCash)}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = onCard
                )
            }

            // ── 하단 3열 통계 (베이스금액 | 평가총액 | 수익율) ──
            if (hasBase) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = onCard.copy(alpha = 0.15f))
                Spacer(Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    // 베이스금액 (좌측 정렬)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text("베이스금액", style = MaterialTheme.typography.labelSmall, color = subColor)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "₩${"%,d".format(initialBudget)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = onCard
                        )
                    }
                    // 평가 총액 (중앙 정렬)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("평가 총액", style = MaterialTheme.typography.labelSmall, color = subColor)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "₩${"%,d".format(totalAsset)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = onCard
                        )
                    }
                    // 수익율 (우측 정렬)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text("수익율", style = MaterialTheme.typography.labelSmall, color = subColor)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${if (isProfit) "+" else ""}${"%.2f".format(profitRate)}%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = rateColor
                        )
                    }
                }
            }
        }
    }
}

// ── 보유 종목 탭 ──────────────────────────────────────────────

@Composable
private fun HoldingsTab(
    holdings: List<HoldingItem>,
    role: UserRole,
    onItemClick: (HoldingItem) -> Unit
) {
    if (holdings.isEmpty()) {
        val emptyMsg = if (role == UserRole.PARENT) {
            "보유 중인 종목이 없습니다.\n설정에서 초기 보유 종목을 세팅할 수 있습니다."
        } else {
            "보유 중인 종목이 없습니다.\n부모님의 승인 후 보유 종목이 등록됩니다."
        }
        EmptyState(message = emptyMsg)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(holdings, key = { it.stock.ticker }) { holding ->
            HoldingCard(holding = holding, onClick = { onItemClick(holding) })
        }
    }
}

@Composable
private fun HoldingCard(holding: HoldingItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(holding.stock.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(holding.stock.ticker, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${holding.quantity}주 · 평균 ₩${"%,d".format(holding.avgPrice)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "₩${"%,d".format(holding.stock.currentPrice)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                val rateText = "%.2f%%".format(holding.profitRate)
                val rateColor = if (holding.profitRate >= 0) StockUp else StockDown
                Text(
                    text = if (holding.profitRate >= 0) "+${rateText}" else rateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = rateColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "총 ₩${"%,d".format(holding.totalValue)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── 관심 종목 탭 ──────────────────────────────────────────────

@Composable
private fun WatchlistTab(
    watchlist: List<StockItem>,
    onItemClick: (StockItem) -> Unit,
    onRemove: (StockItem) -> Unit
) {
    if (watchlist.isEmpty()) {
        EmptyState(message = "관심 종목이 없습니다.\n하단 [검색] 탭에서 종목을 찾아 등록해 보세요.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(watchlist, key = { it.ticker }) { stock ->
            WatchlistCard(
                stock = stock,
                onClick = { onItemClick(stock) },
                onRemove = { onRemove(stock) }
            )
        }
    }
}

@Composable
private fun WatchlistCard(stock: StockItem, onClick: () -> Unit, onRemove: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stock.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(stock.ticker, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 8.dp)) {
                Text(
                    text = "₩${"%,d".format(stock.currentPrice)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                val rateText = "%.2f%%".format(stock.changeRate)
                Text(
                    text = if (stock.changeRate >= 0) "+${rateText}" else rateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (stock.changeRate >= 0) StockUp else StockDown
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "관심 종목 삭제",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── 공통 Empty State ──────────────────────────────────────────

@Composable
private fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── 출금 요청 다이얼로그 ──────────────────────────────────────

@Composable
private fun WithdrawalDialog(
    onConfirm: (amount: Long, description: String, timestamp: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val todayStr   = remember { SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date()) }
    var amountText by remember { mutableStateOf("") }
    var descText   by remember { mutableStateOf("") }
    var dateText   by remember { mutableStateOf(todayStr) }
    val amount = amountText.toLongOrNull() ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("출금 요청") },
        text = {
            Column {
                Text(
                    "출금할 금액을 입력하세요. 예수금에서 즉시 차감됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                    label = { Text("출금 금액 (원)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = descText,
                    onValueChange = { descText = it },
                    label = { Text("설명 (선택)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("출금 일시") },
                    placeholder = { Text("YYYY-MM-DD") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val ts = runCatching {
                        SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).parse(dateText)?.time
                    }.getOrNull() ?: System.currentTimeMillis()
                    onConfirm(amount, descText, ts)
                },
                enabled = amount > 0 && dateText.length == 10
            ) { Text("출금 요청") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("취소") }
        }
    )
}
