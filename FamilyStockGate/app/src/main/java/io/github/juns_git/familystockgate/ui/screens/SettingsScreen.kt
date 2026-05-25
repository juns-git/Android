package io.github.juns_git.familystockgate.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.juns_git.familystockgate.data.model.HoldingItem
import io.github.juns_git.familystockgate.data.model.StockItem
import io.github.juns_git.familystockgate.data.model.UserRole
import io.github.juns_git.familystockgate.ui.viewmodel.AppViewModel

private data class DummyChild(val uid: String, val nickname: String)
private val dummyChildren = listOf(
    DummyChild("c1", "홍길동"),
    DummyChild("c2", "김철수")
)

// [Frame 9] Settings — 대리 거래 비용 설정 + 자녀 자산 관리 제어판 (부모 전용)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AppViewModel, innerPadding: PaddingValues) {
    val role by viewModel.debugRole.collectAsState()
    val commissionRate by viewModel.commissionRate.collectAsState()
    val taxRate by viewModel.taxRate.collectAsState()
    val availableCash by viewModel.availableCash.collectAsState()
    val holdings by viewModel.holdings.collectAsState()

    var commissionText by remember(commissionRate) { mutableStateOf(commissionRate.toString()) }
    var taxText by remember(taxRate) { mutableStateOf(taxRate.toString()) }
    var showManagementSheet by remember { mutableStateOf(false) }
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

        // [DEBUG] 역할 전환 — Settings 화면 내 토글
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "[ DEBUG ] 현재 역할: ${if (role == UserRole.PARENT) "부모 (관리자)" else "자녀"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "자녀",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Switch(
                        checked = role == UserRole.PARENT,
                        onCheckedChange = { viewModel.toggleDebugRole() }
                    )
                    Text(
                        "부모",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (role == UserRole.PARENT) {

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
                            viewModel.updateCommissionRate(commissionText.toDoubleOrNull() ?: 0.0)
                            viewModel.updateTaxRate(taxText.toDoubleOrNull() ?: 0.0)
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
            // 자녀 모드: 관리 제어판 완전 숨김
            Text(
                text = "부모 계정에서 설정을 변경할 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }

        Spacer(Modifier.height(32.dp))
    }

    // ── 자녀 자산 관리 BottomSheet ─────────────────────────────
    if (showManagementSheet) {
        ModalBottomSheet(
            onDismissRequest = { showManagementSheet = false },
            sheetState = sheetState
        ) {
            ChildAssetManagementContent(
                availableCash = availableCash,
                holdings = holdings,
                onUpdateCash = { uid, amount -> viewModel.updateChildCash(uid, amount) },
                onAddHolding = { viewModel.addInitialHolding(it) },
                onRemoveHolding = { viewModel.removeInitialHolding(it) }
            )
        }
    }
}

// ── 자녀 자산 관리 시트 내용 ──────────────────────────────────

@Composable
private fun ChildAssetManagementContent(
    availableCash: Long,
    holdings: List<HoldingItem>,
    onUpdateCash: (uid: String, amount: Long) -> Unit,
    onAddHolding: (HoldingItem) -> Unit,
    onRemoveHolding: (ticker: String) -> Unit
) {
    var selectedChildIndex by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 40.dp)
    ) {
        Text(
            "자녀 실전 자산 관리 제어판",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))

        // 자녀 선택 탭
        TabRow(selectedTabIndex = selectedChildIndex) {
            dummyChildren.forEachIndexed { index, child ->
                Tab(
                    selected = selectedChildIndex == index,
                    onClick = { selectedChildIndex = index },
                    text = { Text(child.nickname) }
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        val selectedChild = dummyChildren[selectedChildIndex]

        // ① 예수금 잔액 수정
        Text(
            "① 예수금 잔액 수정",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        CashEditSection(
            currentCash = availableCash,
            onSave = { amount -> onUpdateCash(selectedChild.uid, amount) }
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

        if (holdings.isEmpty()) {
            Text(
                "등록된 보유 종목이 없습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        } else {
            holdings.forEach { holding ->
                HoldingManagementRow(
                    holding = holding,
                    onRemove = { onRemoveHolding(holding.stock.ticker) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        AddHoldingForm(onAdd = onAddHolding)
    }
}

// ── 예수금 수정 섹션 ──────────────────────────────────────────

@Composable
private fun CashEditSection(currentCash: Long, onSave: (Long) -> Unit) {
    var cashText by remember(currentCash) { mutableStateOf(currentCash.toString()) }
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
        Button(
            onClick = { onSave(cashText.toLongOrNull() ?: currentCash) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("저장") }
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
private fun AddHoldingForm(onAdd: (HoldingItem) -> Unit) {
    var ticker by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var qtyText by remember { mutableStateOf("") }
    var avgPriceText by remember { mutableStateOf("") }

    val isValid = ticker.isNotBlank() && name.isNotBlank() &&
            (qtyText.toIntOrNull() ?: 0) > 0 && (avgPriceText.toLongOrNull() ?: 0L) > 0L

    Column {
        Text(
            "새 종목 추가",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
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
        Button(
            onClick = {
                val avgPrice = avgPriceText.toLongOrNull() ?: 0L
                onAdd(
                    HoldingItem(
                        stock = StockItem(ticker, name, avgPrice, 0.0),
                        quantity = qtyText.toIntOrNull() ?: 0,
                        avgPrice = avgPrice
                    )
                )
                ticker = ""; name = ""; qtyText = ""; avgPriceText = ""
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = isValid
        ) { Text("추가") }
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
