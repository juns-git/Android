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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import io.github.juns_git.android.familystockgate.data.model.TradeRequest
import io.github.juns_git.android.familystockgate.data.model.TradeStatus
import io.github.juns_git.android.familystockgate.data.model.TradeType
import io.github.juns_git.android.familystockgate.data.model.UserRole
import io.github.juns_git.android.familystockgate.ui.viewmodel.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// [Frame 7] Ledger & Approval Center
// 부모 워크플로: PENDING(접수 대기) → ACCEPTED(접수 완료) → FILLED | PARTIAL_FILLED | UNFILLED
@Composable
fun LedgerScreen(viewModel: AppViewModel, innerPadding: PaddingValues) {
    val role by viewModel.debugRole.collectAsState()
    val tradeRequests by viewModel.tradeRequests.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var fillDialogTarget by remember { mutableStateOf<TradeRequest?>(null) }
    var editDialogTarget by remember { mutableStateOf<TradeRequest?>(null) }

    val pendingChildren by viewModel.pendingChildren.collectAsState()

    // 대기 탭: PENDING + ACCEPTED
    val activePending = tradeRequests.filter {
        it.status == TradeStatus.PENDING || it.status == TradeStatus.ACCEPTED
    }
    // 완료 탭: FILLED + PARTIAL_FILLED + UNFILLED + 구형 호환
    val completed = tradeRequests.filter {
        it.status in listOf(
            TradeStatus.FILLED, TradeStatus.PARTIAL_FILLED,
            TradeStatus.UNFILLED, TradeStatus.APPROVED, TradeStatus.REJECTED
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        Text(
            text = "장부 & 승인 센터",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("대기 중인 요청 (${activePending.size + pendingChildren.size})") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("완료된 거래 기록 (${completed.size})") }
            )
        }

        when (selectedTab) {
            0 -> PendingTab(
                role = role,
                pendingChildren = pendingChildren,
                tradeRequests = activePending,
                onApproveChild = { viewModel.approveChildConnection(it) },
                onRejectChild = { viewModel.rejectChildConnection(it) },
                onAccept = { viewModel.acceptTradeRequest(it.requestId) },
                onFill = { fillDialogTarget = it },
                // 미체결 처리: 다이얼로그 없이 즉시 처리
                onUnfill = { viewModel.unfillTradeRequest(it.requestId) }
            )
            1 -> CompletedTab(
                tradeRequests = completed,
                role = role,
                onEdit = { editDialogTarget = it }
            )
        }
    }

    // ── 체결 등록 다이얼로그 (부분체결 자동 감지) ─────────────
    fillDialogTarget?.let { req ->
        FillDialog(
            request = req,
            onConfirm = { filledPrice, filledQty, completedAt ->
                viewModel.fillTradeRequest(req.requestId, filledPrice, filledQty, completedAt)
                fillDialogTarget = null
            },
            onDismiss = { fillDialogTarget = null }
        )
    }

    // ── 완료 거래 수정 다이얼로그 (부모 전용) ──────────────────
    editDialogTarget?.let { req ->
        EditTradeDialog(
            request = req,
            onConfirm = { newPrice, newQty, newMemo ->
                viewModel.editCompletedTrade(
                    requestId        = req.requestId,
                    childUid         = req.childUid,
                    newFilledPrice   = newPrice,
                    newFilledQuantity= newQty,
                    newMemo          = newMemo
                )
                editDialogTarget = null
            },
            onDismiss = { editDialogTarget = null }
        )
    }
}

// ── 대기 중인 요청 탭 ─────────────────────────────────────────

