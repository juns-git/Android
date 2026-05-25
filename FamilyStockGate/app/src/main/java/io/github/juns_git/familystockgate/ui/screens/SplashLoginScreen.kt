package io.github.juns_git.familystockgate.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// [Frame 0] Splash & Login
@Composable
fun SplashLoginScreen(
    innerPadding: PaddingValues,
    onLoginSuccess: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 앱 로고 영역
        Surface(
            modifier = Modifier.size(120.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "FSG",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "FamilyStockGate",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = "가족 전용 모의주식 투자 플랫폼",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(64.dp))

        Button(
            onClick = {
                // TODO: Google Sign-In 런처 실행
                //   val signInIntent = googleSignInClient.signInIntent
                //   launcher.launch(signInIntent)
                //   → ActivityResult 콜백에서 Firebase Auth 인증 처리 후 onLoginSuccess() 호출
                onLoginSuccess() // [DEBUG] 테스트: 즉시 다음 화면으로 이동
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Google 계정으로 계속하기")
        }
    }
}
