package io.github.juns_git.familystockgate.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.juns_git.familystockgate.data.model.UserRole
import io.github.juns_git.familystockgate.ui.viewmodel.AppViewModel

// [Frame 1] Profile Setup
@Composable
fun ProfileSetupScreen(
    viewModel: AppViewModel,
    innerPadding: PaddingValues,
    onParentSetupComplete: () -> Unit,
    onChildPendingConnection: () -> Unit
) {
    var nickname by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(UserRole.PARENT) }
    var parentEmail by remember { mutableStateOf("") }

    val searchedParentUid by viewModel.searchedParentUid.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(24.dp))

        Text("[Frame 1] 프로필 설정", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))

        // 닉네임 입력
        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            label = { Text("닉네임") },
            placeholder = { Text("앱에서 사용할 이름을 입력하세요") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))

        // 역할 선택 라디오 버튼
        Text("역할 선택", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Row {
            listOf(UserRole.PARENT to "부모", UserRole.CHILD to "자녀").forEach { (role, label) ->
                Row(
                    modifier = Modifier
                        .selectable(
                            selected = selectedRole == role,
                            onClick = {
                                selectedRole = role
                                viewModel.clearSearchResult()
                            }
                        )
                        .padding(end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedRole == role,
                        onClick = {
                            selectedRole = role
                            viewModel.clearSearchResult()
                        }
                    )
                    Text(label, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }

        // 자녀 모드: 부모 이메일 검색 UI
        if (selectedRole == UserRole.CHILD) {
            Spacer(Modifier.height(20.dp))
            Text("부모 계정 연결", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = parentEmail,
                onValueChange = { parentEmail = it },
                label = { Text("부모 구글 이메일") },
                placeholder = { Text("parent@gmail.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { viewModel.searchParentByEmail(parentEmail) },
                modifier = Modifier.fillMaxWidth(),
                enabled = parentEmail.contains("@") && !isLoading
            ) {
                Text(if (isLoading) "검색 중..." else "이메일로 부모 검색")
            }

            // 검색 결과 카드
            searchedParentUid?.let { uid ->
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "✓ 부모 계정 발견",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            parentEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                // TODO: viewModel.createChildUser(uid, email, nickname) 먼저 호출 후
                                viewModel.sendConnectionRequest(uid)
                                onChildPendingConnection()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = nickname.isNotBlank()
                        ) {
                            Text("연결 요청 보내기")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(16.dp))

        // 부모 모드 시작 버튼 (자녀 모드는 위 연결 요청 버튼으로 처리)
        if (selectedRole == UserRole.PARENT) {
            Button(
                onClick = {
                    // TODO: viewModel.createParentUser(uid, email, nickname)
                    onParentSetupComplete()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = nickname.isNotBlank()
            ) {
                Text("부모 계정으로 시작하기")
            }
        } else {
            OutlinedButton(
                onClick = {
                    // TODO: viewModel.createChildUser(uid, email, nickname) (familyId 없이 생성)
                    onParentSetupComplete() // [DEBUG] 연결 없이 바로 홈으로 (테스트용)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = nickname.isNotBlank()
            ) {
                Text("[DEBUG] 연결 없이 홈으로 이동 (테스트용)")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
