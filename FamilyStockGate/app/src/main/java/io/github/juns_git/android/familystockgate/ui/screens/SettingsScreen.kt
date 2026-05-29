package io.github.juns_git.android.familystockgate.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.juns_git.android.familystockgate.data.model.HoldingItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import io.github.juns_git.android.familystockgate.data.model.StockItem
import io.github.juns_git.android.familystockgate.data.model.UserRole
import io.github.juns_git.android.familystockgate.ui.viewmodel.AppViewModel
import io.github.juns_git.android.familystockgate.ui.viewmodel.FamilyStockViewModel
import io.github.juns_git.android.familystockgate.utils.FirebaseConfigManager
import io.github.juns_git.android.familystockgate.utils.FirebaseCustomConfig

// [Frame 9] Settings — 대리 거래 비용 설정 + 자녀 자산 관리 제어판 (부모 전용)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(appViewModel: AppViewModel, familyViewModel: FamilyStockViewModel, innerPadding: PaddingValues) {
    val role               by appViewModel.debugRole.collectAsState()
    val commissionRate     by appViewModel.commissionRate.collectAsState()
    val taxRate            by appViewModel.taxRate.collectAsState()
    val fcmEnabled         by appViewModel.fcmEnabled.collectAsState()
    val familyMembers      by appViewModel.familyMembers.collectAsState()
    val memberBalances     by appViewModel.memberBalances.collectAsState()
    val memberHoldings     by appViewModel.memberHoldings.collectAsState()
    val stockSearchResults by appViewModel.searchResults.collectAsState()
    val isMasterRefreshing by appViewModel.isMasterRefreshing.collectAsState()
    val recentSearches     by appViewModel.recentSearches.collectAsState()

    var commissionText by remember(commissionRate) { mutableStateOf(commissionRate.toString()) }
    var taxText by remember(taxRate) { mutableStateOf(taxRate.toString()) }
    var showManagementSheet by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "설정",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        if (role == UserRole.PARENT) {

            // ── 🔒 독립 파이어베이스 서버 연동 ─────────────────
            FirebaseServerSection(viewModel = familyViewModel)

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(24.dp))

            // ── 📦 주식 종목 마스터 데이터 최신화 (부모 전용) ────
            Text(
                text = "📦 주식 종목 마스터 데이터",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Text(
                text = "부모가 최신화하면 자녀 기기에서 백그라운드 자동 동기화됩니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { appViewModel.refreshStockMaster() },
                enabled = !isMasterRefreshing,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                if (isMasterRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 8.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Text(if (isMasterRefreshing) "다운로드 중..." else "🔄 주식 종목 마스터 데이터 최신화")
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(24.dp))

            // ── 💸 대리 거래 비용 설정 ─────────────────────────
            Text(
                text = "💸 대리 거래 비용 설정",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    DecimalTextField(
                        label = "체결 수수료 (%)",
                        value = commissionText,
                        onValueChange = { commissionText = it }
                    )
                    Spacer(Modifier.height(8.dp))
                    DecimalTextField(
                        label = "거래 세금 (%)",
                        value = taxText,
                        onValueChange = { taxText = it }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "매수 차감: 주가 × 수량 × (1 + 수수료% + 세금%)\n매도 차감: 매도 대금 × (수수료% + 세금%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            appViewModel.updateCommissionRate(commissionText.toDoubleOrNull() ?: 0.0)
                            appViewModel.updateTaxRate(taxText.toDoubleOrNull() ?: 0.0)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("저장") }
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(24.dp))

            // ── ⚙️ 자녀 실전 자산 관리 제어판 ────────────────────
            Text(
                text = "⚙️ 자녀 실전 자산 관리",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Text(
                text = "자녀의 초기 예수금과 보유 주식을 직접 세팅합니다.\n이후 주식 변경은 거래 요청 체결을 통해 자동 반영됩니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { showManagementSheet = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text("자녀 실전 자산 관리 제어판 열기")
            }

        } else {
            // ── 🔔 자녀 모드: 알림 설정 ────────────────────────
            Text(
                text = "🔔 알림 설정",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "푸시 알림 수신",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "거래 체결·미체결 알림을 받습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = fcmEnabled,
                        onCheckedChange = { appViewModel.updateFcmEnabled(it) }
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = { showResetDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp, MaterialTheme.colorScheme.error
            )
        ) {
            Text("Firebase 초기화 (전체 삭제)")
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { appViewModel.signOut() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("로그아웃")
        }

        Spacer(Modifier.height(32.dp))
    }

    // ── Firebase 초기화 확인 다이얼로그 ──────────────────────────
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text(
                    "⚠️ Firebase 전체 초기화",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    "이 작업은 되돌릴 수 없습니다.\n\n" +
                    "• 내 계정 및 가족 데이터 전체 삭제\n" +
                    "• 자녀 자산·거래 기록 모두 삭제\n" +
                    "• 초대코드 삭제\n" +
                    "• 커스텀 Firebase 설정 초기화\n\n" +
                    "정말 모든 데이터를 삭제하시겠습니까?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDialog = false
                        appViewModel.resetAllData()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("전체 삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    // ── 자녀 자산 관리 BottomSheet ─────────────────────────────
    if (showManagementSheet) {
        ModalBottomSheet(
            onDismissRequest = { showManagementSheet = false },
            sheetState = sheetState
        ) {
            ChildAssetManagementContent(
                familyMembers = familyMembers,
                memberBalances = memberBalances,
                memberHoldings = memberHoldings,
                onUpdateCash = { uid, amount, date -> appViewModel.updateChildCash(uid, amount, date) },
                onAddHolding = { uid, holding, acquiredAt -> appViewModel.addInitialHolding(uid, holding, acquiredAt) },
                onRemoveHolding = { uid, ticker -> appViewModel.removeInitialHolding(uid, ticker) },
                stockSearchResults = stockSearchResults,
                onStockSearch = { appViewModel.searchStockFromServer(it) },
                onClearStockSearch = { appViewModel.clearSearchResults() },
                onAddInterest = { uid, amount, desc, date -> appViewModel.addInterest(uid, amount, desc, date) },
                recentSearches = recentSearches,
                onAddRecentSearch = { appViewModel.addRecentSearch(it) },
                onRemoveRecentSearch = { appViewModel.removeRecentSearch(it) }
            )
        }
    }
}

// ── 자녀 자산 관리 시트 내용 ──────────────────────────────────

@Composable
private fun ChildAssetManagementContent(
    familyMembers: List<Pair<String, String>>,
    memberBalances: Map<String, Long>,
    memberHoldings: Map<String, List<HoldingItem>>,
    onUpdateCash: (uid: String, amount: Long, date: Long) -> Unit,
    onAddHolding: (uid: String, holding: HoldingItem, acquiredAt: Long) -> Unit,
    onRemoveHolding: (uid: String, ticker: String) -> Unit,
    stockSearchResults: List<StockItem>,
    onStockSearch: (String) -> Unit,
    onClearStockSearch: () -> Unit,
    onAddInterest: (uid: String, amount: Long, description: String, date: Long) -> Unit,
    recentSearches: List<StockItem>,
    onAddRecentSearch: (StockItem) -> Unit,
    onRemoveRecentSearch: (String) -> Unit
) {
    var selectedMemberIndex by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 40.dp)
    ) {
        Text(
            "실전 자산 관리 제어판",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))

        if (familyMembers.isEmpty()) {
            Text(
                "가족 구성원 정보를 불러오는 중입니다...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        // 구성원 선택 탭
        val safeIndex = selectedMemberIndex.coerceAtMost(familyMembers.lastIndex)
        TabRow(selectedTabIndex = safeIndex) {
            familyMembers.forEachIndexed { index, (_, nickname) ->
                Tab(
                    selected = safeIndex == index,
                    onClick = { selectedMemberIndex = index },
                    text = { Text(nickname.ifBlank { "구성원 ${index + 1}" }) }
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        val (selectedUid, _) = familyMembers[safeIndex]
        val currentCash = memberBalances[selectedUid] ?: 0L
        val currentHoldings = memberHoldings[selectedUid] ?: emptyList()

        // ① 예수금 잔액 수정
        Text(
            "① 예수금 잔액 수정",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        CashEditSection(
            currentCash = currentCash,
            onSave = { amount, date -> onUpdateCash(selectedUid, amount, date) }
        )

        Spacer(Modifier.height(24.dp))

        // ② 초기 주식 설정
        Text(
            "② 초기 주식 설정",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "현재 보유 종목 (체결 등록 이전 초기 잔고 세팅용)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        if (currentHoldings.isEmpty()) {
            Text(
                "등록된 보유 종목이 없습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        } else {
            currentHoldings.forEach { holding ->
                HoldingManagementRow(
                    holding = holding,
                    onRemove = { onRemoveHolding(selectedUid, holding.stock.ticker) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        AddHoldingForm(
            onAdd = { holding, acquiredAt -> onAddHolding(selectedUid, holding, acquiredAt) },
            searchResults = stockSearchResults,
            onSearch = onStockSearch,
            onClearSearch = onClearStockSearch,
            recentSearches = recentSearches,
            onAddRecentSearch = onAddRecentSearch,
            onRemoveRecentSearch = onRemoveRecentSearch
        )

        Spacer(Modifier.height(24.dp))

        // ③ 이자 지급
        Text(
            "③ 이자 지급",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "이자를 지급하면 예수금이 증가하고 장부에 자동 기록됩니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        InterestSection(
            onAdd = { amount, desc, date -> onAddInterest(selectedUid, amount, desc, date) }
        )
    }
}

// ── 예수금 수정 섹션 ──────────────────────────────────────────

@Composable
private fun CashEditSection(currentCash: Long, onSave: (amount: Long, date: Long) -> Unit) {
    val todayStr  = remember { SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date()) }
    var cashText  by remember(currentCash) { mutableStateOf(currentCash.toString()) }
    var dateText  by remember { mutableStateOf(todayStr) }
    Column {
        OutlinedTextField(
            value = cashText,
            onValueChange = { cashText = it.filter { c -> c.isDigit() } },
            label = { Text("예수금 (원)") },
            singleLine = true,
            suffix = { Text("원") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = { Text("현재: ₩${"%,d".format(currentCash)}") }
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = dateText,
            onValueChange = { dateText = it },
            label = { Text("설정 기준일") },
            placeholder = { Text("YYYY-MM-DD") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val date = runCatching {
                    SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).parse(dateText)?.time
                }.getOrNull() ?: System.currentTimeMillis()
                onSave(cashText.toLongOrNull() ?: currentCash, date)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = dateText.length == 10
        ) { Text("저장") }
    }
}

// ── 이자 지급 섹션 ────────────────────────────────────────────

@Composable
private fun InterestSection(onAdd: (amount: Long, description: String, date: Long) -> Unit) {
    val todayStr    = remember { SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date()) }
    var amountText  by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var dateText    by remember { mutableStateOf(todayStr) }

    val isValid = (amountText.toLongOrNull() ?: 0L) > 0L && dateText.length == 10

    Column {
        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it.filter { c -> c.isDigit() } },
            label = { Text("이자 금액 (원)") },
            singleLine = true,
            suffix = { Text("원") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("이자 종류 (예: 월 이자, 정기예금 이자)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = dateText,
            onValueChange = { dateText = it },
            label = { Text("지급 일자") },
            placeholder = { Text("YYYY-MM-DD") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val date = runCatching {
                    SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).parse(dateText)?.time
                }.getOrNull() ?: System.currentTimeMillis()
                onAdd(amountText.toLongOrNull() ?: 0L, description, date)
                amountText = ""; description = ""; dateText = todayStr
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = isValid
        ) {
            Text("이자 지급")
        }
    }
}

// ── 보유 종목 행 ──────────────────────────────────────────────

@Composable
private fun HoldingManagementRow(holding: HoldingItem, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${holding.stock.name} (${holding.stock.ticker})",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                "${holding.quantity}주 · 평단가 ₩${"%,d".format(holding.avgPrice)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Delete, contentDescription = "삭제",
                tint = MaterialTheme.colorScheme.error)
        }
    }
    HorizontalDivider()
}

// ── 종목 추가 폼 ──────────────────────────────────────────────

@Composable
private fun AddHoldingForm(
    onAdd: (HoldingItem, acquiredAt: Long) -> Unit,
    searchResults: List<StockItem>,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    recentSearches: List<StockItem>,
    onAddRecentSearch: (StockItem) -> Unit,
    onRemoveRecentSearch: (String) -> Unit
) {
    val todayStr     = remember { SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date()) }
    var ticker       by remember { mutableStateOf("") }
    var name         by remember { mutableStateOf("") }
    var qtyText      by remember { mutableStateOf("") }
    var avgPriceText by remember { mutableStateOf("") }
    var dateText     by remember { mutableStateOf(todayStr) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchQuery  by remember { mutableStateOf("") }

    val isValid = ticker.isNotBlank() && name.isNotBlank() &&
            (qtyText.toIntOrNull() ?: 0) > 0 && (avgPriceText.toLongOrNull() ?: 0L) > 0L &&
            dateText.length == 10

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "새 종목 추가",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            OutlinedButton(
                onClick = { showSearchDialog = true },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("🔍 종목 검색", style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = ticker,
                onValueChange = { ticker = it.uppercase() },
                label = { Text("종목코드") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("종목명") },
                singleLine = true,
                modifier = Modifier.weight(1.5f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = qtyText,
                onValueChange = { qtyText = it.filter { c -> c.isDigit() } },
                label = { Text("수량 (주)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = avgPriceText,
                onValueChange = { avgPriceText = it.filter { c -> c.isDigit() } },
                label = { Text("평단가 (원)") },
                singleLine = true,
                modifier = Modifier.weight(1.5f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = dateText,
            onValueChange = { dateText = it },
            label = { Text("취득일") },
            placeholder = { Text("YYYY-MM-DD") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val avgPrice = avgPriceText.toLongOrNull() ?: 0L
                val acquiredAt = runCatching {
                    SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).parse(dateText)?.time
                }.getOrNull() ?: System.currentTimeMillis()
                onAdd(
                    HoldingItem(
                        stock = StockItem(ticker, name, avgPrice, 0.0),
                        quantity = qtyText.toIntOrNull() ?: 0,
                        avgPrice = avgPrice
                    ),
                    acquiredAt
                )
                ticker = ""; name = ""; qtyText = ""; avgPriceText = ""; dateText = todayStr
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = isValid
        ) { Text("추가") }
    }

    // ── 종목 검색 다이얼로그 ───────────────────────────────────
    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = {
                showSearchDialog = false
                searchQuery = ""
                onClearSearch()
            },
            title = { Text("종목 검색", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    // 최근 검색 칩
                    if (recentSearches.isNotEmpty() && searchQuery.isBlank()) {
                        Text(
                            "최근 검색",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(recentSearches, key = { it.ticker }) { stock ->
                                AdminRecentChip(
                                    stock = stock,
                                    onClick = {
                                        ticker = stock.ticker
                                        name = stock.name
                                        showSearchDialog = false
                                        searchQuery = ""
                                        onClearSearch()
                                    },
                                    onRemove = { onRemoveRecentSearch(stock.ticker) }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { q ->
                            searchQuery = q
                            if (q.isBlank()) onClearSearch() else onSearch(q)
                        },
                        label = { Text("종목명 또는 종목코드") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))

                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        if (searchResults.isEmpty() && searchQuery.isNotBlank()) {
                            Text(
                                "검색 결과가 없습니다.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        searchResults.forEach { stock ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        ticker = stock.ticker
                                        name = stock.name
                                        onAddRecentSearch(stock)
                                        showSearchDialog = false
                                        searchQuery = ""
                                        onClearSearch()
                                    }
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stock.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        stock.ticker,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    "₩${"%,d".format(stock.currentPrice)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showSearchDialog = false
                    searchQuery = ""
                    onClearSearch()
                }) {
                    Text("닫기")
                }
            }
        )
    }
}

// ── 관리자 패널 최근 검색 칩 ──────────────────────────────────

@Composable
private fun AdminRecentChip(
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
                    Icons.Default.Delete,
                    contentDescription = "삭제",
                    modifier = Modifier.size(11.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// ── 소수점 입력 TextField ─────────────────────────────────────

@Composable
private fun DecimalTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { new ->
            val dotCount = new.count { it == '.' }
            if (new.all { it.isDigit() || it == '.' } && dotCount <= 1) onValueChange(new)
        },
        label = { Text(label) },
        singleLine = true,
        suffix = { Text("%") },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

// ── 🔒 독립 파이어베이스 서버 연동 섹션 ──────────────────────

@Composable
private fun FirebaseServerSection(viewModel: FamilyStockViewModel) {
    val context    = LocalContext.current
    val clipboard  = LocalClipboardManager.current
    val inviteCode by viewModel.inviteCode.collectAsState()

    var expanded    by remember { mutableStateOf(false) }
    var apiKey      by remember { mutableStateOf(FirebaseConfigManager.loadConfig(context)?.apiKey      ?: "") }
    var appId       by remember { mutableStateOf(FirebaseConfigManager.loadConfig(context)?.appId       ?: "") }
    var projectId   by remember { mutableStateOf(FirebaseConfigManager.loadConfig(context)?.projectId   ?: "") }
    var gcmSenderId by remember { mutableStateOf(FirebaseConfigManager.loadConfig(context)?.gcmSenderId ?: "") }

    val isCustom = FirebaseConfigManager.isUsingCustomFirebase(context)

    Text(
        text = "🔒 독립 파이어베이스 서버 연동",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCustom) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp)) {

            // 현재 연결 상태
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isCustom) "✓ 전용 서버 연결 중" else "기본 서버 (개발자) 사용 중",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCustom) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isCustom && projectId.isNotBlank()) {
                        Text(
                            text = "Project: $projectId",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "접기" else "펼치기"
                    )
                }
            }

            // 초대코드 표시 (부모 전용)
            inviteCode?.let { code ->
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text(
                    "자녀 초대 코드",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = code,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Button(
                        onClick = { clipboard.setText(AnnotatedString(code)) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("복사", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text(
                    "이 코드를 자녀에게 공유하면 자녀가 이 전용 서버에 연결할 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 키 편집 폼 (접었다 펼치기)
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))

                    Text(
                        "Firebase 키 변경",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "변경 후 앱을 재시작해야 적용됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))

                    listOf(
                        Triple("① API Key",        apiKey,      { v: String -> apiKey = v }),
                        Triple("② Application ID", appId,       { v: String -> appId = v }),
                        Triple("③ Project ID",     projectId,   { v: String -> projectId = v }),
                        Triple("④ GCM Sender ID",  gcmSenderId, { v: String -> gcmSenderId = v })
                    ).forEach { (label, value, onChange) ->
                        OutlinedTextField(
                            value = value,
                            onValueChange = onChange,
                            label = { Text(label) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                        )
                        Spacer(Modifier.height(6.dp))
                    }

                    Spacer(Modifier.height(8.dp))

                    val allFilled = apiKey.isNotBlank() && appId.isNotBlank() &&
                                    projectId.isNotBlank() && gcmSenderId.isNotBlank()

                    Button(
                        onClick = {
                            viewModel.updateFirebaseConfig(
                                FirebaseCustomConfig(
                                    apiKey      = apiKey.trim(),
                                    appId       = appId.trim(),
                                    projectId   = projectId.trim(),
                                    gcmSenderId = gcmSenderId.trim()
                                )
                            ) {
                                // 재시작 (새 키는 다음 앱 실행 시 MainActivity.onCreate 에서 초기화됨)
                                val intent = context.packageManager
                                    .getLaunchIntentForPackage(context.packageName)!!
                                    .apply {
                                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = allFilled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("저장 후 앱 재시작")
                    }

                    Spacer(Modifier.height(8.dp))

                    // 초대코드 재발급
                    if (isCustom) {
                        Button(
                            onClick = {
                                val config = FirebaseConfigManager.loadConfig(context)
                                if (config != null) {
                                    viewModel.generateAndSaveInviteCode(config)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            Text("초대 코드 재발급")
                        }
                    }
                }
            }
        }
    }
}
