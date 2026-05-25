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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.juns_git.familystockgate.data.model.UserRole
import io.github.juns_git.familystockgate.ui.viewmodel.AppViewModel

// [Frame 8~9] Leaderboard & Settings
@Composable
fun LeaderboardScreen(viewModel: AppViewModel, innerPadding: PaddingValues) {
    val role by viewModel.debugRole.collectAsState()
    val roleLabel = if (role == UserRole.PARENT) "부모" else "자녀"

    var fcmEnabled by remember { mutableStateOf(true) }

    // 테스트용 더미 랭킹 데이터
    val rankings = listOf(
        RankEntry("홍길동", "+15.3%", "₩1,153,000", isMe = false),
        RankEntry("김철수", "+8.7%", "₩1,087,000", isMe = false),
        RankEntry("부모님", "+3.2%", "₩1,032,000", isMe = role == UserRole.PARENT),
        RankEntry("이영희", "-2.1%", "₩978,900", isMe = false)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "[Frame 8~9] $roleLabel 모드 · 리더보드 & 설정",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(16.dp))

        // ── 가족 수익률 랭킹 보드 ─────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("🏆 가족 수익률 랭킹", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))

                rankings.forEachIndexed { index, entry ->
                    RankRow(rank = index + 1, entry = entry)
                    if (index < rankings.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── 설정 ─────────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("설정", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))

                // FCM 푸시 알림 ON/OFF
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("푸시 알림", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "거래 요청, 승인/거절, 잔액 변경 알림",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = fcmEnabled,
                        onCheckedChange = { enabled ->
                            fcmEnabled = enabled
                            // TODO: Firestore users/{uid}.fcmEnabled 필드 업데이트
                            //       또는 Firebase Messaging 토픽 구독/해제 처리
                        }
                    )
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { viewModel.signOut() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("로그아웃")
                }
            }
        }
    }
}

private data class RankEntry(
    val name: String,
    val rate: String,
    val totalAsset: String,
    val isMe: Boolean
)

@Composable
private fun RankRow(rank: Int, entry: RankEntry) {
    val medal = when (rank) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> "${rank}위"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(medal, style = MaterialTheme.typography.titleLarge)

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (entry.isMe) FontWeight.Bold else FontWeight.Normal
                )
                if (entry.isMe) {
                    Text(
                        text = " (나)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = entry.totalAsset,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = entry.rate,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (entry.rate.startsWith("+")) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
        )
    }
}
