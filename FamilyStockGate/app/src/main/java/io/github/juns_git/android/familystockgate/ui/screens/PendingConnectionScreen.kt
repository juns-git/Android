package io.github.juns_git.android.familystockgate.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.juns_git.android.familystockgate.ui.viewmodel.AppViewModel

// 자녀가 부모의 승인을 기다리는 대기 화면
@Composable
fun PendingConnectionScreen(
    viewModel: AppViewModel,
    innerPadding: PaddingValues,
    onApproved: () -> Unit
) {
    // TODO: Firebase Firestore 실시간 리스너로 users/{uid}.familyId 변화를 감지하여
    //       null → 값 으로 바뀌면 자동으로 onApproved() 호출
    // LaunchedEffect(Unit) {
    //     viewModel.currentUser.collect { user ->
    //         if (user?.familyId != null) onApproved()
    //     }
    // }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(32.dp))

        Text(
            text = "부모님의 승인을 기다리는 중...",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "부모님 기기에서 앱을 열어\n연결 요청을 승인해 주세요.\n\n승인 즉시 이 화면을 벗어납니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(64.dp))

        // [DEBUG] 테스트용 우회 버튼
        OutlinedButton(
            onClick = onApproved,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("[DEBUG] 승인된 것으로 간주하고 홈으로 이동")
        }
    }
}