@Composable
private fun PendingTab(
    role: UserRole,
    pendingChildren: List<Map<String, String>>,
    tradeRequests: List<TradeRequest>,
    onApproveChild: (String) -> Unit,
    onRejectChild: (String) -> Unit,
    onAccept: (TradeRequest) -> Unit,
    onFill: (TradeRequest) -> Unit,
    onUnfill: (TradeRequest) -> Unit
) {
    if (role == UserRole.PARENT) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (pendingChildren.isNotEmpty()) {
                item {
                    Text("가족 연결 요청", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                }
                items(pendingChildren, key = { it["uid"] ?: "" }) { child ->
                    val childUid = child["uid"] ?: ""
                    PendingChildCard(
                        displayName = child["nickname"] ?: child["email"] ?: childUid,
                        onApprove = { onApproveChild(childUid) },
                        onReject = { onRejectChild(childUid) }
                    )
                }
                item { Spacer(Modifier.height(4.dp)) }
            }
            if (tradeRequests.isNotEmpty()) {
                item {
                    Text("거래 요청", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                }
                items(tradeRequests, key = { it.requestId }) { req ->
                    ApprovalCard(
                        request = req,
                        onAccept = { onAccept(req) },
                        onFill = { onFill(req) },
                        onUnfill = { onUnfill(req) }
                    )
                }
            }
            if (pendingChildren.isEmpty() && tradeRequests.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text("대기 중인 요청이 없습니다.")
                    }
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (tradeRequests.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text("대기 중인 요청이 없습니다.")
                    }
                }
            } else {
                items(tradeRequests, key = { it.requestId }) { req ->
                    ChildRequestCard(request = req)
                }
            }
        }
    }
}

// ── 완료된 거래 기록 탭 ───────────────────────────────────────

@Composable
private fun CompletedTab(
    tradeRequests: List<TradeRequest>,
    role: UserRole,
    onEdit: (TradeRequest) -> Unit
) {
    if (tradeRequests.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("완료된 거래 기록이 없습니다.")
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tradeRequests, key = { it.requestId }) { req ->
            CompletedCard(request = req, role = role, onEdit = { onEdit(req) })
        }
    }
}

// ── 카드 컴포저블들 ───────────────────────────────────────────

@Composable
private fun PendingChildCard(displayName: String, onApprove: () -> Unit, onReject: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("[가족 연결 요청]", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text("${displayName} 님이 가족 연결을 요청했습니다.",
                color = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onApprove, modifier = Modifier.weight(1f)) { Text("승인") }
                OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f)) { Text("거절") }
            }
        }
    }
}

