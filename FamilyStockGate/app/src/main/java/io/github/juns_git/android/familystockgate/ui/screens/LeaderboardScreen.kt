package io.github.juns_git.android.familystockgate.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import io.github.juns_git.android.familystockgate.data.model.LeaderboardEntry
import io.github.juns_git.android.familystockgate.ui.viewmodel.AppViewModel

// [Frame 8] Leaderboard — 가족 실시간 수익률 랭킹 + FCM 알림 설정
@Composable
fun LeaderboardScreen(
    viewModel: AppViewModel,
    innerPadding: PaddingValues,
    onUserClick: (uid: String, nickname: String) -> Unit = { _, _ -> }
) {
    val context     = LocalContext.current
    val leaderboard by viewModel.leaderboard.collectAsState()
    val fcmEnabled  by viewModel.fcmEnabled.collectAsState()

    // 시스템 레벨 알림 권한 상태 (화면 진입 시 1회 스냅샷)
    val notificationGranted = remember {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    // Switch가 실제로 표시할 상태: 앱 설정 ON + 시스템 권한 허용 모두 충족 시에만 ON
    val switchOn = fcmEnabled && notificationGranted

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "가족 투자 현황",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))

        // ── 🏆 가족 수익률 랭킹 보드 ──────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("🏆 가족 수익률 랭킹", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))

                if (leaderboard.isEmpty()) {
                    Text(
                        "아직 자녀 투자 실적이 없습니다.\n자녀가 가족 그룹에 연결되면 자동으로 나타납니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    leaderboard.forEachIndexed { index, entry ->
                        RankRow(
                            rank = index + 1,
                            entry = entry,
                            onUserClick = { onUserClick(entry.childUid, entry.nickname) }
                        )
                        if (index < leaderboard.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── 🔔 알림 설정 + 로그아웃 ──────────────────────────────────────────
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
                            text = if (!notificationGranted)
                                "시스템 알림이 꺼져 있습니다. 탭하여 설정하기"
                            else
                                "거래 요청, 승인/거절, 잔액 변경 알림",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (!notificationGranted)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = switchOn,
                        onCheckedChange = { enabled ->
                            if (enabled && !notificationGranted) {
                                // 시스템 알림 권한 미허용 → 앱 알림 설정 화면으로 유도
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                context.startActivity(intent)
                            } else {
                                // 앱 레벨 FCM 설정 Firestore 동기화
                                viewModel.updateFcmEnabled(enabled)
                            }
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

// ── 랭킹 행 ──────────────────────────────────────────────────────────────────

@Composable
private fun RankRow(rank: Int, entry: LeaderboardEntry, onUserClick: () -> Unit = {}) {
    val medal = when (rank) {
        1    -> "🥇"
        2    -> "🥈"
        3    -> "🥉"
        else -> "${rank}위"
    }

    // 수익률 표시 문자열: 소수점 둘째자리, 부호 명시
    val rateDisplay = buildString {
        if (entry.profitRate >= 0) append("+")
        append("%.2f".format(entry.profitRate))
        append("%")
    }
    val isPositive = entry.profitRate >= 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onUserClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(medal, style = MaterialTheme.typography.titleLarge)

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.nickname,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (entry.isCurrentUser) FontWeight.Bold else FontWeight.Normal
                )
                if (entry.isCurrentUser) {
                    Text(
                        text = " (나)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = "현재자산 ₩${"%,d".format(entry.totalAsset)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (entry.initialBudget > 0) {
                Text(
                    text = "기초자산 ₩${"%,d".format(entry.initialBudget)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = rateDisplay,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isPositive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
        )
    }
}
