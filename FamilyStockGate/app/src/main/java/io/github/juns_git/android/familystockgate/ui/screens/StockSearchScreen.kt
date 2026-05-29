package io.github.juns_git.android.familystockgate.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.juns_git.android.familystockgate.data.model.StockItem
import io.github.juns_git.android.familystockgate.ui.viewmodel.AppViewModel

// [검색 화면] 종목 검색 → 관심 종목 등록 전용
// 이 화면에서는 종목 클릭 시 상세/거래 화면으로 이동하지 않음.
// 반드시 [관심 등록] → 홈의 [관심 종목] 탭 → 종목 클릭 순서로 거래 진입.
@Composable
fun StockSearchScreen(
    viewModel: AppViewModel,
    innerPadding: PaddingValues
) {
    val searchResults  by viewModel.searchResults.collectAsState()
    val isLoading      by viewModel.isLoading.collectAsState()
    val errorMessage   by viewModel.errorMessage.collectAsState()
    val watchlist      by viewModel.watchlist.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val watchlistTickers = watchlist.map { it.ticker }.toSet()

    var query by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        viewModel.startPriceAutoRefresh()
        onDispose {
            viewModel.stopPriceAutoRefresh()
            viewModel.clearSearchResults()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        Text("[검색] 종목 검색 → 관심 등록", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "관심 종목으로 등록한 뒤, 홈 화면에서 클릭하면 거래할 수 있습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        // ── 검색 입력창 ──────────────────────────────────────
        OutlinedTextField(
            value = query,
            onValueChange = { newQuery ->
                query = newQuery
                viewModel.searchStockFromServer(newQuery)
            },
            label = { Text("종목명 또는 종목 코드 검색") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        query = ""
                        viewModel.clearSearchResults()
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "검색어 지우기")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        // ── 최근 검색 칩 (쿼리 비어있을 때만 노출) ──────────────
        if (query.isBlank() && recentSearches.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "최근 검색",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recentSearches, key = { it.ticker }) { stock ->
                    RecentChip(
                        stock = stock,
                        onClick = {
                            query = stock.name
                            viewModel.searchStockFromServer(stock.name)
                        },
                        onRemove = { viewModel.removeRecentSearch(stock.ticker) }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── 에러 배너 ────────────────────────────────────────
        if (errorMessage != null) {
            Text(
                text = "오류: $errorMessage",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            )
        }

        // ── 검색 결과 / 로딩 / 안내 ─────────────────────────────
        when {
            query.isBlank() -> {
                SearchHint()
            }
            // 첫 검색 중 (아직 결과 없음) → 빈 카드 없이 원형 스피너만 표시
            isLoading && searchResults.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            searchResults.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "\"$query\" 검색 결과가 없습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> {
                // 결과가 있을 때 추가 로딩은 상단 선형 인디케이터로 표시 (LazyColumn 유지)
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(searchResults, key = { it.ticker }) { stock ->
                            SearchResultCard(
                                stock = stock,
                                isInWatchlist = stock.ticker in watchlistTickers,
                                onToggleWatchlist = { inWatchlist ->
                                    if (inWatchlist) {
                                        viewModel.removeFromWatchlist(stock.ticker)
                                    } else {
                                        viewModel.addToWatchlist(stock)
                                    }
                                }
                            )
                        }
                    }
                    if (isLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                        )
                    }
                }
            }
        }
    }
}

// ── 검색 결과 카드 ────────────────────────────────────────────

@Composable
private fun SearchResultCard(
    stock: StockItem,
    isInWatchlist: Boolean,
    onToggleWatchlist: (currentlyInWatchlist: Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 종목 정보
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stock.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stock.ticker,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 현재가 + 등락률
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(
                    text = "₩${"%,d".format(stock.currentPrice)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                val rateText = "%.2f%%".format(stock.changeRate)
                Text(
                    text = if (stock.changeRate >= 0) "+$rateText" else rateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (stock.changeRate >= 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                )
            }

            // [☆ / ★] 관심 등록 버튼
            IconButton(
                onClick = { onToggleWatchlist(isInWatchlist) }
            ) {
                Icon(
                    imageVector = if (isInWatchlist) Icons.Default.Star else Icons.Default.StarOutline,
                    contentDescription = if (isInWatchlist) "관심 해제" else "관심 등록",
                    tint = if (isInWatchlist) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── 최근 검색 칩 ──────────────────────────────────────────────

@Composable
private fun RecentChip(
    stock: StockItem,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 1.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stock.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 80.dp)
                    .clickable(onClick = onClick)
                    .padding(start = 10.dp, top = 6.dp, bottom = 6.dp, end = 4.dp)
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(20.dp)
                    .clickable(onClick = onRemove)
            ) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = "삭제",
                    modifier = Modifier.size(11.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// ── 검색 전 안내 화면 ─────────────────────────────────────────

@Composable
private fun SearchHint() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.padding(bottom = 12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "종목명 또는 코드를 입력하세요\n예) 삼성전자, 005930",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
