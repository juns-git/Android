package io.github.juns_git.familystockgate.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.juns_git.familystockgate.data.model.HoldingItem
import io.github.juns_git.familystockgate.data.model.StockItem
import io.github.juns_git.familystockgate.data.model.UserRole
import io.github.juns_git.familystockgate.ui.viewmodel.AppViewModel

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

    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        // ── 예수금 섹션 ───────────────────────────────────────
        CashSection(
            role = role,
            availableCash = availableCash,
            onToggleRole = { viewModel.toggleDebugRole() }
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
}

// ── 예수금 카드 (부모/자녀 공통 모니터링, 수정 버튼 없음) ──────

@Composable
private fun CashSection(role: UserRole, availableCash: Long, onToggleRole: () -> Unit) {
    val roleLabel = if (role == UserRole.PARENT) "부모" else "자녀"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "${roleLabel} 모드 · 투자 가능 예수금",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "₩ ${"%,d".format(availableCash)}",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            // [DEBUG] 역할 전환 스위치
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "[ DEBUG ] 역할 전환",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "자녀",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                    Switch(
                        checked = role == UserRole.PARENT,
                        onCheckedChange = { onToggleRole() }
                    )
                    Text(
                        "부모",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

// ── 보유 종목 탭 ──────────────────────────────────────────────

@Composable
private fun HoldingsTab(
    holdings: List<HoldingItem>,
    onItemClick: (HoldingItem) -> Unit
) {
    if (holdings.isEmpty()) {
        EmptyState(message = "보유 중인 종목이 없습니다.\n부모님의 승인 후 보유 종목이 등록됩니다.")
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
                val rateColor = if (holding.profitRate >= 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
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
                    color = if (stock.changeRate >= 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
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
