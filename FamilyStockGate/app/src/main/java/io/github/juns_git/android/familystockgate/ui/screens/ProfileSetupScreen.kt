package io.github.juns_git.android.familystockgate.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.juns_git.android.familystockgate.data.model.UserRole
import io.github.juns_git.android.familystockgate.ui.theme.CharacterBadge
import io.github.juns_git.android.familystockgate.ui.viewmodel.AuthState
import io.github.juns_git.android.familystockgate.ui.viewmodel.FamilyStockViewModel
import io.github.juns_git.android.familystockgate.ui.viewmodel.InviteCodeFetchState
import io.github.juns_git.android.familystockgate.ui.viewmodel.ServerSetupState
import io.github.juns_git.android.familystockgate.utils.FirebaseConfigManager
import io.github.juns_git.android.familystockgate.utils.FirebaseCustomConfig

// ── 앱 재시작 헬퍼 ────────────────────────────────────────────────────────────

private fun restartApp(context: android.content.Context) {
    val intent = context.packageManager
        .getLaunchIntentForPackage(context.packageName)!!
        .apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK) }
    context.startActivity(intent)
}

// ── [Frame 1] Profile Setup ───────────────────────────────────────────────────

@Composable
fun ProfileSetupScreen(
    viewModel: FamilyStockViewModel,
    innerPadding: PaddingValues,
    onSignOut: () -> Unit
) {
    // null = 아직 미선택 (초기 상태) — 역할을 선택해야만 다음 단계로 진행
    var selectedRole by remember { mutableStateOf<UserRole?>(null) }

    // 닉네임은 상위에서 관리 — serverSetupState/inviteCodeFetchState 완료 시 createProfile 호출에 사용
    var pendingNickname by remember { mutableStateOf("") }

    val authState          by viewModel.authState.collectAsState()
    val parentCheckDone    by viewModel.parentExistsCheckDone.collectAsState()
    val serverSetupState   by viewModel.serverSetupState.collectAsState()
    val inviteCodeFetch    by viewModel.inviteCodeFetchState.collectAsState()
    val isLoading          by viewModel.isLoading.collectAsState()
    val errorMessage       by viewModel.errorMessage.collectAsState()

    // PARENT 역할을 선택(또는 ServerSetupRequired로 전환)될 때만 부모 존재 여부 조회
    // ServerSetupRequired는 이미 checkParentExists 결과이므로 재조회 불필요
    LaunchedEffect(selectedRole) {
        if (selectedRole == UserRole.PARENT && authState !is AuthState.ServerSetupRequired) {
            viewModel.checkParentExists()
        }
    }

    // 독립 서버 개설 완료 → 부모 프로필 생성 (authState는 ViewModel 내부에서 LoggedIn으로 전환)
    LaunchedEffect(serverSetupState) {
        if (serverSetupState is ServerSetupState.Done && pendingNickname.isNotBlank()) {
            viewModel.createParentProfile(pendingNickname)
            pendingNickname = ""
            viewModel.resetServerSetup()
        }
    }

    // 자녀 초대코드 연결 완료 → 자녀 프로필 생성 (authState는 PendingConnection으로 전환)
    LaunchedEffect(inviteCodeFetch) {
        if (inviteCodeFetch is InviteCodeFetchState.Success && pendingNickname.isNotBlank()) {
            viewModel.createChildProfile(pendingNickname)
            pendingNickname = ""
            viewModel.resetInviteCodeFetch()
        }
    }

    // 이후 navigation은 AppNavHost의 LaunchedEffect(authState)가 담당

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(24.dp))

        CharacterBadge(size = 64.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(12.dp))

        Text("프로필 설정", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "역할을 선택하면 가입 방식이 결정됩니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        // ── 역할 선택 (미선택 시 안내 문구 표시) ──────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(UserRole.PARENT to "부모 (관리자)", UserRole.CHILD to "자녀").forEach { (role, label) ->
                val selected = selectedRole == role
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                        .selectable(selected = selected, onClick = { selectedRole = role })
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected, onClick = { selectedRole = role })
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))

        // 전역 에러 메시지
        if (errorMessage != null) {
            Text(
                errorMessage!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(8.dp))
        }

        // ── 역할별 UI 분기 ─────────────────────────────────────────────────────
        when (selectedRole) {
            null -> {
                // 역할 미선택 상태: 안내만 표시, 아무런 Firestore 작업 없음
                Text(
                    "역할을 선택하면 가입 방식이 표시됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            UserRole.PARENT -> ParentSection(
                authState        = authState,
                parentCheckDone  = parentCheckDone,
                serverSetupState = serverSetupState,
                isLoading        = isLoading,
                onStartSetup = { config, nickname ->
                    pendingNickname = nickname
                    viewModel.generateAndSaveInviteCode(config)
                },
                onDirectStart = { nickname ->
                    viewModel.createParentProfile(nickname)
                }
            )
            UserRole.CHILD -> ChildSection(
                inviteCodeFetchState = inviteCodeFetch,
                isLoading            = isLoading,
                onConnect = { code, nickname ->
                    pendingNickname = nickname
                    viewModel.fetchKeysFromInviteCode(code)
                },
                onDirectStart = { nickname ->
                    viewModel.createChildProfile(nickname)
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp, MaterialTheme.colorScheme.error
            )
        ) {
            Text("로그아웃")
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ── 부모 섹션 ─────────────────────────────────────────────────────────────────

@Composable
private fun ParentSection(
    authState: AuthState,
    parentCheckDone: Boolean,
    serverSetupState: ServerSetupState,
    isLoading: Boolean,
    onStartSetup: (FirebaseCustomConfig, String) -> Unit,
    onDirectStart: (nickname: String) -> Unit
) {
    when {
        // 이미 부모 존재 → 독립 서버 개설 가이드 강제 표시
        authState is AuthState.ServerSetupRequired -> {
            IndependentServerSetupGuide(
                serverSetupState = serverSetupState,
                onSetup = onStartSetup
            )
        }

        // 부모 존재 확인 중
        !parentCheckDone -> {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "서버 현황 확인 중…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 등록된 부모 없음 → 바로 가입 허용
        else -> {
            var nickname by remember { mutableStateOf("") }
            Text(
                "이 서버에 등록된 부모 계정이 없습니다. 바로 시작할 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text("닉네임") },
                placeholder = { Text("앱에서 사용할 이름") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onDirectStart(nickname) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = nickname.isNotBlank() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("부모 계정으로 시작하기")
                }
            }
        }
    }
}

// ── 독립 서버 개설 가이드 ────────────────────────────────────────────────────

@Composable
private fun IndependentServerSetupGuide(
    serverSetupState: ServerSetupState,
    onSetup: (FirebaseCustomConfig, String) -> Unit
) {
    val clipboard = LocalClipboardManager.current

    var apiKey      by remember { mutableStateOf("") }
    var appId       by remember { mutableStateOf("") }
    var projectId   by remember { mutableStateOf("") }
    var gcmSenderId by remember { mutableStateOf("") }
    var nickname    by remember { mutableStateOf("") }

    val allFilled = apiKey.isNotBlank() && appId.isNotBlank() &&
                    projectId.isNotBlank() && gcmSenderId.isNotBlank() && nickname.isNotBlank()

    // ⚠️ 경고 카드
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "⚠️ 이미 등록된 부모 계정이 존재하는 서버입니다",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "다른 가족이 독립적으로 이 앱을 사용하시려면, 본인 가정을 위한 전용 파이어베이스 프로젝트를 개설해야 합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }

    Spacer(Modifier.height(20.dp))

    // 개설 순서
    Text("📋 개설 순서", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))

    val steps = listOf(
        "Firebase 콘솔(console.firebase.google.com)에서 새 프로젝트를 생성하세요.",
        "안드로이드 앱 추가 → 패키지명에 반드시 아래 값을 입력하세요.\n\nio.github.juns_git.android.familystockgate",
        "SHA-1 서명 지문 입력 칸에 아래 개발자 공용 인증 키를 입력하세요.",
        "Authentication 메뉴 → Google 로그인을 활성화하세요.",
        "Firestore Database 메뉴 → 데이터베이스를 테스트 모드로 생성하세요."
    )
    steps.forEachIndexed { i, step ->
        StepRow(number = i + 1, text = step)
        if (i == 1) {
            CodeChip(
                code = "io.github.juns_git.android.familystockgate",
                onCopy = { clipboard.setText(AnnotatedString("io.github.juns_git.android.familystockgate")) }
            )
        }
        if (i == 2) {
            CodeChip(
                code = FirebaseConfigManager.DEV_SHA1,
                onCopy = { clipboard.setText(AnnotatedString(FirebaseConfigManager.DEV_SHA1)) }
            )
        }
        Spacer(Modifier.height(8.dp))
    }

    Spacer(Modifier.height(20.dp))
    HorizontalDivider()
    Spacer(Modifier.height(20.dp))

    // google-services.json 키 매핑 가이드
    Text(
        "🗺️ google-services.json 키 매핑 가이드",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    Text(
        "다운로드한 google-services.json 에서 아래 항목을 복사해 아래 입력창에 붙여넣으세요.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(12.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            JsonMappingRow("① API Key",        "api_key  →  \"current_key\"")
            JsonMappingRow("② Application ID", "client_info  →  \"mobilesdk_app_id\"")
            JsonMappingRow("③ Project ID",     "project_info  →  \"project_id\"")
            JsonMappingRow("④ GCM Sender ID",  "project_info  →  \"project_number\"")
        }
    }

    Spacer(Modifier.height(20.dp))
    HorizontalDivider()
    Spacer(Modifier.height(20.dp))

    // 4가지 키 입력 폼
    Text("🔑 발급받은 키 입력", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))

    listOf(
        Triple("① API Key",        apiKey,      { v: String -> apiKey = v }),
        Triple("② Application ID", appId,       { v: String -> appId = v }),
        Triple("③ Project ID",     projectId,   { v: String -> projectId = v }),
        Triple("④ GCM Sender ID",  gcmSenderId, { v: String -> gcmSenderId = v })
    ).forEach { (label, value, onValueChange) ->
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
        )
        Spacer(Modifier.height(8.dp))
    }

    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = nickname,
        onValueChange = { nickname = it },
        label = { Text("닉네임 (앱에서 사용할 이름)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(Modifier.height(20.dp))

    if (serverSetupState is ServerSetupState.Error) {
        Text(
            serverSetupState.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(8.dp))
    }

    val isLoading = serverSetupState is ServerSetupState.Loading
    Button(
        onClick = {
            onSetup(
                FirebaseCustomConfig(
                    apiKey      = apiKey.trim(),
                    appId       = appId.trim(),
                    projectId   = projectId.trim(),
                    gcmSenderId = gcmSenderId.trim()
                ),
                nickname.trim()
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = allFilled && !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text("우리 가족 전용 서버 개설 및 시작하기", fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── 자녀 섹션 ─────────────────────────────────────────────────────────────────

@Composable
private fun ChildSection(
    inviteCodeFetchState: InviteCodeFetchState,
    isLoading: Boolean,
    onConnect: (code: String, nickname: String) -> Unit,
    onDirectStart: (nickname: String) -> Unit
) {
    val context = LocalContext.current
    val hasConfig = remember { FirebaseConfigManager.hasConfig(context) }

    var inviteCode by remember { mutableStateOf("") }
    var nickname   by remember { mutableStateOf("") }

    if (hasConfig) {
        // 이미 부모 서버에 연결된 상태 → 닉네임만 입력
        Text(
            "✓ 부모님의 전용 서버에 연결되어 있습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            label = { Text("닉네임") },
            placeholder = { Text("앱에서 사용할 이름") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onDirectStart(nickname) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = nickname.isNotBlank() && !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("자녀 계정으로 시작하기")
            }
        }
        return
    }

    // 부모 서버 미연결 → 초대코드 입력
    Text(
        "부모님이 공유해준 6자리 초대 코드를 입력하세요.\n초대 코드는 부모님의 설정 화면에서 확인할 수 있습니다.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(16.dp))

    OutlinedTextField(
        value = inviteCode,
        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) inviteCode = it },
        label = { Text("초대 코드") },
        placeholder = { Text("6자리 숫자") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = { Text("숫자 6자리  (예: 392847)") }
    )

    Spacer(Modifier.height(8.dp))

    OutlinedTextField(
        value = nickname,
        onValueChange = { nickname = it },
        label = { Text("닉네임") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(Modifier.height(16.dp))

    if (inviteCodeFetchState is InviteCodeFetchState.Error) {
        Text(
            inviteCodeFetchState.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(8.dp))
    }

    val isFetching = inviteCodeFetchState is InviteCodeFetchState.Loading
    Button(
        onClick = { onConnect(inviteCode, nickname) },
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        enabled = inviteCode.length == 6 && nickname.isNotBlank() && !isFetching && !isLoading
    ) {
        if (isFetching || isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text("부모 서버에 연결하기")
        }
    }

}

// ── 공용 UI 컴포넌트 ──────────────────────────────────────────────────────────

@Composable
private fun StepRow(number: Int, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$number",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CodeChip(code: String, onCopy: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 36.dp, top = 4.dp, bottom = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.08f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            code,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onCopy, modifier = Modifier.size(24.dp)) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = "복사",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun JsonMappingRow(field: String, path: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            field,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.9f)
        )
        Text(
            path,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1.1f)
        )
    }
}
