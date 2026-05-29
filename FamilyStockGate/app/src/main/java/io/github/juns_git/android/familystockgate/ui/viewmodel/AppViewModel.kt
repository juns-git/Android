package io.github.juns_git.android.familystockgate.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import io.github.juns_git.android.familystockgate.data.model.FamilyData
import io.github.juns_git.android.familystockgate.data.model.HoldingItem
import io.github.juns_git.android.familystockgate.data.model.LeaderboardEntry
import io.github.juns_git.android.familystockgate.data.model.StockItem
import io.github.juns_git.android.familystockgate.data.model.TradeRequest
import io.github.juns_git.android.familystockgate.data.model.TradeStatus
import io.github.juns_git.android.familystockgate.data.model.TradeType
import io.github.juns_git.android.familystockgate.data.model.UserData
import io.github.juns_git.android.familystockgate.data.model.UserRole
import io.github.juns_git.android.familystockgate.data.local.StockMasterRepository
import io.github.juns_git.android.familystockgate.data.remote.RetrofitClient
import io.github.juns_git.android.familystockgate.data.remote.StockItemResponse
import io.github.juns_git.android.familystockgate.utils.FirebaseConfigManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

private const val KRX_API_KEY = "7595edc97b238f5cb7410b243ea55eea09592fa9a872d7fe016e6c1add100071"

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()

    // 가족 데이터용 동적 Firestore (커스텀 앱 초기화 후 자동 전환)
    private val db: FirebaseFirestore
        get() = FirebaseConfigManager.getFamilyDb(getApplication())

    // Cloud Functions: 개발자 DEFAULT 프로젝트에서 FCM 발송 처리
    private val functions = FirebaseFunctions.getInstance()

    // ── 리스너 관리 ──────────────────────────────────────────────────────────

    private var authStateListener: FirebaseAuth.AuthStateListener? = null
    private var userDocListener: ListenerRegistration? = null
    private val familyListeners = mutableListOf<ListenerRegistration>()
    private var searchJob: Job? = null

    // ── 컨텍스트 캐시 (Firestore 쓰기 시 참조) ────────────────────────────────

    private var cachedUid: String? = null
    private var cachedFamilyId: String? = null
    private var cachedRole: UserRole = UserRole.CHILD
    private var cachedAdminUid: String? = null  // FCM 알림 대상 (부모)용

    // ── 리더보드 연산용 내부 버퍼 (Firestore 리스너 콜백 간 공유) ──────────────
    // key: childUid, value: Triple(balance, initialBudget, holdingStocks raw maps)
    private val lbAssets = mutableMapOf<String, Triple<Long, Long, List<Map<String, Any>>>>()
    private val lbNicknames = mutableMapOf<String, String>()

    // ── 마켓 시세 스텁 (실제 API 연동 전 더미) ────────────────────────────────

    private val marketPrices: Map<String, StockItem> = mapOf(
        "005930" to StockItem("005930", "삼성전자",          79_500L,  1.23),
        "000660" to StockItem("000660", "SK하이닉스",        165_000L, 2.45),
        "035420" to StockItem("035420", "NAVER",            195_000L,-0.78),
        "035720" to StockItem("035720", "카카오",             55_000L, 0.34),
        "005380" to StockItem("005380", "현대차",            215_000L,-1.12),
        "051910" to StockItem("051910", "LG화학",            320_000L, 0.63),
        "006400" to StockItem("006400", "삼성SDI",           280_000L,-0.54),
        "373220" to StockItem("373220", "LG에너지솔루션",    380_000L, 1.05),
        "207940" to StockItem("207940", "삼성바이오로직스",   920_000L, 0.22)
    )

    // ── StateFlows (UI 타입·이름 유지, 값은 Firestore에서 채움) ─────────────

    private val _currentUser = MutableStateFlow<UserData?>(null)
    val currentUser: StateFlow<UserData?> = _currentUser.asStateFlow()

    private val _familyData = MutableStateFlow<FamilyData?>(null)
    val familyData: StateFlow<FamilyData?> = _familyData.asStateFlow()

    private val _tradeRequests = MutableStateFlow<List<TradeRequest>>(emptyList())
    val tradeRequests: StateFlow<List<TradeRequest>> = _tradeRequests.asStateFlow()

    private val _availableCash = MutableStateFlow(0L)
    val availableCash: StateFlow<Long> = _availableCash.asStateFlow()

    private val _commissionRate = MutableStateFlow(0.015)
    val commissionRate: StateFlow<Double> = _commissionRate.asStateFlow()

    private val _taxRate = MutableStateFlow(0.18)
    val taxRate: StateFlow<Double> = _taxRate.asStateFlow()

    private val _watchlist = MutableStateFlow<List<StockItem>>(emptyList())
    val watchlist: StateFlow<List<StockItem>> = _watchlist.asStateFlow()

    private val _holdings = MutableStateFlow<List<HoldingItem>>(emptyList())
    val holdings: StateFlow<List<HoldingItem>> = _holdings.asStateFlow()

    private val _searchResults = MutableStateFlow<List<StockItem>>(emptyList())
    val searchResults: StateFlow<List<StockItem>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // ── 📦 로컬 마스터 캐시 관련 StateFlows ─────────────────────────────────

    private val _allStocksMasterList = MutableStateFlow<List<StockItem>>(emptyList())

    /** 부모의 [🔄 마스터 최신화] 버튼 로딩 여부 */
    private val _isMasterRefreshing = MutableStateFlow(false)
    val isMasterRefreshing: StateFlow<Boolean> = _isMasterRefreshing.asStateFlow()

    /** 최근 검색 종목 (양쪽 검색 화면 공용, max 5) */
    private val _recentSearches = MutableStateFlow<List<StockItem>>(emptyList())
    val recentSearches: StateFlow<List<StockItem>> = _recentSearches.asStateFlow()

    /** 관심 등록 / 초기 설정 시점에 단발 조회된 최신 시세 캐시 */
    private val _livePrices = MutableStateFlow<Map<String, StockItem>>(emptyMap())

    /** 10초 자동 가격 갱신 Job */
    private var priceRefreshJob: Job? = null

    /** 수동·자동 가격 조회 중 여부 */
    private val _isPriceRefreshing = MutableStateFlow(false)
    val isPriceRefreshing: StateFlow<Boolean> = _isPriceRefreshing.asStateFlow()

    /** API 조회 성공 시 실시간 가격 오버레이 (ticker → StockItem) */
    private val dynamicPrices = mutableMapOf<String, StockItem>()

    /** Firestore watchlist 리스너에서 캐시된 티커 목록 (가격 조회용) */
    private val cachedWatchlistTickers = mutableListOf<String>()

    private val _searchedParentUid = MutableStateFlow<String?>(null)
    val searchedParentUid: StateFlow<String?> = _searchedParentUid.asStateFlow()

    private val _debugRole = MutableStateFlow(UserRole.PARENT)
    val debugRole: StateFlow<UserRole> = _debugRole.asStateFlow()

    // ── 🔔 FCM 관련 StateFlows ────────────────────────────────────────────────

    /** 앱 레벨 FCM 수신 ON/OFF (Firestore users/{uid}.fcmEnabled 와 동기화) */
    private val _fcmEnabled = MutableStateFlow(true)
    val fcmEnabled: StateFlow<Boolean> = _fcmEnabled.asStateFlow()

    // ── 🏆 리더보드 StateFlow ─────────────────────────────────────────────────

    private val _leaderboard = MutableStateFlow<List<LeaderboardEntry>>(emptyList())
    val leaderboard: StateFlow<List<LeaderboardEntry>> = _leaderboard.asStateFlow()

    private val _pendingChildren = MutableStateFlow<List<Map<String, String>>>(emptyList())
    val pendingChildren: StateFlow<List<Map<String, String>>> = _pendingChildren.asStateFlow()

    /** 가족 전체 구성원 (uid to nickname): 부모 포함, 자산 관리 패널용 */
    private val _familyMembers = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val familyMembers: StateFlow<List<Pair<String, String>>> = _familyMembers.asStateFlow()

    /** 구성원별 예수금 (uid → balance) */
    private val _memberBalances = MutableStateFlow<Map<String, Long>>(emptyMap())
    val memberBalances: StateFlow<Map<String, Long>> = _memberBalances.asStateFlow()

    /** 구성원별 보유 종목 (uid → holdings) */
    private val _memberHoldings = MutableStateFlow<Map<String, List<HoldingItem>>>(emptyMap())
    val memberHoldings: StateFlow<Map<String, List<HoldingItem>>> = _memberHoldings.asStateFlow()

    /** [Frame 8-1] 포트폴리오 상세 화면에서 조회 중인 대상 UID */
    private val _portfolioTargetUid = MutableStateFlow("")
    val portfolioTargetUid: StateFlow<String> = _portfolioTargetUid.asStateFlow()

    // ── 초기화: FirebaseAuth 상태 감시 ──────────────────────────────────────

    init {
        // 앱 시작 즉시 로컬 마스터 데이터 로드 (Auth 완료 전에 검색 가능하도록)
        viewModelScope.launch { loadStockMasterData() }

        authStateListener = FirebaseAuth.AuthStateListener { fbAuth ->
            val user = fbAuth.currentUser
            if (user != null) {
                if (cachedUid != user.uid) {
                    cachedUid = user.uid
                    attachUserDocListener(user.uid)
                }
            } else {
                clearAll()
            }
        }
        auth.addAuthStateListener(authStateListener!!)
    }

    // users/{uid} 리스너: familyId + fcmEnabled 확보 후 구독 시작
    private fun attachUserDocListener(uid: String) {
        userDocListener?.remove()
        // 로그인/세션 복원 시 FCM 토큰 항상 최신화
        refreshFcmToken(uid)
        userDocListener = db.collection("users").document(uid)
            .addSnapshotListener { snap, _ ->
                if (snap == null || !snap.exists()) return@addSnapshotListener
                val familyId = snap.getString("familyId") ?: return@addSnapshotListener
                val roleStr  = snap.getString("role") ?: "CHILD"
                val role     = if (roleStr == "PARENT") UserRole.PARENT else UserRole.CHILD

                // FCM 수신 설정 동기화
                _fcmEnabled.value = snap.getBoolean("fcmEnabled") ?: true

                _currentUser.value = UserData(
                    uid      = uid,
                    email    = snap.getString("email")    ?: "",
                    nickname = snap.getString("nickname") ?: "",
                    role     = role,
                    familyId = familyId
                )

                // familyId가 바뀐 경우에만 구독 재설정
                if (cachedFamilyId != familyId) {
                    cachedFamilyId = familyId
                    cachedRole     = role
                    _debugRole.value = role
                    clearFamilyListeners()
                    subscribeToFamilyData(familyId, uid, role)
                }
            }
    }

    // ── Firestore 실시간 구독 4종 ────────────────────────────────────────────

    private fun subscribeToFamilyData(familyId: String, uid: String, role: UserRole) {

        // ① 가족 마스터 문서: 관심종목 + 수수료 설정 + adminUid (FCM 수신자 캐시)
        db.collection("families").document(familyId)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                cachedAdminUid = snap.getString("adminUid")
                @Suppress("UNCHECKED_CAST")
                _pendingChildren.value = (snap.get("pendingChildren") as? List<Map<String, String>>) ?: emptyList()

                // 자녀 기기: 서버 stockMasterVersion이 로컬보다 높으면 백그라운드 자동 싱크
                val remoteVersion = snap.getLong("stockMasterVersion") ?: 0L
                if (remoteVersion > 0L && role == UserRole.CHILD) {
                    val localVersion = StockMasterRepository.getLocalVersion(getApplication())
                    if (remoteVersion > localVersion) {
                        viewModelScope.launch {
                            runCatching { downloadAndCacheStockMaster(remoteVersion) }
                        }
                    }
                }

                @Suppress("UNCHECKED_CAST")
                val tickers = (snap.get("watchlist") as? List<String>) ?: emptyList()
                cachedWatchlistTickers.apply { clear(); addAll(tickers) }
                val masterList = _allStocksMasterList.value
                val liveMap = _livePrices.value
                _watchlist.value = tickers.map { ticker ->
                    dynamicPrices[ticker]
                        ?: liveMap[ticker]
                        ?: masterList.find { it.ticker == ticker }
                        ?: marketPrices[ticker]
                        ?: StockItem(ticker, ticker, 0L, 0.0)
                }
                val fee = snap.get("feeSettings")
                if (fee is Map<*, *>) {
                    (fee["brokerFeeRate"] as? Double)?.let { _commissionRate.value = it }
                    (fee["tradeTaxRate"]  as? Double)?.let { _taxRate.value = it }
                }
            }.also { familyListeners.add(it) }

        // ② 자산: 전체 childAssets 구독 → 본인 자산 + 전체 구성원 데이터 + 리더보드
        db.collection("families").document(familyId)
            .collection("childAssets")
            .addSnapshotListener { snaps, _ ->
                if (snaps == null) return@addSnapshotListener
                lbAssets.clear()
                val newBalances = mutableMapOf<String, Long>()
                val newHoldings = mutableMapOf<String, List<HoldingItem>>()

                for (doc in snaps.documents) {
                    val balance       = doc.getLong("balance") ?: 0L
                    val initialBudget = doc.getLong("initialBudget") ?: balance
                    @Suppress("UNCHECKED_CAST")
                    val stocks = (doc.get("holdingStocks") as? List<Map<String, Any>>) ?: emptyList()

                    lbAssets[doc.id] = Triple(balance, initialBudget, stocks)
                    newBalances[doc.id] = balance
                    newHoldings[doc.id] = stocks.mapNotNull { it.toHoldingItem() }

                    // 본인 문서: 부모·자녀 모두 자신의 데이터만 HomeScreen에 표시
                    if (doc.id == uid) {
                        _availableCash.value = balance
                        _holdings.value      = stocks.mapNotNull { it.toHoldingItem() }
                    }
                }

                _memberBalances.value = newBalances
                _memberHoldings.value = newHoldings

                recomputeLeaderboard(uid)
            }.also { familyListeners.add(it) }

        // ③ 거래 요청: 부모는 전체, 자녀는 본인 것만 필터
        db.collection("families").document(familyId)
            .collection("transactionRequests")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snaps, _ ->
                if (snaps == null) return@addSnapshotListener
                val all = snaps.documents.mapNotNull { doc ->
                    runCatching { doc.toTradeRequest() }.getOrNull()
                }
                _tradeRequests.value = if (role == UserRole.CHILD) {
                    all.filter { it.childUid == uid }
                } else {
                    all
                }
            }.also { familyListeners.add(it) }

        // ④ 전체 가족 닉네임 (리더보드 표시명 + 자산 관리 패널용, 부모 포함)
        db.collection("users")
            .whereEqualTo("familyId", familyId)
            .addSnapshotListener { snaps, _ ->
                if (snaps == null) return@addSnapshotListener
                lbNicknames.clear()
                val members = mutableListOf<Pair<String, String>>()
                snaps.documents.forEach { doc ->
                    val nickname = doc.getString("nickname") ?: ""
                    lbNicknames[doc.id] = nickname
                    members.add(doc.id to nickname)
                }
                _familyMembers.value = members
                recomputeLeaderboard(uid)
            }.also { familyListeners.add(it) }
    }

    // ── 🏆 리더보드 수익률 산정 ───────────────────────────────────────────────
    // 공식: (현재총자산 - 기초자산) / 기초자산 × 100, 소수점 둘째자리
    private fun recomputeLeaderboard(currentUid: String) {
        _leaderboard.value = lbAssets.entries.map { (childUid, triple) ->
            val (balance, initialBudget, holdings) = triple
            val stockValue = holdings.sumOf { h ->
                val ticker = h["ticker"] as? String ?: return@sumOf 0L
                val qty    = (h["quantity"] as? Long)?.toInt() ?: 0
                (effectivePrice(ticker)?.currentPrice ?: 0L) * qty
            }
            val totalAsset = balance + stockValue
            val rawRate = if (initialBudget > 0) {
                (totalAsset - initialBudget).toDouble() / initialBudget.toDouble() * 100.0
            } else 0.0
            // 소수점 둘째자리 반올림
            val profitRate = kotlin.math.round(rawRate * 100) / 100.0
            LeaderboardEntry(
                childUid      = childUid,
                nickname      = lbNicknames[childUid] ?: childUid,
                totalAsset    = totalAsset,
                initialBudget = initialBudget,
                profitRate    = profitRate,
                isCurrentUser = childUid == currentUid
            )
        }.sortedByDescending { it.profitRate }
    }

    // ── [DEBUG] 역할 전환 토글 ────────────────────────────────────────────────

    fun toggleDebugRole() {
        _debugRole.value = if (_debugRole.value == UserRole.PARENT) UserRole.CHILD else UserRole.PARENT
    }

    // ── 🔔 FCM 수신 ON/OFF 토글 (Firestore 동기화) ─────────────────────────────

    fun updateFcmEnabled(enabled: Boolean) {
        _fcmEnabled.value = enabled
        val uid = cachedUid ?: return
        viewModelScope.launch {
            try {
                db.collection("users").document(uid)
                    .update("fcmEnabled", enabled).await()
            } catch (e: Exception) {
                _errorMessage.value = networkSafeMessage(e)
            }
        }
    }

    // ── C-0. 직접 체결 (부모 전용) ───────────────────────────────────────────
    // 승인 절차 없이 본인 childAssets 문서에 바로 반영한다.

    fun executeTradeDirectly(
        stockName: String, ticker: String,
        quantity: Int, price: Long, type: TradeType
    ) {
        val uid      = cachedUid      ?: return
        val familyId = cachedFamilyId ?: run { _errorMessage.value = "가족에 연결되지 않았습니다."; return }
        val commRate = _commissionRate.value
        val txRate   = _taxRate.value

        viewModelScope.launch {
            try {
                db.runTransaction { tx ->
                    val assetRef  = db.collection("families").document(familyId)
                        .collection("childAssets").document(uid)
                    val assetSnap = tx.get(assetRef)
                    val balance   = assetSnap.getLong("balance") ?: 0L
                    @Suppress("UNCHECKED_CAST")
                    val stocks    = (assetSnap.get("holdingStocks") as? List<Map<String, Any>>)
                        ?.toMutableList() ?: mutableListOf()

                    val feeRate = (commRate + txRate) / 100.0

                    if (type == TradeType.BUY) {
                        val totalCost = Math.round(price.toDouble() * quantity * (1.0 + feeRate))
                        check(balance >= totalCost) {
                            "예수금 부족 (필요: ₩${"%,d".format(totalCost)}, 잔고: ₩${"%,d".format(balance)})"
                        }
                        val existing = stocks.firstOrNull { it["ticker"] == ticker }
                        val existQty = (existing?.get("quantity") as? Long)?.toInt() ?: 0
                        val existAvg = (existing?.get("averagePrice") as? Double) ?: 0.0
                        val newQty   = existQty + quantity
                        val newAvg   = if (existQty == 0) totalCost.toDouble() / quantity
                                       else (existAvg * existQty + totalCost.toDouble()) / newQty
                        val updated  = stocks.toMutableList()
                        val idx      = updated.indexOfFirst { it["ticker"] == ticker }
                        val entry    = mapOf(
                            "ticker"       to ticker,
                            "name"         to stockName,
                            "quantity"     to newQty.toLong(),
                            "averagePrice" to newAvg
                        )
                        if (idx >= 0) updated[idx] = entry else updated.add(entry)
                        tx.update(assetRef, mapOf(
                            "balance"       to balance - totalCost,
                            "holdingStocks" to updated
                        ))
                    } else { // SELL
                        val netProceeds = Math.round(price.toDouble() * quantity * (1.0 - feeRate))
                        val existing    = stocks.firstOrNull { it["ticker"] == ticker }
                        val existQty    = (existing?.get("quantity") as? Long)?.toInt() ?: 0
                        check(existQty >= quantity) { "보유 수량 부족 (보유: ${existQty}주, 매도 요청: ${quantity}주)" }
                        val updated = stocks.toMutableList()
                        val idx     = updated.indexOfFirst { it["ticker"] == ticker }
                        if (idx >= 0) {
                            val remain = existQty - quantity
                            if (remain == 0) updated.removeAt(idx)
                            else {
                                val m = updated[idx].toMutableMap()
                                m["quantity"] = remain.toLong()
                                updated[idx]  = m
                            }
                        }
                        tx.update(assetRef, mapOf(
                            "balance"       to balance + netProceeds,
                            "holdingStocks" to updated
                        ))
                    }
                }.await()
            } catch (e: Exception) {
                _errorMessage.value = networkSafeMessage(e)
            }
        }
    }

    // ── C. 거래 요청 제출 (자녀 전용) + 부모 FCM 알림 ────────────────────────

    fun submitTradeRequest(
        stockName: String, ticker: String, quantity: Int,
        price: Long, memo: String, type: TradeType
    ) {
        val uid      = cachedUid      ?: return
        val familyId = cachedFamilyId ?: run { _errorMessage.value = "가족에 연결되지 않았습니다."; return }
        val nickname = _currentUser.value?.nickname ?: ""

        if (memo.trim().length < 10) {
            _errorMessage.value = "투자 메모를 10자 이상 작성해 주세요."; return
        }
        if (type == TradeType.BUY) {
            // 수수료·세금 포함 실부담액으로 사전 검증 (Math.round: 반올림)
            val feeRate       = (_commissionRate.value + _taxRate.value) / 100.0
            val estimatedCost = Math.round(price.toDouble() * quantity * (1.0 + feeRate))
            if (estimatedCost > _availableCash.value) {
                _errorMessage.value =
                    "예수금이 부족합니다. (수수료·세금 포함 ₩${"%,d".format(estimatedCost)} 필요)"; return
            }
        }
        if (type == TradeType.SELL) {
            val holdingQty = _holdings.value.find { it.stock.ticker == ticker }?.quantity ?: 0
            if (holdingQty < quantity) { _errorMessage.value = "보유 수량이 부족합니다."; return }
        }

        viewModelScope.launch {
            try {
                val id = UUID.randomUUID().toString()
                db.collection("families").document(familyId)
                    .collection("transactionRequests").document(id)
                    .set(mapOf(
                        "requestId"         to id,
                        "childUid"          to uid,
                        "childNickname"     to nickname,
                        "stockName"         to stockName,
                        "stockTicker"       to ticker,
                        "quantity"          to quantity.toLong(),
                        "pricePerShare"     to price,
                        "memo"              to memo,
                        "type"              to if (type == TradeType.BUY) "BUY" else "SELL",
                        "status"            to "PENDING",
                        "timestamp"         to System.currentTimeMillis(),
                        "filledPrice"       to 0L,
                        "filledQuantity"    to 0L,
                        "completedAt"       to 0L,
                        "remainingQuantity" to 0L,
                        "failReason"        to ""
                    )).await()

                // [자녀→부모] FCM: 부모에게 거래 승인 요청 알림
                cachedAdminUid?.let { adminUid ->
                    val typeLabel = if (type == TradeType.BUY) "매수" else "매도"
                    sendFcmToUser(
                        adminUid,
                        "🔔 거래 승인 요청",
                        "$nickname 자녀가 $stockName ${quantity}주 $typeLabel 승인을 요청했습니다!"
                    )
                }
            } catch (e: Exception) {
                _errorMessage.value = networkSafeMessage(e)
            }
        }
    }

    // ── D. 장부 1단계: PENDING → ACCEPTED ─────────────────────────────────────

    fun acceptTradeRequest(requestId: String) {
        val familyId = cachedFamilyId ?: return
        viewModelScope.launch {
            try {
                db.collection("families").document(familyId)
                    .collection("transactionRequests").document(requestId)
                    .update("status", "ACCEPTED").await()
            } catch (e: Exception) {
                _errorMessage.value = networkSafeMessage(e)
            }
        }
    }

    // ── D. 장부 2단계 체결: runTransaction 원자 정산 + 부분체결 분리 + 자녀 FCM ─

    fun fillTradeRequest(requestId: String, filledPrice: Long, filledQuantity: Int, completedAt: Long) {
        val familyId = cachedFamilyId ?: return
        val now      = System.currentTimeMillis()   // 부분체결 신규 요청의 timestamp용
        val commRate = _commissionRate.value
        val txRate   = _taxRate.value

        // FCM 발송용: 트랜잭션 전에 StateFlow에서 사전 조회
        val reqInState   = _tradeRequests.value.find { it.requestId == requestId }
        val fcmChildUid  = reqInState?.childUid
        val fcmStockName = reqInState?.stockName ?: ""
        val reqTotalQty  = reqInState?.quantity ?: filledQuantity

        viewModelScope.launch {
            try {
                db.runTransaction { tx ->
                    val reqRef  = db.collection("families").document(familyId)
                        .collection("transactionRequests").document(requestId)
                    val reqSnap = tx.get(reqRef)

                    check(reqSnap.getString("status") == "ACCEPTED") {
                        "접수 완료(ACCEPTED) 상태가 아닙니다."
                    }

                    val childUid  = reqSnap.getString("childUid")!!
                    val typeStr   = reqSnap.getString("type")!!
                    val ticker    = reqSnap.getString("stockTicker")!!
                    val stockName = reqSnap.getString("stockName")      ?: ""
                    val reqQty    = (reqSnap.getLong("quantity")        ?: 0L).toInt()
                    val reqPrice  = reqSnap.getLong("pricePerShare")    ?: 0L
                    val memo      = reqSnap.getString("memo")           ?: ""
                    val childNick = reqSnap.getString("childNickname")  ?: ""
                    // 체결 수량 범위 원천 차단: 0주 이하·요청 수량 초과 모두 차단
                    check(filledQuantity > 0) {
                        "체결 수량은 1주 이상이어야 합니다."
                    }
                    check(filledQuantity <= reqQty) {
                        "체결 수량(${filledQuantity}주)이 요청 수량(${reqQty}주)을 초과합니다."
                    }
                    val isPartial = filledQuantity < reqQty

                    val assetRef  = db.collection("families").document(familyId)
                        .collection("childAssets").document(childUid)
                    val assetSnap = tx.get(assetRef)
                    val balance   = assetSnap.getLong("balance") ?: 0L
                    @Suppress("UNCHECKED_CAST")
                    val stocks    = (assetSnap.get("holdingStocks") as? List<Map<String, Any>>)
                        ?.toMutableList() ?: mutableListOf()

                    val feeRate = (commRate + txRate) / 100.0

                    if (typeStr == "BUY") {
                        // Math.round: 반올림 적용 (toLong 버림 → 원화 오차 제거)
                        val totalCost = Math.round(filledPrice.toDouble() * filledQuantity * (1.0 + feeRate))
                        check(balance >= totalCost) {
                            "예수금 부족 (필요: ₩${"%,d".format(totalCost)}, 잔고: ₩${"%,d".format(balance)})"
                        }
                        val existing = stocks.firstOrNull { it["ticker"] == ticker }
                        val existQty = (existing?.get("quantity") as? Long)?.toInt() ?: 0
                        val existAvg = (existing?.get("averagePrice") as? Double) ?: 0.0
                        val newQty   = existQty + filledQuantity
                        val newAvg   = if (existQty == 0) totalCost.toDouble() / filledQuantity
                                       else (existAvg * existQty + totalCost.toDouble()) / newQty

                        val updated = stocks.toMutableList()
                        val idx     = updated.indexOfFirst { it["ticker"] == ticker }
                        val entry   = mapOf(
                            "ticker"       to ticker,
                            "name"         to stockName,
                            "quantity"     to newQty.toLong(),
                            "averagePrice" to newAvg
                        )
                        if (idx >= 0) updated[idx] = entry else updated.add(entry)
                        tx.update(assetRef, mapOf(
                            "balance"       to balance - totalCost,
                            "holdingStocks" to updated
                        ))
                    } else { // SELL
                        val netProceeds = Math.round(filledPrice.toDouble() * filledQuantity * (1.0 - feeRate))
                        val existing    = stocks.firstOrNull { it["ticker"] == ticker }
                        val existQty    = (existing?.get("quantity") as? Long)?.toInt() ?: 0
                        check(existQty >= filledQuantity) { "보유 수량 부족 (보유: ${existQty}주, 체결 요청: ${filledQuantity}주)" }

                        val updated = stocks.toMutableList()
                        val idx     = updated.indexOfFirst { it["ticker"] == ticker }
                        if (idx >= 0) {
                            val remain = existQty - filledQuantity
                            if (remain == 0) updated.removeAt(idx)
                            else {
                                val m = updated[idx].toMutableMap()
                                m["quantity"] = remain.toLong()
                                updated[idx]  = m
                            }
                        }
                        tx.update(assetRef, mapOf(
                            "balance"       to balance + netProceeds,
                            "holdingStocks" to updated
                        ))
                    }

                    tx.update(reqRef, mapOf(
                        "status"            to if (isPartial) "PARTIAL_FILLED" else "FILLED",
                        "filledPrice"       to filledPrice,
                        "filledQuantity"    to filledQuantity.toLong(),
                        "completedAt"       to completedAt,
                        "remainingQuantity" to if (isPartial) (reqQty - filledQuantity).toLong() else 0L
                    ))

                    // 부분체결: 잔여 수량 새 PENDING 문서로 분리
                    if (isPartial) {
                        val newId  = UUID.randomUUID().toString()
                        val newRef = db.collection("families").document(familyId)
                            .collection("transactionRequests").document(newId)
                        tx.set(newRef, mapOf(
                            "requestId"         to newId,
                            "childUid"          to childUid,
                            "childNickname"     to childNick,
                            "stockName"         to stockName,
                            "stockTicker"       to ticker,
                            "quantity"          to (reqQty - filledQuantity).toLong(),
                            "pricePerShare"     to reqPrice,
                            "memo"              to memo,
                            "type"              to typeStr,
                            "status"            to "PENDING",
                            "timestamp"         to now,
                            "filledPrice"       to 0L,
                            "filledQuantity"    to 0L,
                            "completedAt"       to 0L,
                            "remainingQuantity" to 0L,
                            "failReason"        to ""
                        ))
                    }
                }.await()

                // [부모→자녀] FCM: 체결/부분체결 알림
                fcmChildUid?.let { childUid ->
                    val isPartial = filledQuantity < reqTotalQty
                    val body = if (isPartial)
                        "📈 $fcmStockName ${filledQuantity}주 부분 체결되었습니다. 나머지 수량은 별도 처리됩니다."
                    else
                        "📈 요청하신 $fcmStockName 주식이 체결되었습니다!"
                    sendFcmToUser(childUid, "📈 거래 체결 알림", body)
                }
            } catch (e: Exception) {
                _errorMessage.value = networkSafeMessage(e)
            }
        }
    }

    // ── D. 장부: 완료된 거래 수정 (부모 전용) ────────────────────────────────────
    // BUY : 기존 cost 환급 + 새 cost 차감, 보유 수량/평단가 역산 후 재적용
    // SELL: 기존 proceeds 환수 + 새 proceeds 지급, 보유 수량 조정

    fun editCompletedTrade(
        requestId: String,
        childUid: String,
        newFilledPrice: Long,
        newFilledQuantity: Int,
        newMemo: String
    ) {
        val familyId = cachedFamilyId ?: return
        val commRate = _commissionRate.value
        val txRate   = _taxRate.value
        val feeRate  = (commRate + txRate) / 100.0

        viewModelScope.launch {
            try {
                db.runTransaction { tx ->
                    val reqRef  = db.collection("families").document(familyId)
                        .collection("transactionRequests").document(requestId)
                    val reqSnap = tx.get(reqRef)

                    val typeStr       = reqSnap.getString("type")            ?: "BUY"
                    val ticker        = reqSnap.getString("stockTicker")     ?: return@runTransaction
                    val stockName     = reqSnap.getString("stockName")       ?: ""
                    val oldFilledPrice= reqSnap.getLong("filledPrice")       ?: 0L
                    val oldFilledQty  = (reqSnap.getLong("filledQuantity")   ?: 0L).toInt()
                    val reqQty        = (reqSnap.getLong("quantity")         ?: 0L).toInt()

                    val assetRef  = db.collection("families").document(familyId)
                        .collection("childAssets").document(childUid)
                    val assetSnap = tx.get(assetRef)
                    val balance   = assetSnap.getLong("balance") ?: 0L
                    @Suppress("UNCHECKED_CAST")
                    val stocks    = (assetSnap.get("holdingStocks") as? List<Map<String, Any>>)
                        ?.toMutableList() ?: mutableListOf()

                    val updated = stocks.toMutableList()

                    if (typeStr == "BUY") {
                        val oldCost = Math.round(oldFilledPrice.toDouble() * oldFilledQty * (1.0 + feeRate))
                        val newCost = Math.round(newFilledPrice.toDouble() * newFilledQuantity * (1.0 + feeRate))

                        val existing        = updated.firstOrNull { it["ticker"] == ticker }
                        val currentQty      = (existing?.get("quantity") as? Long)?.toInt() ?: 0
                        val currentAvg      = (existing?.get("averagePrice") as? Double) ?: 0.0
                        val currentTotalCost= currentAvg * currentQty

                        val reversedQty     = currentQty - oldFilledQty
                        val reversedCost    = currentTotalCost - oldCost
                        val newQty          = reversedQty + newFilledQuantity
                        val newAvg          = if (newQty > 0) (reversedCost + newCost) / newQty else 0.0

                        val idx = updated.indexOfFirst { it["ticker"] == ticker }
                        if (newQty <= 0) { if (idx >= 0) updated.removeAt(idx) }
                        else {
                            val entry = mapOf(
                                "ticker"       to ticker,
                                "name"         to stockName,
                                "quantity"     to newQty.toLong(),
                                "averagePrice" to newAvg
                            )
                            if (idx >= 0) updated[idx] = entry else updated.add(entry)
                        }
                        tx.update(assetRef, mapOf(
                            "balance"       to balance + oldCost - newCost,
                            "holdingStocks" to updated
                        ))
                    } else { // SELL
                        val oldProceeds = Math.round(oldFilledPrice.toDouble() * oldFilledQty * (1.0 - feeRate))
                        val newProceeds = Math.round(newFilledPrice.toDouble() * newFilledQuantity * (1.0 - feeRate))

                        val existing    = updated.firstOrNull { it["ticker"] == ticker }
                        val currentQty  = (existing?.get("quantity") as? Long)?.toInt() ?: 0
                        val currentAvg  = (existing?.get("averagePrice") as? Double) ?: 0.0
                        val newHoldQty  = currentQty + (oldFilledQty - newFilledQuantity)
                        // 역산 후 보유 수량이 음수가 되는 수정 원천 차단
                        check(newHoldQty >= 0) {
                            "보유 수량이 부족하여 수정 불가 (현재 ${currentQty}주, 역산 후 ${newHoldQty}주)"
                        }

                        val idx = updated.indexOfFirst { it["ticker"] == ticker }
                        if (newHoldQty <= 0) { if (idx >= 0) updated.removeAt(idx) }
                        else {
                            val entry = mapOf(
                                "ticker"       to ticker,
                                "name"         to stockName,
                                "quantity"     to newHoldQty.toLong(),
                                "averagePrice" to currentAvg
                            )
                            if (idx >= 0) updated[idx] = entry else updated.add(entry)
                        }
                        tx.update(assetRef, mapOf(
                            "balance"       to balance - oldProceeds + newProceeds,
                            "holdingStocks" to updated
                        ))
                    }

                    val newStatus = if (newFilledQuantity < reqQty) "PARTIAL_FILLED" else "FILLED"
                    tx.update(reqRef, mapOf(
                        "filledPrice"       to newFilledPrice,
                        "filledQuantity"    to newFilledQuantity.toLong(),
                        "remainingQuantity" to (reqQty - newFilledQuantity).coerceAtLeast(0).toLong(),
                        "status"            to newStatus,
                        "memo"              to newMemo
                    ))
                }.await()
            } catch (e: Exception) {
                _errorMessage.value = networkSafeMessage(e)
            }
        }
    }

    // ── D. 장부 2단계: 미체결 처리 + 자녀 FCM ───────────────────────────────────

    fun unfillTradeRequest(requestId: String) {
        val familyId = cachedFamilyId ?: return
        val now      = System.currentTimeMillis()

        // FCM 발송용: StateFlow에서 사전 조회
        val reqInState   = _tradeRequests.value.find { it.requestId == requestId }
        val fcmChildUid  = reqInState?.childUid
        val fcmStockName = reqInState?.stockName ?: ""

        viewModelScope.launch {
            try {
                db.collection("families").document(familyId)
                    .collection("transactionRequests").document(requestId)
                    .update(mapOf("status" to "UNFILLED", "completedAt" to now)).await()

                // [부모→자녀] FCM: 미체결 처리 알림
                fcmChildUid?.let { childUid ->
                    sendFcmToUser(childUid, "❌ 주문 미체결 처리", "주문이 미체결 처리되었습니다.")
                }
            } catch (e: Exception) {
                _errorMessage.value = networkSafeMessage(e)
            }
        }
    }

    // ── C-Extra. 배당 등록 (부모·자녀 공통, 즉시 체결) ──────────────────────────
    // 세금 처리 없이 총액을 그대로 예수금에 추가하고 DIVIDEND 타입으로 FILLED 기록

    fun addDividend(stockName: String, ticker: String, amount: Long, timestamp: Long) {
        val uid      = cachedUid      ?: return
        val familyId = cachedFamilyId ?: run { _errorMessage.value = "가족에 연결되지 않았습니다."; return }
        val nickname = _currentUser.value?.nickname ?: ""
        val now      = timestamp

        viewModelScope.launch {
            try {
                val assetRef = db.collection("families").document(familyId)
                    .collection("childAssets").document(uid)
                db.runTransaction { tx ->
                    val snap    = tx.get(assetRef)
                    val balance = snap.getLong("balance") ?: 0L
                    if (snap.exists()) {
                        tx.update(assetRef, "balance", balance + amount)
                    } else {
                        tx.set(assetRef, mapOf(
                            "balance"       to amount,
                            "holdingStocks" to emptyList<Any>()
                        ))
                    }
                }.await()

                val id = UUID.randomUUID().toString()
                db.collection("families").document(familyId)
                    .collection("transactionRequests").document(id)
                    .set(mapOf(
                        "requestId"         to id,
                        "childUid"          to uid,
                        "childNickname"     to nickname,
                        "stockName"         to stockName,
                        "stockTicker"       to ticker,
                        "quantity"          to 1L,
                        "pricePerShare"     to amount,
                        "memo"              to "${stockName} 배당금",
                        "type"              to "DIVIDEND",
                        "status"            to "FILLED",
                        "timestamp"         to now,
                        "filledPrice"       to amount,
                        "filledQuantity"    to 1L,
                        "completedAt"       to now,
                        "remainingQuantity" to 0L,
                        "failReason"        to ""
                    )).await()
            } catch (e: Exception) {
                _errorMessage.value = networkSafeMessage(e)
            }
        }
    }

    // ── B-Extra. 이자 지급 (부모 전용) ──────────────────────────────────────────
    // 예수금 증가 + 장부(transactionRequests)에 INTEREST 타입으로 FILLED 기록 생성

    fun addInterest(targetUid: String, amount: Long, description: String, timestamp: Long) {
        val familyId = cachedFamilyId ?: return
        val now      = timestamp
        val nickname = _familyMembers.value.find { it.first == targetUid }?.second ?: targetUid

        viewModelScope.launch {
            try {
                val assetRef = db.collection("families").document(familyId)
                    .collection("childAssets").document(targetUid)
                db.runTransaction { tx ->
                    val snap    = tx.get(assetRef)
                    val balance = snap.getLong("balance") ?: 0L
                    if (snap.exists()) {
                        tx.update(assetRef, "balance", balance + amount)
                    } else {
                        tx.set(assetRef, mapOf(
                            "balance"       to amount,
                            "holdingStocks" to emptyList<Any>()
                        ))
                    }
                }.await()

                val id    = UUID.randomUUID().toString()
                val title = description.ifBlank { "이자 수입" }
                db.collection("families").document(familyId)
                    .collection("transactionRequests").document(id)
                    .set(mapOf(
                        "requestId"         to id,
                        "childUid"          to targetUid,
                        "childNickname"     to nickname,
                        "stockName"         to title,
                        "stockTicker"       to "INTEREST",
                        "quantity"          to 1L,
                        "pricePerShare"     to amount,
                        "memo"              to title,
                        "type"              to "INTEREST",
                        "status"            to "FILLED",
                        "timestamp"         to now,
                        "filledPrice"       to amount,
                        "filledQuantity"    to 1L,
                        "completedAt"       to now,
                        "remainingQuantity" to 0L,
                        "failReason"        to ""
                    )).await()

                sendFcmToUser(
                    targetUid,
                    "💰 이자 지급",
                    "₩${"%,d".format(amount)} 이자가 지급되었습니다."
                )
            } catch (e: Exception) {
                _errorMessage.value = networkSafeMessage(e)
            }
        }
    }

    // ── C-Extra. 출금 요청 (자녀·부모 공통, 즉시 차감) ─────────────────────────
    // 예수금에서 금액 차감 + WITHDRAWAL 타입으로 FILLED 기록 생성

    fun requestWithdrawal(amount: Long, description: String, timestamp: Long) {
        val uid      = cachedUid      ?: return
        val familyId = cachedFamilyId ?: run { _errorMessage.value = "가족에 연결되지 않았습니다."; return }
        val nickname = _currentUser.value?.nickname ?: ""

        viewModelScope.launch {
            try {
                val assetRef = db.collection("families").document(familyId)
                    .collection("childAssets").document(uid)
                db.runTransaction { tx ->
                    val snap    = tx.get(assetRef)
                    val balance = snap.getLong("balance") ?: 0L
                    check(balance >= amount) {
                        "예수금 부족 (필요: ₩${"%,d".format(amount)}, 잔고: ₩${"%,d".format(balance)})"
                    }
                    tx.update(assetRef, "balance", balance - amount)
                }.await()

                val id    = UUID.randomUUID().toString()
                val title = description.ifBlank { "출금" }
                db.collection("families").document(familyId)
                    .collection("transactionRequests").document(id)
                    .set(mapOf(
                        "requestId"         to id,
                        "childUid"          to uid,
                        "childNickname"     to nickname,
                        "stockName"         to title,
                        "stockTicker"       to "WITHDRAWAL",
                        "quantity"          to 1L,
                        "pricePerShare"     to amount,
                        "memo"              to title,
                        "type"              to "WITHDRAWAL",
                        "status"            to "FILLED",
                        "timestamp"         to timestamp,
                        "filledPrice"       to amount,
                        "filledQuantity"    to 1L,
                        "completedAt"       to timestamp,
                        "remainingQuantity" to 0L,
                        "failReason"        to ""
                    )).await()
            } catch (e: Exception) {
                _errorMessage.value = networkSafeMessage(e)
            }
        }
    }

    // ── A. 관심 종목 토글 (FieldValue.arrayUnion / arrayRemove) ──────────────

    fun addToWatchlist(stock: StockItem) {
        val familyId = cachedFamilyId ?: return
        if (_watchlist.value.any { it.ticker == stock.ticker }) return
        viewModelScope.launch {
            // 검색 결과 가격을 직접 사용 (srtnCd 단독 재조회 시 엉뚱한 종목 반환 문제 우회)
            // auto-refresh(refreshActiveStockPrices)가 10초 내에 최신가로 갱신함
            _livePrices.value = _livePrices.value + (stock.ticker to stock)
            val currentTickers = _watchlist.value.map { it.ticker }
            _watchlist.value = currentTickers.map { t ->
                dynamicPrices[t] ?: _livePrices.value[t]
                    ?: _allStocksMasterList.value.find { it.ticker == t }
                    ?: marketPrices[t] ?: StockItem(t, t, 0L, 0.0)
            }
            // 최근 검색 추가 (IO 스레드)
            withContext(Dispatchers.IO) {
                StockMasterRepository.addRecentSearch(getApplication(), stock)
            }
            _recentSearches.value = withContext(Dispatchers.IO) {
                StockMasterRepository.loadRecentSearches(getApplication())
            }
            try {
                db.collection("families").document(familyId)
                    .update("watchlist", FieldValue.arrayUnion(stock.ticker)).await()
            } catch (e: Exception) {
                _errorMessage.value = networkSafeMessage(e)
            }
        }
    }

    fun removeFromWatchlist(ticker: String) {
        val familyId = cachedFamilyId ?: return
        viewModelScope.launch {
            try {
                db.collection("families").document(familyId)
                    .update("watchlist", FieldValue.arrayRemove(ticker)).await()
            } catch (e: Exception) {
                _errorMessage.value = networkSafeMessage(e)
            }
        }
    }

    // ── B. 관리자: 자녀 예수금 수정 + initialBudget 최초 설정 + FCM 알림 ──────

    fun updateChildCash(childUid: String, newAmount: Long, updatedAt: Long) {
        val familyId = cachedFamilyId ?: return
        viewModelScope.launch {
            try {
                val assetRef = db.collection("families").document(familyId)
                    .collection("childAssets").document(childUid)
                db.runTransaction { tx ->
                    val snap = tx.get(assetRef)
                    @Suppress("UNCHECKED_CAST")
                    val stocks = (snap.get("holdingStocks") as? List<Map<String, Any>>) ?: emptyList()
                    // initialBudget = 설정 현금 + 초기 보유 종목 매입 원가 합계
                    val stockCostBasis = stocks.costBasis()
                    val newInitialBudget = newAmount + stockCostBasis
                    if (snap.exists()) {
                        tx.update(assetRef, mapOf(
                            "balance"        to newAmount,
                            "initialBudget"  to newInitialBudget,
                            "balanceSetAt"   to updatedAt
                        ))
                    } else {
                        tx.set(assetRef, mapOf(
                            "balance"        to newAmount,
                            "holdingStocks"  to emptyList<Any>(),
                            "initialBudget"  to newInitialBudget,
                            "balanceSetAt"   to updatedAt
                        ))
                    }
                }.await()
                // [부모→자녀] FCM: 예수금 변경 알림
                sendFcmToUser(
                    childUid,
                    "💰 예수금 업데이트",
                    "예수금이 ₩${"%,d".format(newAmount)}으로 조정되었습니다."
                )
            } catch (e: Exception) {
                _errorMessage.value = networkSafeMessage(e)
            }
        }
    }

    // ── B. 관리자: 초기 보유 주식 세팅 ───────────────────────────────────────

    fun addInitialHolding(targetUid: String, holding: HoldingItem, acquiredAt: Long) {
        val familyId = cachedFamilyId ?: return
        viewModelScope.launch {
            // 검색 결과 가격을 직접 사용 (srtnCd 단독 재조회 시 엉뚱한 종목 반환 문제 우회)
            _livePrices.value = _livePrices.value + (holding.stock.ticker to holding.stock)
            withContext(Dispatchers.IO) {
                StockMasterRepository.addRecentSearch(getApplication(), holding.stock)
            }
            _recentSearches.value = withContext(Dispatchers.IO) {
                StockMasterRepository.loadRecentSearches(getApplication())
            }
            try {
                val assetRef = db.collection("families").document(familyId)
                    .collection("childAssets").document(targetUid)
                db.runTransaction { tx ->
                    val snap    = tx.get(assetRef)
                    val balance = snap.getLong("balance") ?: 0L
                    @Suppress("UNCHECKED_CAST")
                    val current = (snap.get("holdingStocks") as? List<Map<String, Any>>)
                        ?.toMutableList() ?: mutableListOf()
                    val entry = mapOf(
                        "ticker"       to holding.stock.ticker,
                        "name"         to holding.stock.name,
                        "quantity"     to holding.quantity.toLong(),
                        "averagePrice" to holding.avgPrice.toDouble(),
                        "acquiredAt"   to acquiredAt
                    )
                    val idx = current.indexOfFirst { it["ticker"] == holding.stock.ticker }
                    if (idx >= 0) current[idx] = entry else current.add(entry)
                    // initialBudget = 잔고 + 전체 보유 종목 매입 원가 합계
                    val newInitialBudget = balance + current.costBasis()
                    if (snap.exists()) {
                        tx.update(assetRef, mapOf(
                            "holdingStocks" to current,
                            "initialBudget" to newInitialBudget
                        ))
                    } else {
                        tx.set(assetRef, mapOf(
                            "balance"       to 0L,
                            "holdingStocks" to current,
                            "initialBudget" to newInitialBudget
                        ))
                    }
                }.await()

                // 초기 주식 설정 → FILLED 기록 생성 (장부 & Frame 8-1 거래 기록에 반영)
                val nickname = _familyMembers.value.find { it.first == targetUid }?.second ?: targetUid
                val txId = UUID.randomUUID().toString()
                db.collection("families").document(familyId)
                    .collection("transactionRequests").document(txId)
                    .set(mapOf(
                        "requestId"         to txId,
                        "childUid"          to targetUid,
                        "childNickname"     to nickname,
                        "stockName"         to holding.stock.name,
                        "stockTicker"       to holding.stock.ticker,
                        "quantity"          to holding.quantity.toLong(),
                        "pricePerShare"     to holding.avgPrice,
                        "memo"              to "초기 보유 종목 등록",
                        "type"              to "BUY",
                        "status"            to "FILLED",
                        "timestamp"         to acquiredAt,
                        "filledPrice"       to holding.avgPrice,
                        "filledQuantity"    to holding.quantity.toLong(),
                        "completedAt"       to acquiredAt,
                        "remainingQuantity" to 0L,
                        "failReason"        to ""
                    )).await()
            } catch (e: Exception) {
                _errorMessage.value = networkSafeMessage(e)
            }
        }
    }

    fun removeInitialHolding(targetUid: String, ticker: String) {
        val familyId = cachedFamilyId ?: return
        viewModelScope.launch {
            try {
                val assetRef = db.collection("families").document(familyId)
                    .collection("childAssets").document(targetUid)
                db.runTransaction { tx ->
                    val snap    = tx.get(assetRef)
                    val balance = snap.getLong("balance") ?: 0L
                    @Suppress("UNCHECKED_CAST")
                    val updated = (snap.get("holdingStocks") as? List<Map<String, Any>>)
                        ?.filter { it["ticker"] != ticker } ?: emptyList()
                    // initialBudget = 잔고 + 남은 보유 종목 매입 원가 합계
                    val newInitialBudget = balance + updated.costBasis()
                    tx.update(assetRef, mapOf(
                        "holdingStocks" to updated,
                        "initialBudget" to newInitialBudget
                    ))
                }.await()
            } catch (e: Exception) {
                _errorMessage.value = networkSafeMessage(e)
            }
        }
    }

    // ── B. 수수료·세금 설정 ────────────────────────────────────────────────────

    fun updateCommissionRate(rate: Double) {
        _commissionRate.value = rate
        val familyId = cachedFamilyId ?: return
        viewModelScope.launch {
            try {
                db.collection("families").document(familyId)
                    .update("feeSettings.brokerFeeRate", rate).await()
            } catch (e: Exception) {
                _errorMessage.value = networkSafeMessage(e)
            }
        }
    }

    fun updateTaxRate(rate: Double) {
        _taxRate.value = rate
        val familyId = cachedFamilyId ?: return
        viewModelScope.launch {
            try {
                db.collection("families").document(familyId)
                    .update("feeSettings.tradeTaxRate", rate).await()
            } catch (e: Exception) {
                _errorMessage.value = networkSafeMessage(e)
            }
        }
    }

    // ── 종목 검색 (로컬 마켓 스텁) ────────────────────────────────────────────

    fun searchStocks(query: String) {
        if (query.isBlank()) { _searchResults.value = emptyList(); return }
        viewModelScope.launch {
            _searchResults.value = marketPrices.values.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.ticker.contains(query, ignoreCase = true)
            }
        }
    }

    fun clearSearchResults() {
        searchJob?.cancel()
        _searchResults.value = emptyList()
    }

    // ── 종목 검색 (공공데이터포털 금융위원회_주식시세정보 API) ──────────────────
    // 300ms 디바운스 적용; isLoading으로 검색 중 스피너 제어

    fun searchStockFromServer(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) { _searchResults.value = emptyList(); return }

        val master = _allStocksMasterList.value
        if (master.isNotEmpty()) {
            // ✅ 로컬 필터링 — 네트워크 없이 즉각 반응 (Dispatchers.Default: CPU 집약 작업)
            searchJob = viewModelScope.launch(Dispatchers.Default) {
                val results = master.filter { stock ->
                    stock.name.contains(query, ignoreCase = true) ||
                    stock.ticker.contains(query, ignoreCase = true)
                }.take(50)
                _searchResults.value = results
            }
            return
        }

        // 마스터 미로드 시 폴백: 서버 검색 (300ms 디바운스)
        searchJob = viewModelScope.launch {
            delay(300)
            _isLoading.value = true
            try {
                val resp = RetrofitClient.stockApiService.searchByName(
                    serviceKey = KRX_API_KEY,
                    resultType = "json",
                    query      = query,
                    numOfRows  = 30
                )
                _searchResults.value = (resp.response?.body?.items ?: emptyList())
                    .mapNotNull { it.toStockItem() }
                    .distinctBy { it.ticker }
            } catch (e: CancellationException) {
                throw e
            } catch (e: retrofit2.HttpException) {
                _errorMessage.value = when (e.code()) {
                    403 -> "API 키 인증 실패 (403) — data.go.kr에서 '금융위원회_주식시세정보' 활용 신청 후 인코딩 키를 확인하세요."
                    else -> "API 오류 (HTTP ${e.code()})"
                }
            } catch (e: Exception) {
                _errorMessage.value = networkSafeMessage(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ── 📦 마스터 데이터 로컬 캐시 관련 함수들 ─────────────────────────────────

    /**
     * 앱 시작 시 1회 호출.
     * - 로컬 파일 있음: 즉시 메모리 적재 (네트워크 없이 검색 가능)
     * - 로컬 파일 없음: 전체 5,000건 다운로드 후 저장
     */
    private suspend fun loadStockMasterData() {
        val ctx = getApplication<android.app.Application>()
        if (StockMasterRepository.hasLocalData(ctx)) {
            val stocks = withContext(Dispatchers.IO) { StockMasterRepository.loadStockMaster(ctx) }
            _allStocksMasterList.value = stocks
        } else {
            _isLoading.value = true
            try {
                downloadAndCacheStockMaster(System.currentTimeMillis())
            } catch (_: Exception) {
                // 초기 다운로드 실패 — 검색 폴백 모드로 동작
            } finally {
                _isLoading.value = false
            }
        }
        _recentSearches.value = withContext(Dispatchers.IO) {
            StockMasterRepository.loadRecentSearches(ctx)
        }
    }

    /** 공공 API에서 전체 종목 다운로드 후 로컬 저장 + 메모리 적재 */
    private suspend fun downloadAndCacheStockMaster(version: Long) {
        val resp = withContext(Dispatchers.IO) {
            RetrofitClient.bulkStockApiService.fetchAllStocks(
                serviceKey = KRX_API_KEY,
                resultType = "json",
                numOfRows  = 5000
            )
        }
        val stocks = (resp.response?.body?.items ?: emptyList())
            .mapNotNull { it.toStockItem() }
            .distinctBy { it.ticker }
        if (stocks.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                StockMasterRepository.saveStockMaster(getApplication(), stocks, version)
            }
            _allStocksMasterList.value = stocks
        }
    }

    /**
     * [Frame 9] 부모 전용 수동 마스터 최신화.
     * API 강제 호출 → 로컬 저장 → Firestore stockMasterVersion 갱신 (자녀 자동 싱크 트리거)
     */
    fun refreshStockMaster() {
        val familyId = cachedFamilyId ?: run { _errorMessage.value = "가족에 연결되지 않았습니다."; return }
        if (_isMasterRefreshing.value) return
        _isMasterRefreshing.value = true
        viewModelScope.launch {
            try {
                val newVersion = System.currentTimeMillis()
                downloadAndCacheStockMaster(newVersion)
                db.collection("families").document(familyId)
                    .update("stockMasterVersion", newVersion).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = networkSafeMessage(e)
            } finally {
                _isMasterRefreshing.value = false
            }
        }
    }

    /** 관심 등록 / 초기 설정 시점 단발 현재가 조회 (srtnCd 방식) */
    private suspend fun fetchLivePriceInternal(ticker: String): StockItem? = runCatching {
        withContext(Dispatchers.IO) {
            RetrofitClient.stockApiService.fetchSingleStock(
                serviceKey = KRX_API_KEY,
                resultType = "json",
                ticker     = ticker,
                numOfRows  = 1
            ).response?.body?.items?.firstOrNull()?.toStockItem()
        }
    }.getOrNull()

    /**
     * 이름 검색(likeItmsNm) 방식으로 현재가 조회 후 티커로 재검증.
     * srtnCd 필터가 엉뚱한 종목을 반환하는 문제를 우회하는 안전한 방식.
     */
    private suspend fun fetchLivePriceByName(ticker: String, stockName: String): StockItem? =
        runCatching {
            withContext(Dispatchers.IO) {
                RetrofitClient.stockApiService.searchByName(
                    serviceKey = KRX_API_KEY,
                    resultType = "json",
                    query      = stockName,
                    numOfRows  = 10
                ).response?.body?.items
                    ?.mapNotNull { it.toStockItem() }
                    ?.find { it.ticker == ticker }
            }
        }.getOrNull()

    /** 최근 검색 추가 (외부 UI에서 직접 호출 가능 — 초기 종목 등록 선택 시) */
    fun addRecentSearch(stock: StockItem) {
        viewModelScope.launch(Dispatchers.IO) {
            StockMasterRepository.addRecentSearch(getApplication(), stock)
            _recentSearches.value = StockMasterRepository.loadRecentSearches(getApplication())
        }
    }

    fun removeRecentSearch(ticker: String) {
        viewModelScope.launch(Dispatchers.IO) {
            StockMasterRepository.removeRecentSearch(getApplication(), ticker)
            _recentSearches.value = StockMasterRepository.loadRecentSearches(getApplication())
        }
    }

    // ── ⚡ 실시간 가격 업데이트 엔진 ─────────────────────────────────────────────

    /** 실시간 조회 우선순위: dynamicPrices(자동갱신 최신) > _livePrices(단발 스냅샷) > marketPrices(스텁) */
    private fun effectivePrice(ticker: String): StockItem? =
        dynamicPrices[ticker] ?: _livePrices.value[ticker] ?: marketPrices[ticker]

    /**
     * 현재 watchlist + holdings 티커의 최신 가격을 API에서 순차 조회하여
     * dynamicPrices 에 업데이트한 뒤 모든 파생 상태를 재산정한다.
     *
     * srtnCd 단독 필터는 서버 캐시/동시 처리 문제로 엉뚱한 종목을 반환할 수 있으므로,
     * 이미 동작이 검증된 이름 검색(likeItmsNm) 방식을 사용하고 티커로 재검증한다.
     */
    fun refreshActiveStockPrices() {
        if (_isPriceRefreshing.value) return
        _isPriceRefreshing.value = true
        viewModelScope.launch {
            try {
                // Main.immediate는 첫 suspension 전까지 동기 실행되므로,
                // yield()로 한 번 양보해 Compose가 isPriceRefreshing=true 상태를 렌더할 시간을 확보
                kotlinx.coroutines.yield()

                // 티커 → 종목명 맵: watchlist·holdings·마스터 순으로 덮어쓰기 (정확도 우선)
                val tickerNameMap = mutableMapOf<String, String>()
                _allStocksMasterList.value.forEach { s -> tickerNameMap[s.ticker] = s.name }
                _watchlist.value.forEach { s -> tickerNameMap[s.ticker] = s.name }
                _holdings.value.forEach  { h -> tickerNameMap[h.stock.ticker] = h.stock.name }

                val holdingTickers = _holdings.value.map { it.stock.ticker }
                val tickers = (cachedWatchlistTickers + holdingTickers).distinct()
                if (tickers.isEmpty()) { return@launch }

                var anyUpdated = false
                for (ticker in tickers) {
                    val name = tickerNameMap[ticker] ?: continue
                    val stock = fetchLivePriceByName(ticker, name)
                    if (stock != null) {
                        dynamicPrices[ticker] = stock
                        anyUpdated = true
                    }
                    delay(150) // API rate-limit 방지용 간격
                }
                if (anyUpdated) recomputeAllPriceDependentState()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 가격 업데이트 실패는 silent — 기존 가격 유지
            } finally {
                _isPriceRefreshing.value = false
            }
        }
    }

    /** dynamicPrices 반영 후 보유·관심·리더보드 StateFlow 재산정 */
    private fun recomputeAllPriceDependentState() {
        val uid = cachedUid ?: return
        val masterList = _allStocksMasterList.value
        val liveMap = _livePrices.value

        _holdings.value = lbAssets[uid]?.third?.mapNotNull { it.toHoldingItem() } ?: emptyList()

        _memberHoldings.value = lbAssets.mapValues { (_, triple) ->
            triple.third.mapNotNull { it.toHoldingItem() }
        }

        _watchlist.value = cachedWatchlistTickers.map { ticker ->
            dynamicPrices[ticker]
                ?: liveMap[ticker]
                ?: masterList.find { it.ticker == ticker }
                ?: marketPrices[ticker]
                ?: StockItem(ticker, ticker, 0L, 0.0)
        }

        recomputeLeaderboard(uid)
    }

    /** HomeScreen / StockSearchScreen 진입 시 10초 자동 갱신 시작 */
    fun startPriceAutoRefresh() {
        if (priceRefreshJob?.isActive == true) return
        priceRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(10_000L)
                refreshActiveStockPrices()
            }
        }
    }

    /** 해당 화면 이탈 시 갱신 중단 */
    fun stopPriceAutoRefresh() {
        priceRefreshJob?.cancel()
        priceRefreshJob = null
    }

    private fun StockItemResponse.toStockItem(): StockItem? {
        val ticker = srtnCd?.trim().takeIf { !it.isNullOrBlank() } ?: return null
        val name   = itmsNm?.trim().takeIf { !it.isNullOrBlank() } ?: return null
        val price  = clpr?.replace(",", "")?.trim()?.toLongOrNull() ?: 0L
        val rate   = fltRt?.replace(",", "")?.trim()?.toDoubleOrNull() ?: 0.0
        return StockItem(ticker, name, price, rate)
    }

    // ── 자녀 연결 승인/거절 ──────────────────────────────────────────────────

    fun approveChildConnection(childUid: String) {
        val familyId = cachedFamilyId ?: return
        viewModelScope.launch {
            try {
                db.runTransaction { tx ->
                    val familyRef = db.collection("families").document(familyId)
                    val snap      = tx.get(familyRef)
                    @Suppress("UNCHECKED_CAST")
                    val pending   = (snap.get("pendingChildren") as? List<Map<String, String>>) ?: emptyList()
                    tx.update(familyRef, "pendingChildren", pending.filter { it["uid"] != childUid })
                }.await()
                db.collection("users").document(childUid)
                    .update("familyId", familyId).await()
                db.collection("families").document(familyId)
                    .collection("childAssets").document(childUid)
                    .set(mapOf("balance" to 0L, "holdingStocks" to emptyList<Any>()), SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                _errorMessage.value = networkSafeMessage(e)
            }
        }
    }

    fun rejectChildConnection(childUid: String) {
        val familyId = cachedFamilyId ?: return
        viewModelScope.launch {
            try {
                db.runTransaction { tx ->
                    val familyRef = db.collection("families").document(familyId)
                    val snap      = tx.get(familyRef)
                    @Suppress("UNCHECKED_CAST")
                    val pending   = (snap.get("pendingChildren") as? List<Map<String, String>>) ?: emptyList()
                    tx.update(familyRef, "pendingChildren", pending.filter { it["uid"] != childUid })
                }.await()
            } catch (e: Exception) {
                _errorMessage.value = networkSafeMessage(e)
            }
        }
    }

    // ── 레거시 호환 위임 함수 ────────────────────────────────────────────────

    fun approveTradeRequest(request: TradeRequest) { acceptTradeRequest(request.requestId) }
    fun rejectTradeRequest(request: TradeRequest)  { unfillTradeRequest(request.requestId) }
    fun loadExistingUser(uid: String)              {}
    fun createParentUser(uid: String, email: String, nickname: String) {}
    fun createChildUser(uid: String, email: String, nickname: String)  {}
    fun sendConnectionRequest(parentUid: String)   {}

    fun searchParentByEmail(email: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val snap = db.collection("users")
                    .whereEqualTo("email", email)
                    .whereEqualTo("role", "PARENT")
                    .get().await()
                if (snap.isEmpty) {
                    _errorMessage.value = "존재하지 않거나 부모로 등록되지 않은 이메일입니다."
                    _searchedParentUid.value = null
                } else {
                    _searchedParentUid.value = snap.documents.first().id
                }
            } catch (e: Exception) {
                _errorMessage.value = networkSafeMessage(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearSearchResult() { _searchedParentUid.value = null }

    fun signOut() {
        clearAll()
        auth.signOut()
    }

    /**
     * Firestore의 모든 가족·유저 데이터와 로컬 설정을 완전히 삭제한 뒤 로그아웃한다.
     * 부모: childAssets·transactionRequests 서브컬렉션 + families 문서 + global_invites 초대코드
     * 자녀: 본인 childAssets 문서만 삭제
     * 공통: users/{uid} 문서, SharedPreferences(커스텀 Firebase 키 + 초대코드)
     */
    fun resetAllData() {
        val uid      = cachedUid      ?: run { signOut(); return }
        val familyId = cachedFamilyId
        val role     = cachedRole

        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (familyId != null) {
                    if (role == UserRole.PARENT) {
                        // 서브컬렉션: childAssets
                        db.collection("families").document(familyId)
                            .collection("childAssets").get().await()
                            .documents.forEach { it.reference.delete().await() }
                        // 서브컬렉션: transactionRequests
                        db.collection("families").document(familyId)
                            .collection("transactionRequests").get().await()
                            .documents.forEach { it.reference.delete().await() }
                        // 가족 마스터 문서
                        db.collection("families").document(familyId).delete().await()
                        // global_invites 초대코드
                        FirebaseConfigManager.loadInviteCode(getApplication())?.let { code ->
                            FirebaseConfigManager.getDefaultDb()
                                .collection("global_invites").document(code).delete().await()
                        }
                    } else {
                        // 자녀: 본인 자산 문서만
                        runCatching {
                            db.collection("families").document(familyId)
                                .collection("childAssets").document(uid).delete().await()
                        }
                    }
                }
                // 유저 문서
                db.collection("users").document(uid).delete().await()
                // 로컬 SharedPreferences (커스텀 Firebase 키 + 초대코드) 전체 삭제
                FirebaseConfigManager.clearConfig(getApplication())
            } catch (e: Exception) {
                _errorMessage.value = networkSafeMessage(e)
            } finally {
                _isLoading.value = false
                clearAll()
                auth.signOut()
            }
        }
    }

    fun clearError() { _errorMessage.value = null }

    /** [Frame 8-1] 대상 자녀 포트폴리오 조회 진입점. 실데이터는 기존 실시간 StateFlow에서 파생. */
    fun fetchTargetUserPortfolio(targetUid: String) {
        _portfolioTargetUid.value = targetUid
    }

    // ── 🔔 FCM 내부 유틸 ─────────────────────────────────────────────────────

    /** FCM 토큰을 비동기 수집하여 users/{uid}.fcmToken에 최신화 */
    private fun refreshFcmToken(uid: String) {
        viewModelScope.launch {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                // update() fails silently on non-existent docs — intentional:
                // avoids creating a stub doc that makes FamilyStockViewModel think the user already exists
                db.collection("users").document(uid)
                    .update("fcmToken", token).await()
            } catch (_: Exception) {}
        }
    }

    /**
     * Cloud Functions HTTP Callable을 통해 recipientUid 기기로 FCM 푸시 알림 발송.
     * - 수신자의 fcmToken을 Firestore에서 조회하여 Cloud Function에 전달
     * - fcmEnabled = false 이면 발송 생략
     * - 발송 실패 시 앱 동작에 영향 없도록 silent fail
     */
    private fun sendFcmToUser(recipientUid: String, title: String, body: String) {
        viewModelScope.launch {
            try {
                val userSnap = db.collection("users").document(recipientUid).get().await()
                val fcmToken  = userSnap.getString("fcmToken") ?: return@launch
                val isEnabled = userSnap.getBoolean("fcmEnabled") ?: true
                if (!isEnabled) return@launch

                // Cloud Function "sendFcmNotification" — 개발자 DEFAULT Firebase 프로젝트에 배포
                // 함수 시그니처: { token: string, title: string, body: string } → void
                functions.getHttpsCallable("sendFcmNotification")
                    .call(mapOf("token" to fcmToken, "title" to title, "body" to body))
                    .await()
            } catch (_: Exception) {
                // FCM 전송 실패는 앱 UX에 영향 없음
            }
        }
    }

    // ── 네트워크 에러 메시지 분류 헬퍼 ──────────────────────────────────────

    private fun networkSafeMessage(e: Exception) = when {
        e is com.google.firebase.FirebaseNetworkException ||
        e.cause is java.net.UnknownHostException ||
        e.cause is java.net.SocketTimeoutException ->
            "네트워크 연결을 확인해 주세요."
        else -> e.message ?: "알 수 없는 오류가 발생했습니다."
    }

    // ── 내부 정리 ─────────────────────────────────────────────────────────────

    private fun clearFamilyListeners() {
        familyListeners.forEach { it.remove() }
        familyListeners.clear()
    }

    private fun clearAll() {
        stopPriceAutoRefresh()
        userDocListener?.remove()
        userDocListener = null
        clearFamilyListeners()
        cachedUid      = null
        cachedFamilyId = null
        cachedAdminUid = null
        lbNicknames.clear()
        lbAssets.clear()
        _currentUser.value   = null
        _familyData.value    = null
        _tradeRequests.value = emptyList()
        _availableCash.value = 0L
        _watchlist.value     = emptyList()
        _holdings.value      = emptyList()
        _searchResults.value = emptyList()
        _leaderboard.value      = emptyList()
        _fcmEnabled.value       = true
        _pendingChildren.value  = emptyList()
        _familyMembers.value    = emptyList()
        _memberBalances.value   = emptyMap()
        _memberHoldings.value   = emptyMap()
        _portfolioTargetUid.value = ""
        _livePrices.value       = emptyMap()
        // _allStocksMasterList, _recentSearches 는 로그아웃 후에도 유지 (재로그인 시 즉시 사용)
    }

    override fun onCleared() {
        super.onCleared()
        authStateListener?.let { auth.removeAuthStateListener(it) }
        clearAll()
    }

    // ── Firestore 문서 → 앱 모델 변환 헬퍼 ──────────────────────────────────

    /** holdingStocks 로우 리스트의 매입 원가 합계: sum(avgPrice × quantity) */
    private fun List<Map<String, Any>>.costBasis(): Long = sumOf { h ->
        val qty = (h["quantity"] as? Long) ?: 0L
        val avg = (h["averagePrice"] as? Double) ?: 0.0
        (avg * qty).toLong()
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any>.toHoldingItem(): HoldingItem? {
        val ticker = this["ticker"] as? String ?: return null
        val name   = this["name"]   as? String ?: ticker
        val qty    = (this["quantity"] as? Long)?.toInt() ?: return null
        if (qty <= 0) return null
        val avgD   = this["averagePrice"] as? Double ?: 0.0
        val avgL   = avgD.toLong()
        val market = effectivePrice(ticker)
        return HoldingItem(
            stock    = StockItem(
                ticker       = ticker,
                name         = name,
                currentPrice = market?.currentPrice ?: avgL,
                changeRate   = market?.changeRate   ?: 0.0
            ),
            quantity = qty,
            avgPrice = avgL
        )
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toTradeRequest(): TradeRequest {
        val typeStr   = getString("type")   ?: "BUY"
        val statusStr = getString("status") ?: "PENDING"
        return TradeRequest(
            requestId         = getString("requestId")        ?: id,
            childUid          = getString("childUid")         ?: "",
            childNickname     = getString("childNickname")    ?: "",
            stockName         = getString("stockName")        ?: "",
            stockTicker       = getString("stockTicker")      ?: "",
            quantity          = (getLong("quantity")          ?: 0L).toInt(),
            pricePerShare     = getLong("pricePerShare")      ?: 0L,
            memo              = getString("memo")             ?: "",
            type              = when (typeStr) {
                "INTEREST"   -> TradeType.INTEREST
                "DIVIDEND"   -> TradeType.DIVIDEND
                "WITHDRAWAL" -> TradeType.WITHDRAWAL
                "SELL"       -> TradeType.SELL
                else         -> TradeType.BUY
            },
            status            = when (statusStr) {
                "ACCEPTED"       -> TradeStatus.ACCEPTED
                "FILLED"         -> TradeStatus.FILLED
                "PARTIAL_FILLED" -> TradeStatus.PARTIAL_FILLED
                "UNFILLED"       -> TradeStatus.UNFILLED
                else             -> TradeStatus.PENDING
            },
            timestamp         = getLong("timestamp")          ?: 0L,
            failReason        = getString("failReason")       ?: "",
            filledPrice       = getLong("filledPrice")        ?: 0L,
            filledQuantity    = (getLong("filledQuantity")    ?: 0L).toInt(),
            completedAt       = getLong("completedAt")        ?: 0L,
            remainingQuantity = (getLong("remainingQuantity") ?: 0L).toInt()
        )
    }
}
