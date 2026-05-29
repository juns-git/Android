package io.github.juns_git.android.familystockgate.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessaging
import io.github.juns_git.android.familystockgate.utils.FirebaseConfigManager
import io.github.juns_git.android.familystockgate.utils.FirebaseCustomConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.random.Random

// ── Auth state machine ───────────────────────────────────────────────────────

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()

    /** 최초 가입자 — 역할 선택·프로필 입력 필요 */
    object NeedsProfileSetup : AuthState()

    /** 자녀 계정 — 아직 초대코드 미입력이거나 부모 승인 대기 중 */
    object PendingConnection : AuthState()

    /**
     * 현재 Firebase(기본/커스텀)에 이미 부모 계정이 존재함.
     * 다른 가정은 독립 서버를 개설해야 한다 → 4종 키 입력 가이드 강제 표시.
     */
    object ServerSetupRequired : AuthState()

    /** 인증 완료, 홈 대시보드 진입 가능 */
    data class LoggedIn(val user: UserState) : AuthState()

    data class Error(val message: String) : AuthState()
}

// ── Server/invite code state ─────────────────────────────────────────────────

sealed class InviteCodeFetchState {
    object Idle : InviteCodeFetchState()
    object Loading : InviteCodeFetchState()
    data class Success(val config: FirebaseCustomConfig) : InviteCodeFetchState()
    data class Error(val message: String) : InviteCodeFetchState()
}

sealed class ServerSetupState {
    object Idle : ServerSetupState()
    object Loading : ServerSetupState()
    object Done : ServerSetupState()
    data class Error(val message: String) : ServerSetupState()
}

// ── Domain models ────────────────────────────────────────────────────────────

enum class UserRole { PARENT, CHILD }

data class UserState(
    val uid: String,
    val email: String,
    val nickname: String,
    val role: UserRole,
    val familyId: String?,
    val fcmToken: String? = null
)

data class StockItem(
    val ticker: String,
    val name: String,
    val currentPrice: Long,
    val rate: Double
)

data class HoldingStock(
    val ticker: String,
    val name: String,
    val quantity: Int,
    val averagePrice: Double
)

enum class RequestStatus { PENDING, ACCEPTED, COMPLETED, FAILED }

data class TransactionRequest(
    val id: String,
    val childUid: String,
    val childNickname: String,
    val type: String,
    val ticker: String,
    val name: String,
    val requestPrice: Long,
    val requestQuantity: Int,
    val memo: String,
    val status: RequestStatus,
    val timestamp: Long,
    val actualPrice: Long = 0L,
    val actualQuantity: Int = 0
)

data class FeeSettings(
    val brokerFeeRate: Double = 0.0,
    val tradeTaxRate: Double = 0.0
)

// ── ViewModel ────────────────────────────────────────────────────────────────

class FamilyStockViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()

    /**
     * 동적 Firestore 인스턴스.
     * MainActivity.onCreate 에서 familyApp이 초기화된 시점부터 자동으로 커스텀 Firebase를 반환.
     */
    private val db: FirebaseFirestore
        get() = FirebaseConfigManager.getFamilyDb(getApplication())

    private val listeners = mutableListOf<ListenerRegistration>()
    private var logoutAuthListener: FirebaseAuth.AuthStateListener? = null

    // ── Auth state ───────────────────────────────────────────────────────────

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _currentUser = MutableStateFlow<UserState?>(null)
    val currentUser: StateFlow<UserState?> = _currentUser.asStateFlow()

    // ── Family data ──────────────────────────────────────────────────────────

    /** uid → cash balance */
    private val _childAssets = MutableStateFlow<Map<String, Long>>(emptyMap())
    val childAssets: StateFlow<Map<String, Long>> = _childAssets.asStateFlow()

    /** uid → holding stocks */
    private val _childStocks = MutableStateFlow<Map<String, List<HoldingStock>>>(emptyMap())
    val childStocks: StateFlow<Map<String, List<HoldingStock>>> = _childStocks.asStateFlow()

    private val _familyWatchList = MutableStateFlow<List<String>>(emptyList())
    val familyWatchList: StateFlow<List<String>> = _familyWatchList.asStateFlow()

    private val _transactionRequests = MutableStateFlow<List<TransactionRequest>>(emptyList())
    val transactionRequests: StateFlow<List<TransactionRequest>> = _transactionRequests.asStateFlow()

    private val _feeSettings = MutableStateFlow(FeeSettings())
    val feeSettings: StateFlow<FeeSettings> = _feeSettings.asStateFlow()

    /** uid → nickname */
    private val _childNicknames = MutableStateFlow<Map<String, String>>(emptyMap())
    val childNicknames: StateFlow<Map<String, String>> = _childNicknames.asStateFlow()

    private val _activeChildUid = MutableStateFlow<String?>(null)
    val activeChildUid: StateFlow<String?> = _activeChildUid.asStateFlow()

    private val _searchedParent = MutableStateFlow<UserState?>(null)
    val searchedParent: StateFlow<UserState?> = _searchedParent.asStateFlow()

    private val _pendingChildren = MutableStateFlow<List<Map<String, String>>>(emptyList())
    val pendingChildren: StateFlow<List<Map<String, String>>> = _pendingChildren.asStateFlow()

    // ── Server setup / invite code ───────────────────────────────────────────

    private val _inviteCodeFetchState = MutableStateFlow<InviteCodeFetchState>(InviteCodeFetchState.Idle)
    val inviteCodeFetchState: StateFlow<InviteCodeFetchState> = _inviteCodeFetchState.asStateFlow()

    private val _serverSetupState = MutableStateFlow<ServerSetupState>(ServerSetupState.Idle)
    val serverSetupState: StateFlow<ServerSetupState> = _serverSetupState.asStateFlow()

    /** 부모에게 표시할 6자리 초대코드 */
    private val _inviteCode = MutableStateFlow<String?>(null)
    val inviteCode: StateFlow<String?> = _inviteCode.asStateFlow()

    /** true = checkParentExists() 완료 (결과에 관계없이) */
    private val _parentExistsCheckDone = MutableStateFlow(false)
    val parentExistsCheckDone: StateFlow<Boolean> = _parentExistsCheckDone.asStateFlow()

    // ── Static market stub ───────────────────────────────────────────────────

    val marketIndex: Map<String, StockItem> = mapOf(
        "005930" to StockItem("005930", "삼성전자",   79_500L,  1.23),
        "035720" to StockItem("035720", "카카오",     55_000L,  0.34),
        "AAPL"   to StockItem("AAPL",   "애플",      243_000L, 0.85),
        "000660" to StockItem("000660", "SK하이닉스", 168_000L, 2.45),
        "035420" to StockItem("035420", "NAVER",     192_000L,-0.78),
        "005380" to StockItem("005380", "현대차",    215_000L, -1.12)
    )

    // ── 앱 시작 시 기존 세션 자동 복원 ──────────────────────────────────────

    init {
        _inviteCode.value = FirebaseConfigManager.loadInviteCode(getApplication())
        val cached = auth.currentUser
        if (cached != null) checkExistingSession(cached)
        logoutAuthListener = FirebaseAuth.AuthStateListener { fbAuth ->
            if (fbAuth.currentUser == null) {
                val state = _authState.value
                if (state !is AuthState.Idle && state !is AuthState.Loading) {
                    clearListeners()
                    _currentUser.value         = null
                    _childAssets.value         = emptyMap()
                    _childStocks.value         = emptyMap()
                    _familyWatchList.value     = emptyList()
                    _transactionRequests.value = emptyList()
                    _childNicknames.value      = emptyMap()
                    _activeChildUid.value      = null
                    _searchedParent.value      = null
                    _pendingChildren.value     = emptyList()
                    _authState.value           = AuthState.Idle
                }
            }
        }
        auth.addAuthStateListener(logoutAuthListener!!)
    }

    private fun checkExistingSession(firebaseUser: com.google.firebase.auth.FirebaseUser) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val snap = db.collection("users").document(firebaseUser.uid).get().await()
                // role 미설정 문서(신규 유저 또는 AppViewModel.refreshFcmToken 경쟁 생성 stub)는
                // 미가입 상태로 취급하여 반드시 프로필 설정 화면(Frame 1)으로 보냄
                if (!snap.exists() || snap.getString("role").isNullOrBlank()) {
                    _authState.value = AuthState.NeedsProfileSetup
                    return@launch
                }
                val user = snap.toUserState()
                _currentUser.value = user
                when {
                    user.role == UserRole.CHILD && user.familyId == null ->
                        _authState.value = AuthState.PendingConnection
                    else -> {
                        if (user.familyId != null) subscribeToFamilyData(user.familyId)
                        subscribeToUserDoc(user.uid)
                        _authState.value = AuthState.LoggedIn(user)
                    }
                }
            } catch (_: Exception) {
                _authState.value = AuthState.Idle
            }
        }
    }

    // ── Google Sign-In ───────────────────────────────────────────────────────

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val result = auth.signInWithCredential(credential).await()
                val firebaseUser = result.user ?: throw IllegalStateException("Auth user is null")

                updateFcmToken(firebaseUser.uid)

                val snap = db.collection("users").document(firebaseUser.uid).get().await()
                // role 미설정 문서는 신규 유저로 취급 (AppViewModel stub 경쟁 생성 방어)
                if (!snap.exists() || snap.getString("role").isNullOrBlank()) {
                    _authState.value = AuthState.NeedsProfileSetup
                    return@launch
                }

                val user = snap.toUserState()
                _currentUser.value = user
                when {
                    user.role == UserRole.CHILD && user.familyId == null ->
                        _authState.value = AuthState.PendingConnection
                    else -> {
                        if (user.familyId != null) subscribeToFamilyData(user.familyId)
                        subscribeToUserDoc(user.uid)
                        _authState.value = AuthState.LoggedIn(user)
                    }
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(networkSafeMessage(e))
            }
        }
    }

    // ── 역할 결정 후 부모 존재 여부 확인 (ProfileSetupScreen 에서 호출) ────────

    /**
     * 현재 연결된 Firebase의 users 컬렉션에서 PARENT 계정이 이미 있는지 확인.
     * - 있으면: authState = ServerSetupRequired
     * - 없으면: authState 를 NeedsProfileSetup 으로 유지 (변경 없음)
     */
    fun checkParentExists() {
        _parentExistsCheckDone.value = false
        viewModelScope.launch {
            try {
                val result = db.collection("users")
                    .whereEqualTo("role", "PARENT")
                    .limit(1)
                    .get().await()
                if (!result.isEmpty) {
                    _authState.value = AuthState.ServerSetupRequired
                }
            } catch (e: Exception) {
                _errorMessage.value = "서버 확인 실패: ${e.message}"
            } finally {
                _parentExistsCheckDone.value = true
            }
        }
    }

    // ── 독립 서버 개설 (부모) ─────────────────────────────────────────────────

    /**
     * 1. 4종 키를 SharedPreferences 저장
     * 2. familyApp FirebaseApp 즉시 초기화 (이후 db getter가 커스텀 반환)
     * 3. 6자리 초대코드 생성 → 개발자 DEFAULT Firebase global_invites 에 저장
     * 4. 초대코드 로컬 저장 + _inviteCode 업데이트
     * 5. serverSetupState = Done → UI가 createParentProfile() 호출
     */
    fun generateAndSaveInviteCode(config: FirebaseCustomConfig) {
        _serverSetupState.value = ServerSetupState.Loading
        viewModelScope.launch {
            try {
                FirebaseConfigManager.saveConfig(getApplication(), config)
                FirebaseConfigManager.initCustomFirebaseApp(getApplication())

                val code = "%06d".format(Random.nextInt(1_000_000))

                FirebaseConfigManager.getDefaultDb()
                    .collection("global_invites")
                    .document(code)
                    .set(mapOf(
                        "apiKey"      to config.apiKey,
                        "appId"       to config.appId,
                        "projectId"   to config.projectId,
                        "gcmSenderId" to config.gcmSenderId,
                        "createdAt"   to System.currentTimeMillis()
                    )).await()

                FirebaseConfigManager.saveInviteCode(getApplication(), code)
                _inviteCode.value = code
                _serverSetupState.value = ServerSetupState.Done
            } catch (e: Exception) {
                _serverSetupState.value = ServerSetupState.Error(networkSafeMessage(e))
            }
        }
    }

    // ── 초대코드로 부모 서버 연결 (자녀) ────────────────────────────────────

    /**
     * 개발자 DEFAULT Firebase global_invites/{code} 에서 4종 키를 읽어
     * familyApp 을 즉시 초기화한다.
     * 성공 시 InviteCodeFetchState.Success → UI가 createChildProfile() 호출.
     */
    fun fetchKeysFromInviteCode(code: String) {
        if (code.length != 6 || code.any { !it.isDigit() }) {
            _inviteCodeFetchState.value = InviteCodeFetchState.Error("6자리 숫자 코드를 입력해 주세요.")
            return
        }
        _inviteCodeFetchState.value = InviteCodeFetchState.Loading
        viewModelScope.launch {
            try {
                val doc = FirebaseConfigManager.getDefaultDb()
                    .collection("global_invites")
                    .document(code)
                    .get().await()

                if (!doc.exists()) {
                    _inviteCodeFetchState.value = InviteCodeFetchState.Error("존재하지 않는 초대 코드입니다.")
                    return@launch
                }

                val config = FirebaseCustomConfig(
                    apiKey      = doc.getString("apiKey")      ?: "",
                    appId       = doc.getString("appId")       ?: "",
                    projectId   = doc.getString("projectId")   ?: "",
                    gcmSenderId = doc.getString("gcmSenderId") ?: ""
                )
                if (config.apiKey.isBlank()) {
                    _inviteCodeFetchState.value = InviteCodeFetchState.Error("유효하지 않은 서버 정보입니다.")
                    return@launch
                }

                FirebaseConfigManager.saveConfig(getApplication(), config)
                FirebaseConfigManager.initCustomFirebaseApp(getApplication())
                _inviteCodeFetchState.value = InviteCodeFetchState.Success(config)
            } catch (e: Exception) {
                _inviteCodeFetchState.value = InviteCodeFetchState.Error(networkSafeMessage(e))
            }
        }
    }

    // ── 프로필 생성 ──────────────────────────────────────────────────────────

    fun createParentProfile(nickname: String) {
        val firebaseUser = auth.currentUser ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val familyId = UUID.randomUUID().toString()
                val userData = mapOf(
                    "uid"      to firebaseUser.uid,
                    "email"    to (firebaseUser.email ?: ""),
                    "nickname" to nickname,
                    "role"     to "PARENT",
                    "familyId" to familyId
                )
                val familyData = mapOf(
                    "adminUid"        to firebaseUser.uid,
                    "familyCash"      to emptyMap<String, Long>(),
                    "pendingChildren" to emptyList<Map<String, String>>(),
                    "watchlist"       to emptyList<String>(),
                    "feeSettings"     to mapOf("brokerFeeRate" to 0.0, "tradeTaxRate" to 0.0)
                )
                db.collection("users").document(firebaseUser.uid).set(userData).await()
                db.collection("families").document(familyId).set(familyData).await()
                // 부모 본인도 플레이어이므로 childAssets 문서를 초기화
                db.collection("families").document(familyId)
                    .collection("childAssets").document(firebaseUser.uid)
                    .set(mapOf("balance" to 0L, "holdingStocks" to emptyList<Any>())).await()

                // 커스텀 Firebase 없이 직접 시작한 경우: 기본 Firebase 설정으로 초대코드 생성
                // (커스텀 경로는 generateAndSaveInviteCode()에서 이미 생성됨)
                if (_inviteCode.value == null) {
                    runCatching {
                        val opts = FirebaseApp.getInstance().options
                        val config = FirebaseCustomConfig(
                            apiKey      = opts.apiKey      ?: "",
                            appId       = opts.applicationId,
                            projectId   = opts.projectId   ?: "",
                            gcmSenderId = opts.gcmSenderId ?: ""
                        )
                        val code = "%06d".format(Random.nextInt(1_000_000))
                        FirebaseConfigManager.getDefaultDb()
                            .collection("global_invites")
                            .document(code)
                            .set(mapOf(
                                "apiKey"      to config.apiKey,
                                "appId"       to config.appId,
                                "projectId"   to config.projectId,
                                "gcmSenderId" to config.gcmSenderId,
                                "createdAt"   to System.currentTimeMillis()
                            )).await()
                        FirebaseConfigManager.saveInviteCode(getApplication(), code)
                        _inviteCode.value = code
                    }
                }

                val user = UserState(
                    uid      = firebaseUser.uid,
                    email    = firebaseUser.email ?: "",
                    nickname = nickname,
                    role     = UserRole.PARENT,
                    familyId = familyId
                )
                _currentUser.value = user
                subscribeToFamilyData(familyId)
                subscribeToUserDoc(firebaseUser.uid)
                _authState.value = AuthState.LoggedIn(user)
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createChildProfile(nickname: String) {
        val firebaseUser = auth.currentUser ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val userData = mapOf(
                    "uid"      to firebaseUser.uid,
                    "email"    to (firebaseUser.email ?: ""),
                    "nickname" to nickname,
                    "role"     to "CHILD",
                    "familyId" to null
                )
                db.collection("users").document(firebaseUser.uid).set(userData).await()

                val user = UserState(
                    uid      = firebaseUser.uid,
                    email    = firebaseUser.email ?: "",
                    nickname = nickname,
                    role     = UserRole.CHILD,
                    familyId = null
                )
                _currentUser.value = user

                // 부모의 familyId를 찾아 연결 요청을 pendingChildren에 자동 등록
                // 실패해도 PendingConnection 화면에서 재시도 가능하므로 silent-fail
                runCatching {
                    val parentSnap = db.collection("users")
                        .whereEqualTo("role", "PARENT")
                        .limit(1)
                        .get().await()
                    val parentFamilyId = parentSnap.documents.firstOrNull()?.getString("familyId")
                    if (parentFamilyId != null) {
                        val entry = mapOf(
                            "uid"      to firebaseUser.uid,
                            "email"    to (firebaseUser.email ?: ""),
                            "nickname" to nickname
                        )
                        db.collection("families").document(parentFamilyId)
                            .update("pendingChildren", FieldValue.arrayUnion(entry)).await()
                    }
                }

                subscribeToUserDoc(firebaseUser.uid)   // familyId null→값 변화 감지 → LoggedIn 자동 전환
                _authState.value = AuthState.PendingConnection
            } catch (e: Exception) {
                _errorMessage.value = networkSafeMessage(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ── 부모 검색 / 연결 요청 ────────────────────────────────────────────────

    fun searchParentByEmail(email: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val snap = db.collection("users")
                    .whereEqualTo("email", email)
                    .whereEqualTo("role", "PARENT")
                    .get().await()
                if (snap.isEmpty) {
                    // 이메일이 없거나 CHILD 역할인 경우 모두 차단
                    _errorMessage.value = "존재하지 않거나 부모로 등록되지 않은 이메일입니다."
                    _searchedParent.value = null
                } else {
                    _searchedParent.value = snap.documents.first().toUserState()
                }
            } catch (e: Exception) {
                _errorMessage.value = networkSafeMessage(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun sendConnectionRequest(parentFamilyId: String) {
        val child = _currentUser.value ?: return
        viewModelScope.launch {
            try {
                val entry = mapOf("uid" to child.uid, "email" to child.email, "nickname" to child.nickname)
                db.collection("families").document(parentFamilyId)
                    .update("pendingChildren", FieldValue.arrayUnion(entry)).await()
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }

    fun approveChild(childUid: String, childEmail: String, childNickname: String) {
        val familyId = _currentUser.value?.familyId ?: return
        viewModelScope.launch {
            try {
                db.runTransaction { tx ->
                    val familyRef = db.collection("families").document(familyId)
                    val snap = tx.get(familyRef)
                    @Suppress("UNCHECKED_CAST")
                    val pending = (snap.get("pendingChildren") as? List<Map<String, String>>) ?: emptyList()
                    tx.update(familyRef, "pendingChildren", pending.filter { it["uid"] != childUid })
                }.await()
                db.collection("users").document(childUid).update("familyId", familyId).await()
                db.collection("families").document(familyId)
                    .collection("childAssets").document(childUid)
                    .set(mapOf("balance" to 0L, "holdingStocks" to emptyList<Any>())).await()
                _childNicknames.value = _childNicknames.value + (childUid to childNickname)
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }

    fun rejectChild(childUid: String) {
        val familyId = _currentUser.value?.familyId ?: return
        viewModelScope.launch {
            try {
                db.runTransaction { tx ->
                    val familyRef = db.collection("families").document(familyId)
                    val snap = tx.get(familyRef)
                    @Suppress("UNCHECKED_CAST")
                    val pending = (snap.get("pendingChildren") as? List<Map<String, String>>) ?: emptyList()
                    tx.update(familyRef, "pendingChildren", pending.filter { it["uid"] != childUid })
                }.await()
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }

    fun signOut() {
        clearListeners()
        auth.signOut()
        _currentUser.value         = null
        _authState.value           = AuthState.Idle
        _childAssets.value         = emptyMap()
        _childStocks.value         = emptyMap()
        _familyWatchList.value     = emptyList()
        _transactionRequests.value = emptyList()
        _childNicknames.value      = emptyMap()
        _activeChildUid.value      = null
        _searchedParent.value      = null
        _pendingChildren.value     = emptyList()
        _parentExistsCheckDone.value = false
    }

    // ── Firestore 실시간 구독 ─────────────────────────────────────────────────

    private fun subscribeToFamilyData(familyId: String) {
        db.collection("families").document(familyId)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                @Suppress("UNCHECKED_CAST")
                _familyWatchList.value = (snap.get("watchlist") as? List<String>) ?: emptyList()
                val fee = snap.get("feeSettings")
                if (fee is Map<*, *>) {
                    _feeSettings.value = FeeSettings(
                        brokerFeeRate = (fee["brokerFeeRate"] as? Double) ?: 0.0,
                        tradeTaxRate  = (fee["tradeTaxRate"]  as? Double) ?: 0.0
                    )
                }
                @Suppress("UNCHECKED_CAST")
                _pendingChildren.value = (snap.get("pendingChildren") as? List<Map<String, String>>) ?: emptyList()
            }.also { listeners.add(it) }

        db.collection("families").document(familyId).collection("childAssets")
            .addSnapshotListener { snaps, _ ->
                if (snaps == null) return@addSnapshotListener
                val balances = mutableMapOf<String, Long>()
                val stocks   = mutableMapOf<String, List<HoldingStock>>()
                for (doc in snaps.documents) {
                    balances[doc.id] = doc.getLong("balance") ?: 0L
                    @Suppress("UNCHECKED_CAST")
                    stocks[doc.id] = ((doc.get("holdingStocks") as? List<Map<String, Any>>) ?: emptyList())
                        .map { it.toHoldingStock() }
                }
                _childAssets.value = balances
                _childStocks.value = stocks
            }.also { listeners.add(it) }

        db.collection("families").document(familyId).collection("transactionRequests")
            .orderBy("timestamp", Query.Direction.DESCENDING).limit(200)
            .addSnapshotListener { snaps, _ ->
                if (snaps == null) return@addSnapshotListener
                _transactionRequests.value = snaps.documents.mapNotNull { doc ->
                    runCatching { doc.toTransactionRequest() }.getOrNull()
                }
            }.also { listeners.add(it) }

        db.collection("users").whereEqualTo("familyId", familyId).whereEqualTo("role", "CHILD")
            .addSnapshotListener { snaps, _ ->
                if (snaps == null) return@addSnapshotListener
                _childNicknames.value = snaps.documents.associate { it.id to (it.getString("nickname") ?: "") }
            }.also { listeners.add(it) }
    }

    /**
     * 자신의 user doc를 감시.
     * CHILD: familyId가 null → 값으로 바뀌면 자동으로 LoggedIn 전환.
     */
    private fun subscribeToUserDoc(uid: String) {
        db.collection("users").document(uid)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val familyId = snap.getString("familyId")
                val current  = _currentUser.value ?: return@addSnapshotListener
                if (familyId != null && current.familyId == null) {
                    val updated = current.copy(familyId = familyId)
                    _currentUser.value = updated
                    subscribeToFamilyData(familyId)
                    _authState.value = AuthState.LoggedIn(updated)
                }
            }.also { listeners.add(it) }
    }

    // ── FCM ──────────────────────────────────────────────────────────────────

    private fun updateFcmToken(uid: String) {
        viewModelScope.launch {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                db.collection("users").document(uid).update("fcmToken", token).await()
            } catch (_: Exception) {}
        }
    }

    // ── 부모 관리 기능 ────────────────────────────────────────────────────────

    fun setActiveChild(uid: String?) { _activeChildUid.value = uid }

    fun updateChildAssetBalance(childUid: String, newBalance: Long) {
        val familyId = _currentUser.value?.familyId ?: return
        viewModelScope.launch {
            try {
                db.collection("families").document(familyId)
                    .collection("childAssets").document(childUid)
                    .update("balance", newBalance).await()
            } catch (e: Exception) { _errorMessage.value = e.message }
        }
    }

    fun setChildInitialStock(childUid: String, ticker: String, name: String, quantity: Int, averagePrice: Double) {
        val familyId = _currentUser.value?.familyId ?: return
        viewModelScope.launch {
            try {
                val assetRef = db.collection("families").document(familyId)
                    .collection("childAssets").document(childUid)
                db.runTransaction { tx ->
                    val snap = tx.get(assetRef)
                    @Suppress("UNCHECKED_CAST")
                    val current = (snap.get("holdingStocks") as? List<Map<String, Any>>)
                        ?.toMutableList() ?: mutableListOf()
                    val entry = mapOf("ticker" to ticker, "name" to name, "quantity" to quantity, "averagePrice" to averagePrice)
                    val idx = current.indexOfFirst { it["ticker"] == ticker }
                    if (idx >= 0) current[idx] = entry else current.add(entry)
                    tx.update(assetRef, "holdingStocks", current)
                }.await()
            } catch (e: Exception) { _errorMessage.value = e.message }
        }
    }

    fun removeChildInitialStock(childUid: String, ticker: String) {
        val familyId = _currentUser.value?.familyId ?: return
        viewModelScope.launch {
            try {
                val assetRef = db.collection("families").document(familyId)
                    .collection("childAssets").document(childUid)
                db.runTransaction { tx ->
                    val snap = tx.get(assetRef)
                    @Suppress("UNCHECKED_CAST")
                    val updated = (snap.get("holdingStocks") as? List<Map<String, Any>>)
                        ?.filter { it["ticker"] != ticker } ?: emptyList()
                    tx.update(assetRef, "holdingStocks", updated)
                }.await()
            } catch (e: Exception) { _errorMessage.value = e.message }
        }
    }

    fun updateFeeSettings(brokerFeeRate: Double, tradeTaxRate: Double) {
        val familyId = _currentUser.value?.familyId ?: return
        viewModelScope.launch {
            try {
                db.collection("families").document(familyId)
                    .update("feeSettings", mapOf("brokerFeeRate" to brokerFeeRate, "tradeTaxRate" to tradeTaxRate)).await()
            } catch (e: Exception) { _errorMessage.value = e.message }
        }
    }

    fun updateFirebaseConfig(config: FirebaseCustomConfig, onNeedRestart: () -> Unit) {
        viewModelScope.launch {
            try {
                FirebaseConfigManager.saveConfig(getApplication(), config)
                onNeedRestart()
            } catch (e: Exception) { _errorMessage.value = e.message }
        }
    }

    // ── 관심 종목 ────────────────────────────────────────────────────────────

    fun toggleWatchList(ticker: String) {
        val familyId = _currentUser.value?.familyId ?: return
        val ref = db.collection("families").document(familyId)
        val isWatched = _familyWatchList.value.contains(ticker)
        viewModelScope.launch {
            try {
                if (isWatched) ref.update("watchlist", FieldValue.arrayRemove(ticker)).await()
                else           ref.update("watchlist", FieldValue.arrayUnion(ticker)).await()
            } catch (e: Exception) { _errorMessage.value = e.message }
        }
    }

    // ── 거래 요청 (자녀) ──────────────────────────────────────────────────────

    fun submitTransactionRequest(type: String, ticker: String, requestPrice: Long, requestQuantity: Int, memo: String) {
        val user     = _currentUser.value ?: return
        val familyId = user.familyId ?: run { _errorMessage.value = "가족에 연결되지 않았습니다."; return }
        val stock    = marketIndex[ticker] ?: run { _errorMessage.value = "종목 정보를 찾을 수 없습니다."; return }

        if (type == "BUY") {
            if ((_childAssets.value[user.uid] ?: 0L) < requestPrice * requestQuantity) {
                _errorMessage.value = "잔고가 부족합니다."; return
            }
        } else {
            if ((_childStocks.value[user.uid]?.firstOrNull { it.ticker == ticker }?.quantity ?: 0) < requestQuantity) {
                _errorMessage.value = "보유 수량이 부족합니다."; return
            }
        }

        viewModelScope.launch {
            try {
                val id = UUID.randomUUID().toString()
                db.collection("families").document(familyId).collection("transactionRequests").document(id)
                    .set(mapOf(
                        "id" to id, "childUid" to user.uid, "childNickname" to user.nickname,
                        "type" to type, "ticker" to ticker, "name" to stock.name,
                        "requestPrice" to requestPrice, "requestQuantity" to requestQuantity,
                        "memo" to memo, "status" to "PENDING",
                        "timestamp" to System.currentTimeMillis(), "actualPrice" to 0L, "actualQuantity" to 0
                    )).await()
            } catch (e: Exception) { _errorMessage.value = e.message }
        }
    }

    // ── 거래 처리 (부모) ──────────────────────────────────────────────────────

    fun processToAccepted(requestId: String) {
        val familyId = _currentUser.value?.familyId ?: return
        viewModelScope.launch {
            try {
                db.collection("families").document(familyId)
                    .collection("transactionRequests").document(requestId)
                    .update("status", "ACCEPTED").await()
            } catch (e: Exception) { _errorMessage.value = e.message }
        }
    }

    fun completeTransaction(requestId: String, actualPrice: Long, actualQuantity: Int) {
        val familyId = _currentUser.value?.familyId ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val fee = _feeSettings.value
                val requestRef = db.collection("families").document(familyId)
                    .collection("transactionRequests").document(requestId)

                db.runTransaction { tx ->
                    val requestSnap = tx.get(requestRef)
                    check(requestSnap.getString("status") == "ACCEPTED") { "status 가 ACCEPTED 가 아닙니다." }

                    val childUid        = requestSnap.getString("childUid")!!
                    val type            = requestSnap.getString("type")!!
                    val ticker          = requestSnap.getString("ticker")!!
                    val name            = requestSnap.getString("name") ?: ""
                    val requestQuantity = (requestSnap.getLong("requestQuantity") ?: 0L).toInt()
                    val childNickname   = requestSnap.getString("childNickname") ?: ""
                    val memo            = requestSnap.getString("memo") ?: ""

                    val assetRef  = db.collection("families").document(familyId).collection("childAssets").document(childUid)
                    val assetSnap = tx.get(assetRef)
                    val balance   = assetSnap.getLong("balance") ?: 0L
                    @Suppress("UNCHECKED_CAST")
                    val holdings  = (assetSnap.get("holdingStocks") as? List<Map<String, Any>>)?.toMutableList() ?: mutableListOf()

                    val rate = (fee.brokerFeeRate + fee.tradeTaxRate) / 100.0
                    val newBalance: Long
                    val updatedStocks: List<Map<String, Any>>

                    if (type == "BUY") {
                        val totalCost = (actualPrice * actualQuantity * (1.0 + rate)).toLong()
                        check(balance >= totalCost) { "잔고 부족" }
                        val existing = holdings.firstOrNull { it["ticker"] == ticker }
                        val existQty = (existing?.get("quantity") as? Long)?.toInt() ?: 0
                        val existAvg = (existing?.get("averagePrice") as? Double) ?: 0.0
                        val newQty   = existQty + actualQuantity
                        val newAvg   = if (existQty == 0) totalCost.toDouble() / actualQuantity else (existAvg * existQty + totalCost) / newQty
                        val mutable  = holdings.toMutableList()
                        val idx      = mutable.indexOfFirst { it["ticker"] == ticker }
                        val entry    = mapOf("ticker" to ticker, "name" to name, "quantity" to newQty, "averagePrice" to newAvg)
                        if (idx >= 0) mutable[idx] = entry else mutable.add(entry)
                        newBalance    = balance - totalCost
                        updatedStocks = mutable
                    } else {
                        val netProceed = (actualPrice * actualQuantity * (1.0 - rate)).toLong()
                        val existing   = holdings.firstOrNull { it["ticker"] == ticker }
                        val existQty   = (existing?.get("quantity") as? Long)?.toInt() ?: 0
                        check(existQty >= actualQuantity) { "보유 수량 부족" }
                        val mutable    = holdings.toMutableList()
                        val idx        = mutable.indexOfFirst { it["ticker"] == ticker }
                        if (idx >= 0) {
                            val remainQty = existQty - actualQuantity
                            if (remainQty == 0) mutable.removeAt(idx)
                            else mutable[idx] = (existing!! + mapOf("quantity" to remainQty))
                        }
                        newBalance    = balance + netProceed
                        updatedStocks = mutable
                    }

                    tx.update(requestRef, mapOf("status" to "COMPLETED", "actualPrice" to actualPrice, "actualQuantity" to actualQuantity))
                    tx.update(assetRef, mapOf("balance" to newBalance, "holdingStocks" to updatedStocks))

                    val remaining = requestQuantity - actualQuantity
                    if (remaining > 0) {
                        val newId  = UUID.randomUUID().toString()
                        val newRef = db.collection("families").document(familyId).collection("transactionRequests").document(newId)
                        tx.set(newRef, mapOf(
                            "id" to newId, "childUid" to childUid, "childNickname" to childNickname,
                            "type" to type, "ticker" to ticker, "name" to name,
                            "requestPrice" to actualPrice, "requestQuantity" to remaining,
                            "memo" to memo, "status" to "PENDING",
                            "timestamp" to System.currentTimeMillis(), "actualPrice" to 0L, "actualQuantity" to 0
                        ))
                    }
                }.await()
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun failTransaction(requestId: String) {
        val familyId = _currentUser.value?.familyId ?: return
        viewModelScope.launch {
            try {
                db.collection("families").document(familyId)
                    .collection("transactionRequests").document(requestId)
                    .update("status", "FAILED").await()
            } catch (e: Exception) { _errorMessage.value = e.message }
        }
    }

    // ── State reset helpers ───────────────────────────────────────────────────

    fun clearError()          { _errorMessage.value = null }
    fun resetInviteCodeFetch(){ _inviteCodeFetchState.value = InviteCodeFetchState.Idle }
    fun resetServerSetup()    { _serverSetupState.value = ServerSetupState.Idle }

    /** [DEBUG] PendingConnection 상태를 강제로 LoggedIn으로 전환 (부모 승인 우회) */
    fun debugForceApprove() {
        val user = _currentUser.value ?: return
        _authState.value = AuthState.LoggedIn(user)
    }

    // ── 네트워크 에러 메시지 분류 헬퍼 ──────────────────────────────────────

    private fun networkSafeMessage(e: Exception) = when {
        e is com.google.firebase.FirebaseNetworkException ||
        e.cause is java.net.UnknownHostException ||
        e.cause is java.net.SocketTimeoutException ->
            "네트워크 연결을 확인해 주세요."
        else -> e.message ?: "알 수 없는 오류가 발생했습니다."
    }

    private fun clearListeners() {
        listeners.forEach { it.remove() }
        listeners.clear()
    }

    override fun onCleared() {
        super.onCleared()
        logoutAuthListener?.let { auth.removeAuthStateListener(it) }
        clearListeners()
    }

    // ── DocumentSnapshot / Map 변환 헬퍼 ────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any>.toHoldingStock() = HoldingStock(
        ticker       = this["ticker"] as? String ?: "",
        name         = this["name"]   as? String ?: "",
        quantity     = (this["quantity"] as? Long)?.toInt() ?: 0,
        averagePrice = this["averagePrice"] as? Double ?: 0.0
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toUserState(): UserState {
        val roleStr = getString("role") ?: "CHILD"
        return UserState(
            uid      = id,
            email    = getString("email")    ?: "",
            nickname = getString("nickname") ?: "",
            role     = if (roleStr == "PARENT") UserRole.PARENT else UserRole.CHILD,
            familyId = getString("familyId"),
            fcmToken = getString("fcmToken")
        )
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toTransactionRequest(): TransactionRequest {
        val status = when (getString("status")) {
            "ACCEPTED"  -> RequestStatus.ACCEPTED
            "COMPLETED" -> RequestStatus.COMPLETED
            "FAILED"    -> RequestStatus.FAILED
            else        -> RequestStatus.PENDING
        }
        return TransactionRequest(
            id              = id,
            childUid        = getString("childUid")      ?: "",
            childNickname   = getString("childNickname") ?: "",
            type            = getString("type")          ?: "BUY",
            ticker          = getString("ticker")        ?: "",
            name            = getString("name")          ?: "",
            requestPrice    = getLong("requestPrice")    ?: 0L,
            requestQuantity = (getLong("requestQuantity") ?: 0L).toInt(),
            memo            = getString("memo")          ?: "",
            status          = status,
            timestamp       = getLong("timestamp")       ?: 0L,
            actualPrice     = getLong("actualPrice")     ?: 0L,
            actualQuantity  = (getLong("actualQuantity") ?: 0L).toInt()
        )
    }
}