// 부모용: PENDING → [접수] / ACCEPTED → [체결 등록] + [미체결 처리 즉시]
@Composable
private fun ApprovalCard(
    request: TradeRequest,
    onAccept: () -> Unit,
    onFill: () -> Unit,
    onUnfill: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            TradeRequestHeader(request)
            Spacer(Modifier.height(8.dp))
            Text("[투자 메모]", style = MaterialTheme.typography.labelSmall)
            Text(
                text = request.memo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4
            )
            Spacer(Modifier.height(10.dp))

            when (request.status) {
                TradeStatus.PENDING -> {
                    Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
                        Text("접수 (증권사 주문 접수)")
                    }
                }
                TradeStatus.ACCEPTED -> {
                    Text(
                        "접수 완료 — 증권사 주문 처리 후 결과를 입력해 주세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onFill, modifier = Modifier.weight(1f)) {
                            Text("체결 등록")
                        }
                        OutlinedButton(onClick = onUnfill, modifier = Modifier.weight(1f)) {
                            Text("미체결 처리")
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}

// 자녀용: 본인 요청 상태 확인 (액션 없음)
@Composable
private fun ChildRequestCard(request: TradeRequest) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            TradeRequestHeader(request)
            Spacer(Modifier.height(4.dp))
            Text(
                text = when (request.status) {
                    TradeStatus.PENDING  -> "부모님이 아직 확인하지 않았습니다."
                    TradeStatus.ACCEPTED -> "부모님이 접수했습니다. 증권사 체결을 기다리는 중..."
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// 완료 탭 카드: 체결/부분체결/미체결 상세 + 완료 일시 강조
@Composable
private fun CompletedCard(request: TradeRequest, role: UserRole, onEdit: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA) }
    val isEditable = role == UserRole.PARENT &&
        request.type != TradeType.INTEREST &&
        request.type != TradeType.DIVIDEND &&
        request.type != TradeType.WITHDRAWAL &&
        (request.status == TradeStatus.FILLED || request.status == TradeStatus.PARTIAL_FILLED)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    TradeRequestHeader(request)
                }
                if (isEditable) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "거래 수정",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))

            // ── 완료 일시 (강조) ──────────────────────────────
            if (request.completedAt > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "완료 일시: ${dateFormat.format(Date(request.completedAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            HorizontalDivider()
            Spacer(Modifier.height(6.dp))

            if (request.type == TradeType.INTEREST || request.type == TradeType.DIVIDEND || request.type == TradeType.WITHDRAWAL) {
                val label = when (request.type) {
                    TradeType.DIVIDEND   -> "배당 금액"
                    TradeType.WITHDRAWAL -> "출금 금액"
                    else                 -> "지급 금액"
                }
                val valueColor = if (request.type == TradeType.WITHDRAWAL)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary
                Text(
                    "$label: ₩${"%,d".format(request.filledPrice)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = valueColor,
                    fontWeight = FontWeight.Medium
                )
            } else when (request.status) {
                TradeStatus.FILLED -> {
                    Text("체결 단가: ₩${"%,d".format(request.filledPrice)}",
                        style = MaterialTheme.typography.bodySmall)
                    Text("체결 수량: ${request.filledQuantity}주",
                        style = MaterialTheme.typography.bodySmall)
                    Text(
                        "총 체결 금액: ₩${"%,d".format(request.filledPrice * request.filledQuantity)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
                TradeStatus.PARTIAL_FILLED -> {
                    Text(
                        "부분 체결: ${request.filledQuantity}주 × ₩${"%,d".format(request.filledPrice)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "부분 체결 금액: ₩${"%,d".format(request.filledPrice * request.filledQuantity)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (request.remainingQuantity > 0) {
                        Text(
                            "잔여 ${request.remainingQuantity}주 — 추가 체결 대기 중",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                TradeStatus.UNFILLED -> {
                    Text(
                        "미체결 처리됨",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
                else -> Unit
            }
        }
    }
}

// 공통 헤더: 종목 + 요청 정보 + 상태 칩
@Composable
private fun TradeRequestHeader(request: TradeRequest) {
    val tsFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (request.type == TradeType.INTEREST || request.type == TradeType.DIVIDEND || request.type == TradeType.WITHDRAWAL) {
                val typeLabel = when (request.type) {
                    TradeType.DIVIDEND   -> "배당 수입"
                    TradeType.WITHDRAWAL -> "출금"
                    else                 -> "이자 수입"
                }
                Text(
                    "${request.childNickname} · $typeLabel",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    request.stockName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val typeLabel = if (request.type == TradeType.BUY) "매수" else "매도"
                Text(
                    "${request.childNickname} · ${request.stockName} (${request.stockTicker})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${typeLabel} · ${request.quantity}주 × ₩${"%,d".format(request.pricePerShare)}" +
                            " = ₩${"%,d".format(request.quantity * request.pricePerShare)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (request.timestamp > 0) {
                Text(
                    text = "요청일시: ${tsFormat.format(Date(request.timestamp))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        TradeStatusChip(status = request.status, type = request.type)
    }
}

@Composable
private fun TradeStatusChip(status: TradeStatus, type: TradeType = TradeType.BUY) {
    val (label, containerColor) = when {
        type == TradeType.DIVIDEND   -> "배당 수입" to MaterialTheme.colorScheme.primaryContainer
        type == TradeType.INTEREST   -> "이자 지급" to MaterialTheme.colorScheme.tertiaryContainer
        type == TradeType.WITHDRAWAL -> "출금"      to MaterialTheme.colorScheme.errorContainer
        else -> when (status) {
            TradeStatus.PENDING        -> "접수 대기"  to MaterialTheme.colorScheme.tertiaryContainer
            TradeStatus.ACCEPTED       -> "접수 완료"  to MaterialTheme.colorScheme.secondaryContainer
            TradeStatus.FILLED         -> "체결 완료"  to MaterialTheme.colorScheme.primaryContainer
            TradeStatus.PARTIAL_FILLED -> "부분 체결"  to MaterialTheme.colorScheme.secondaryContainer
            TradeStatus.UNFILLED       -> "미체결"     to MaterialTheme.colorScheme.errorContainer
            TradeStatus.APPROVED       -> "승인됨"     to MaterialTheme.colorScheme.primaryContainer
            TradeStatus.REJECTED       -> "거절됨"     to MaterialTheme.colorScheme.errorContainer
        }
    }
    Surface(shape = MaterialTheme.shapes.small, color = containerColor) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

// ── 체결 등록 다이얼로그 (부분체결 자동 감지) ─────────────────

@Composable
private fun FillDialog(
    request: TradeRequest,
    onConfirm: (filledPrice: Long, filledQuantity: Int, completedAt: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val todayStr  = remember { SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date()) }
    var priceText by remember { mutableStateOf(request.pricePerShare.toString()) }
    var qtyText   by remember { mutableStateOf(request.quantity.toString()) }
    var dateText  by remember { mutableStateOf(todayStr) }
    val typeLabel = if (request.type == TradeType.BUY) "매수" else "매도"

    val enteredQty = qtyText.toIntOrNull() ?: 0
    val isPartial  = enteredQty > 0 && enteredQty < request.quantity

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("체결 등록") },
        text = {
            Column {
                Text(
                    "${request.stockName} ${typeLabel} · 요청 수량: ${request.quantity}주",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter { c -> c.isDigit() } },
                    label = { Text("실제 체결 단가 (원)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it.filter { c -> c.isDigit() } },
                    label = { Text("체결 수량 (주)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = if (isPartial) ({
                        Text(
                            "부분 체결로 처리됩니다. 잔여 ${request.quantity - enteredQty}주는 접수 완료 상태로 유지됩니다.",
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }) else null
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("체결 일시") },
                    placeholder = { Text("YYYY-MM-DD") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val completedAt = runCatching {
                        SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).parse(dateText)?.time
                    }.getOrNull() ?: System.currentTimeMillis()
                    onConfirm(priceText.toLongOrNull() ?: 0L, enteredQty, completedAt)
                },
                enabled = (priceText.isNotBlank() && enteredQty > 0 && dateText.length == 10)
            ) { Text(if (isPartial) "부분 체결 등록" else "체결 등록") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

// ── 완료 거래 수정 다이얼로그 (부모 전용) ──────────────────────────────────────

@Composable
private fun EditTradeDialog(
    request: TradeRequest,
    onConfirm: (newFilledPrice: Long, newFilledQuantity: Int, newMemo: String) -> Unit,
    onDismiss: () -> Unit
) {
    val typeLabel = if (request.type == TradeType.BUY) "매수" else "매도"
    var priceText by remember { mutableStateOf(request.filledPrice.toString()) }
    var qtyText   by remember { mutableStateOf(request.filledQuantity.toString()) }
    var memoText  by remember { mutableStateOf(request.memo) }

    val newQty    = qtyText.toIntOrNull() ?: 0
    val isPartial = newQty > 0 && newQty < request.quantity

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("거래 기록 수정") },
        text = {
            Column {
                Text(
                    "${request.childNickname} · ${request.stockName} ${typeLabel} · 요청 수량: ${request.quantity}주",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter { c -> c.isDigit() } },
                    label = { Text("체결 단가 (원)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it.filter { c -> c.isDigit() } },
                    label = { Text("체결 수량 (주)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = if (isPartial) ({
                        Text(
                            "부분 체결로 변경됩니다. 잔여 ${request.quantity - newQty}주는 별도 처리됩니다.",
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }) else null
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = memoText,
                    onValueChange = { memoText = it },
                    label = { Text("투자 메모") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        priceText.toLongOrNull() ?: 0L,
                        newQty,
                        memoText
                    )
                },
                enabled = (priceText.isNotBlank() && newQty > 0)
            ) { Text("수정 저장") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("취소") }
        }
    )
}
