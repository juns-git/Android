package io.github.juns_git.familystockgate.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.juns_git.familystockgate.data.model.FamilyData
import io.github.juns_git.familystockgate.data.model.HoldingItem
import io.github.juns_git.familystockgate.data.model.StockItem
import io.github.juns_git.familystockgate.data.model.TradeRequest
import io.github.juns_git.familystockgate.data.model.TradeStatus
import io.github.juns_git.familystockgate.data.model.TradeType
import io.github.juns_git.familystockgate.data.model.UserData
import io.github.juns_git.familystockgate.data.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppViewModel : ViewModel() {

    // ──────────────────────────────────────────────
    // 전역 상태 (StateFlow)
    // ──────────────────────────────────────────────

    private val _currentUser = MutableStateFlow<UserData?>(null)
    val currentUser: StateFlow<UserData?> = _currentUser.asStateFlow()

    private val _familyData = MutableStateFlow<FamilyData?>(null)
    val familyData: StateFlow<FamilyData?> = _familyData.asStateFlow()

    private val _tradeRequests = MutableStateFlow<List<TradeRequest>>(
        listOf(
            TradeRequest(
                requestId = "r1", childUid = "c1", childNickname = "홍길동",
                stockName = "삼성전자", stockTicker = "005930",
                quantity = 10, pricePerShare = 79500,
                memo = "삼성전자는 글로벌 반도체 1위 기업입니다. AI 수요 증가로 HBM 메모리 수요가 급증하고 있어 향후 실적 개선이 기대됩니다.",
                type = TradeType.BUY, status = TradeStatus.PENDING,
                timestamp = System.currentTimeMillis() - 600_000
            ),
            TradeRequest(
                requestId = "r2", childUid = "c2", childNickname = "김철수",
                stockName = "NAVER", stockTicker = "035420",
                quantity = 2, pricePerShare = 195000,
                memo = "NAVER는 국내 최대 포털 플랫폼으로 AI 검색 서비스 도입으로 경쟁력이 강화될 것입니다.",
                type = TradeType.BUY, status = TradeStatus.ACCEPTED,
                timestamp = System.currentTimeMillis() - 3_600_000
            ),
            TradeRequest(
                requestId = "r3", childUid = "c1", childNickname = "홍길동",
                stockName = "카카오", stockTicker = "035720",
                quantity = 5, pricePerShare = 55000,
                memo = "카카오 핀테크 부문 성장으로 장기적 가치 상승이 예상됩니다.",
                type = TradeType.SELL, status = TradeStatus.FILLED,
                timestamp = System.currentTimeMillis() - 86_400_000,
                filledPrice = 54500, filledQuantity = 5,
                completedAt = System.currentTimeMillis() - 82_800_000
            ),
            TradeRequest(
                requestId = "r4", childUid = "c2", childNickname = "김철수",
                stockName = "현대차", stockTicker = "005380",
                quantity = 3, pricePerShare = 215000,
                memo = "전기차 전환 가속화로 현대차 글로벌 점유율이 상승할 것입니다.",
                type = TradeType.BUY, status = TradeStatus.UNFILLED,
                timestamp = System.currentTimeMillis() - 172_800_000,
                failReason = "시장가 미달로 체결 실패",
                completedAt = System.currentTimeMillis() - 169_200_000
            )
        )
    )
    val tradeRequests: StateFlow<List<TradeRequest>> = _tradeRequests.asStateFlow()

    // 현재 자녀의 대리 투자 가능 예수금 (부모가 설정)
    private val _availableCash = MutableStateFlow(500_000L)
    val availableCash: StateFlow<Long> = _availableCash.asStateFlow()

    // 대리 거래 수수료 및 세금 설정 (부모가 Settings 화면에서 설정)
    private val _commissionRate = MutableStateFlow(0.015)
    val commissionRate: StateFlow<Double> = _commissionRate.asStateFlow()

    private val _taxRate = MutableStateFlow(0.18)
    val taxRate: StateFlow<Double> = _taxRate.asStateFlow()

    // 관심 종목 (Watchlist)
    private val _watchlist = MutableStateFlow<List<StockItem>>(
        listOf(
            StockItem("005930", "삼성전자", 79500, 1.23),
            StockItem("035420", "NAVER", 195000, -0.78)
        )
    )
    val watchlist: StateFlow<List<StockItem>> = _watchlist.asStateFlow()

    // 보유 종목 (Holdings) — 부모가 승인한 체결 내역에서 자동 계산
    private val _holdings = MutableStateFlow<List<HoldingItem>>(
        listOf(
            HoldingItem(StockItem("000660", "SK하이닉스", 165000, 2.45), quantity = 3, avgPrice = 155000),
            HoldingItem(StockItem("035720", "카카오", 55000, 0.34), quantity = 10, avgPrice = 60000)
        )
    )
    val holdings: StateFlow<List<HoldingItem>> = _holdings.asStateFlow()

    // 종목 검색 결과
    private val _searchResults = MutableStateFlow<List<StockItem>>(emptyList())
    val searchResults: StateFlow<List<StockItem>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // 이메일 검색 결과 (uid)
    private val _searchedParentUid = MutableStateFlow<String?>(null)
    val searchedParentUid: StateFlow<String?> = _searchedParentUid.asStateFlow()

    // ──────────────────────────────────────────────
    // [DEBUG] 테스트용 역할 스위치
    // 실제 Firebase 연동 후에는 _currentUser.value?.role 로 대체
    // ──────────────────────────────────────────────

    private val _debugRole = MutableStateFlow(UserRole.PARENT)
    val debugRole: StateFlow<UserRole> = _debugRole.asStateFlow()

    fun toggleDebugRole() {
        _debugRole.value = if (_debugRole.value == UserRole.PARENT) UserRole.CHILD else UserRole.PARENT
    }

    // ──────────────────────────────────────────────
    // Firebase 연동 스텁 함수
    // ──────────────────────────────────────────────

    /**
     * [기존 유저 로딩]
     * Firebase Auth onAuthStateChanged 에서 uid를 받아 호출한다.
     * Firestore `users/{uid}` 문서를 읽어 _currentUser를 업데이트하고,
     * familyId가 있으면 families/{familyId} 도 실시간 구독한다.
     */
    fun loadExistingUser(uid: String) {
        viewModelScope.launch {
            _isLoading.value = true
            // TODO: val snapshot = Firebase.firestore
            //           .collection("users").document(uid).get().await()
            // TODO: val user = snapshot.toObject<UserData>() ?: run {
            //           _isLoading.value = false; return@launch
            //       }
            // TODO: _currentUser.value = user
            // TODO: if (user.familyId != null) subscribeToFamilyData(user.familyId)

            // 테스트용 더미 데이터
            _currentUser.value = UserData(
                uid = uid,
                email = "parent@example.com",
                nickname = "테스트 부모",
                role = _debugRole.value,
                familyId = "dummy-family-001"
            )
            _isLoading.value = false
        }
    }

    /**
     * [신규 부모 계정 생성]
     * 구글 로그인 후 부모 역할을 선택하면 호출된다.
     * 1) UUID로 고유 familyId 생성
     * 2) Firestore에 users/{uid} + families/{familyId} 문서를 배치 생성
     */
    fun createParentUser(uid: String, email: String, nickname: String) {
        viewModelScope.launch {
            _isLoading.value = true
            // TODO: val familyId = UUID.randomUUID().toString()
            // TODO: val newUser = UserData(uid, email, nickname, UserRole.PARENT, familyId)
            // TODO: val newFamily = FamilyData(familyId, adminUid = uid)
            // TODO: Firebase.firestore.runBatch { batch ->
            //           batch.set(usersRef.document(uid), newUser)
            //           batch.set(familiesRef.document(familyId), newFamily)
            //       }.await()
            // TODO: _currentUser.value = newUser
            // TODO: _familyData.value = newFamily
            _isLoading.value = false
        }
    }

    /**
     * [신규 자녀 계정 생성]
     * 자녀 역할 선택 후 호출. familyId는 null로 초기화 (연결 전 상태).
     */
    fun createChildUser(uid: String, email: String, nickname: String) {
        viewModelScope.launch {
            _isLoading.value = true
            // TODO: val newUser = UserData(uid, email, nickname, UserRole.CHILD, null)
            // TODO: Firebase.firestore.collection("users").document(uid).set(newUser).await()
            // TODO: _currentUser.value = newUser
            _isLoading.value = false
        }
    }

    /**
     * [이메일로 부모 검색]
     * Firestore `users` 컬렉션에서 email + role=PARENT 조건으로 쿼리한다.
     * 검색 결과는 _searchedParentUid StateFlow를 통해 UI에 노출된다.
     */
    fun searchParentByEmail(email: String) {
        viewModelScope.launch {
            _isLoading.value = true
            // TODO: val result = Firebase.firestore.collection("users")
            //           .whereEqualTo("email", email)
            //           .whereEqualTo("role", "PARENT")
            //           .get().await()
            // TODO: _searchedParentUid.value = result.documents.firstOrNull()?.id

            // 테스트용 더미값 (이메일 입력 시 항상 부모를 찾은 것처럼 처리)
            _searchedParentUid.value = if (email.contains("@")) "dummy-parent-uid" else null
            _isLoading.value = false
        }
    }

    fun clearSearchResult() {
        _searchedParentUid.value = null
    }

    /**
     * [자녀 연결 요청 전송]
     * 부모의 families/{familyId}.pendingChildren 배열에 자녀 uid를 추가한다.
     * 완료 후 부모에게 FCM 알림을 전송한다 (Cloud Functions 경유).
     */
    fun sendConnectionRequest(parentUid: String) {
        viewModelScope.launch {
            _isLoading.value = true
            // TODO: val parentDoc = Firebase.firestore
            //           .collection("users").document(parentUid).get().await()
            // TODO: val parentFamilyId = parentDoc.getString("familyId") ?: return@launch
            // TODO: Firebase.firestore.collection("families").document(parentFamilyId)
            //           .update("pendingChildren", FieldValue.arrayUnion(currentUser.value?.uid)).await()
            // TODO: sendFcmViaCloudFunction(
            //           targetUid = parentUid,
            //           title = "가족 연결 요청",
            //           body = "${currentUser.value?.nickname}이(가) 연결을 요청했습니다."
            //       )
            _isLoading.value = false
        }
    }

    /**
     * [자녀 연결 승인 - 부모]
     * 1) families/{familyId}.pendingChildren 에서 childUid 제거
     * 2) users/{childUid}.familyId 를 현재 familyId로 업데이트 (배치 처리)
     * 3) 자녀에게 FCM 알림 전송
     */
    fun approveChildConnection(childUid: String) {
        viewModelScope.launch {
            val familyId = _familyData.value?.familyId ?: return@launch
            // TODO: val batch = Firebase.firestore.batch()
            // TODO: batch.update(familiesRef.document(familyId),
            //           "pendingChildren", FieldValue.arrayRemove(childUid))
            // TODO: batch.update(usersRef.document(childUid), "familyId", familyId)
            // TODO: batch.commit().await()
            // TODO: sendFcmViaCloudFunction(
            //           targetUid = childUid,
            //           title = "연결 승인",
            //           body = "부모님이 연결을 승인했습니다. 이제 주식 투자를 시작할 수 있습니다!"
            //       )
        }
    }

    /**
     * [자녀 연결 거절 - 부모]
     */
    fun rejectChildConnection(childUid: String) {
        viewModelScope.launch {
            val familyId = _familyData.value?.familyId ?: return@launch
            // TODO: Firebase.firestore.collection("families").document(familyId)
            //           .update("pendingChildren", FieldValue.arrayRemove(childUid)).await()
            // TODO: sendFcmViaCloudFunction(childUid, "연결 거절", "연결 요청이 거절되었습니다.")
        }
    }

    /**
     * [거래 요청 제출 - 자녀]
     * families/{familyId}/tradeRequests 서브컬렉션에 요청 문서를 추가하고
     * 부모에게 FCM 푸시 알림을 발송한다.
     */
    fun submitTradeRequest(
        stockName: String,
        ticker: String,
        quantity: Int,
        price: Long,
        memo: String,
        type: TradeType
    ) {
        viewModelScope.launch {
            val user = _currentUser.value ?: return@launch
            val familyId = user.familyId ?: return@launch
            val adminUid = _familyData.value?.adminUid ?: return@launch

            // TODO: val requestId = UUID.randomUUID().toString()
            // TODO: val request = TradeRequest(
            //           requestId, user.uid, user.nickname,
            //           stockName, ticker, quantity, price, memo, type,
            //           TradeStatus.PENDING, System.currentTimeMillis()
            //       )
            // TODO: Firebase.firestore.collection("families").document(familyId)
            //           .collection("tradeRequests").document(requestId).set(request).await()
            // TODO: val typeLabel = if (type == TradeType.BUY) "매수" else "매도"
            // TODO: sendFcmViaCloudFunction(
            //           adminUid, "거래 승인 요청",
            //           "${user.nickname}이(가) $stockName $typeLabel 요청을 보냈습니다."
            //       )
        }
    }

    /**
     * [거래 요청 승인 - 부모]
     * 1) tradeRequest.status 를 APPROVED로 변경
     * 2) familyCash.{childUid} 잔액을 거래 금액만큼 차감/증가 (배치 처리)
     * 3) 자녀에게 FCM 알림 전송
     */
    fun approveTradeRequest(request: TradeRequest) {
        viewModelScope.launch {
            val familyId = _familyData.value?.familyId ?: return@launch
            // TODO: val totalAmount = request.quantity * request.pricePerShare
            // TODO: val cashDelta = if (request.type == TradeType.BUY) -totalAmount else totalAmount
            // TODO: val batch = Firebase.firestore.batch()
            // TODO: batch.update(tradeRequestRef, "status", TradeStatus.APPROVED.name)
            // TODO: batch.update(familiesRef.document(familyId),
            //           "familyCash.${request.childUid}", FieldValue.increment(cashDelta))
            // TODO: batch.commit().await()
            // TODO: val typeLabel = if (request.type == TradeType.BUY) "매수" else "매도"
            // TODO: sendFcmViaCloudFunction(
            //           request.childUid, "거래 승인",
            //           "부모님이 ${request.stockName} $typeLabel 요청을 승인했습니다."
            //       )
        }
    }

    /**
     * [거래 요청 거절 - 부모]
     */
    fun rejectTradeRequest(request: TradeRequest) {
        viewModelScope.launch {
            val familyId = _familyData.value?.familyId ?: return@launch
            // TODO: tradeRequestsRef.document(request.requestId)
            //           .update("status", TradeStatus.REJECTED.name).await()
            // TODO: val typeLabel = if (request.type == TradeType.BUY) "매수" else "매도"
            // TODO: sendFcmViaCloudFunction(
            //           request.childUid, "거래 거절",
            //           "부모님이 ${request.stockName} $typeLabel 요청을 거절했습니다."
            //       )
        }
    }

    /**
     * [자녀 예수금 수정 - 부모]
     * families/{familyId}.familyCash.{childUid} 를 newAmount로 덮어쓴다.
     * 자녀에게 FCM 알림을 발송한다.
     */
    fun updateChildCash(childUid: String, newAmount: Long) {
        _availableCash.value = newAmount
        viewModelScope.launch {
            val familyId = _familyData.value?.familyId ?: return@launch
            // TODO: Firebase.firestore.collection("families").document(familyId)
            //           .update("familyCash.$childUid", newAmount).await()
            // TODO: sendFcmViaCloudFunction(
            //           childUid, "예수금 변경",
            //           "부모님이 예수금을 ${newAmount}원으로 수정했습니다."
            //       )
        }
    }

    fun updateCommissionRate(rate: Double) { _commissionRate.value = rate }

    fun updateTaxRate(rate: Double) { _taxRate.value = rate }

    fun addInitialHolding(holding: HoldingItem) {
        val existing = _holdings.value.find { it.stock.ticker == holding.stock.ticker }
        _holdings.value = if (existing != null) {
            _holdings.value.map { if (it.stock.ticker == holding.stock.ticker) holding else it }
        } else {
            _holdings.value + holding
        }
        // TODO: Firestore initial holdings upsert
    }

    fun removeInitialHolding(ticker: String) {
        _holdings.value = _holdings.value.filter { it.stock.ticker != ticker }
        // TODO: Firestore initial holdings delete
    }

    /**
     * [가족 데이터 실시간 구독]
     * addSnapshotListener로 변경 사항을 자동으로 수신한다.
     * ViewModel이 소멸될 때 리스너를 해제해야 한다 (onCleared 참고).
     */
    private fun subscribeToFamilyData(familyId: String) {
        // TODO: val listener = Firebase.firestore.collection("families").document(familyId)
        //           .addSnapshotListener { snapshot, error ->
        //               if (error != null) { _errorMessage.value = error.message; return@addSnapshotListener }
        //               _familyData.value = snapshot?.toObject<FamilyData>()
        //           }
        // TODO: onCleared 시 listener.remove() 호출하여 메모리 누수 방지
    }

    /**
     * [FCM 푸시 알림 전송 - 서버 사이드]
     * 클라이언트에서 FCM 서버 키를 직접 사용하면 보안 위반.
     * 반드시 Firebase Cloud Functions HTTP Callable 함수를 통해 서버에서 전송해야 한다.
     */
    private suspend fun sendFcmViaCloudFunction(targetUid: String, title: String, body: String) {
        // TODO: val functions = Firebase.functions("asia-northeast3")
        // TODO: val payload = hashMapOf("targetUid" to targetUid, "title" to title, "body" to body)
        // TODO: functions.getHttpsCallable("sendPushNotification").call(payload).await()
    }

    /**
     * [관심 종목 추가]
     * 이미 등록된 ticker는 중복 추가하지 않는다.
     * Firestore: families/{familyId}/watchlist/{ticker} 문서 set
     */
    fun addToWatchlist(stock: StockItem) {
        if (_watchlist.value.any { it.ticker == stock.ticker }) return
        _watchlist.value = _watchlist.value + stock
        viewModelScope.launch {
            // TODO: val familyId = _familyData.value?.familyId ?: return@launch
            // TODO: Firebase.firestore.collection("families").document(familyId)
            //           .collection("watchlist").document(stock.ticker).set(stock).await()
        }
    }

    /**
     * [관심 종목 삭제]
     * Firestore watchlist 컬렉션에서 해당 ticker 문서 삭제.
     */
    fun removeFromWatchlist(ticker: String) {
        _watchlist.value = _watchlist.value.filter { it.ticker != ticker }
        viewModelScope.launch {
            // TODO: val familyId = _familyData.value?.familyId ?: return@launch
            // TODO: Firebase.firestore.collection("families").document(familyId)
            //           .collection("watchlist").document(ticker).delete().await()
        }
    }

    /**
     * [종목 검색]
     * 실제 구현 시 한국투자증권 KIS Developers API 또는 자체 프록시 서버를 통해 호출한다.
     * query가 비어 있으면 결과를 초기화한다.
     * Firestore나 별도 검색 인덱스(Algolia 등)를 활용해도 좋다.
     */
    fun searchStocks(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            // TODO: val response = stockApiService.search(query)
            // TODO: _searchResults.value = response.items.map { StockItem(it.ticker, it.name, it.price, it.changeRate) }

            // 테스트용 더미 검색 데이터
            val all = listOf(
                StockItem("005930", "삼성전자", 79500, 1.23),
                StockItem("000660", "SK하이닉스", 165000, 2.45),
                StockItem("035420", "NAVER", 195000, -0.78),
                StockItem("035720", "카카오", 55000, 0.34),
                StockItem("005380", "현대차", 215000, -1.12),
                StockItem("051910", "LG화학", 320000, 0.63),
                StockItem("006400", "삼성SDI", 280000, -0.54),
                StockItem("373220", "LG에너지솔루션", 380000, 1.05),
                StockItem("207940", "삼성바이오로직스", 920000, 0.22)
            )
            _searchResults.value = all.filter {
                it.name.contains(query, ignoreCase = true) || it.ticker.contains(query)
            }
        }
    }

    fun clearSearchResults() {
        _searchResults.value = emptyList()
    }

    /**
     * [1단계 접수 - 부모]
     * 부모가 [접수] 버튼을 누르면 PENDING → ACCEPTED 로 상태 전환.
     * Firestore: tradeRequests/{id}.status = "ACCEPTED" 업데이트.
     */
    fun acceptTradeRequest(requestId: String) {
        _tradeRequests.value = _tradeRequests.value.map { req ->
            if (req.requestId == requestId) req.copy(status = TradeStatus.ACCEPTED) else req
        }
        viewModelScope.launch {
            // TODO: Firebase.firestore.collection("families").document(familyId)
            //           .collection("tradeRequests").document(requestId)
            //           .update("status", TradeStatus.ACCEPTED.name).await()
        }
    }

    /**
     * [2단계 체결 등록 - 부모]
     * 실제 증권사에서 체결된 단가/수량을 입력 후 ACCEPTED → FILLED 로 상태 전환.
     * 체결 수량·평단가로 보유 종목(holdings)을 갱신한다.
     * Firestore: tradeRequests/{id} 업데이트 + families/{id}/holdings 반영.
     */
    fun fillTradeRequest(requestId: String, filledPrice: Long, filledQuantity: Int) {
        val now = System.currentTimeMillis()
        val commRate = _commissionRate.value
        val txRate = _taxRate.value

        val request = _tradeRequests.value.find { it.requestId == requestId } ?: return
        val isPartial = filledQuantity in 1 until request.quantity

        val updatedList = _tradeRequests.value.map { req ->
            if (req.requestId == requestId) req.copy(
                status = if (isPartial) TradeStatus.PARTIAL_FILLED else TradeStatus.FILLED,
                filledPrice = filledPrice,
                filledQuantity = filledQuantity,
                remainingQuantity = if (isPartial) request.quantity - filledQuantity else 0,
                completedAt = now
            ) else req
        }
        _tradeRequests.value = if (isPartial) {
            val remainingRequest = request.copy(
                requestId = "${requestId}_r${now}",
                quantity = request.quantity - filledQuantity,
                status = TradeStatus.ACCEPTED,
                filledPrice = 0L, filledQuantity = 0, remainingQuantity = 0,
                completedAt = 0L, timestamp = now
            )
            updatedList + remainingRequest
        } else updatedList

        val tradeCost = filledPrice * filledQuantity
        val fee = (tradeCost * (commRate + txRate) / 100.0).toLong()
        if (request.type == TradeType.BUY) {
            _availableCash.value -= (tradeCost + fee)
            val existing = _holdings.value.find { it.stock.ticker == request.stockTicker }
            _holdings.value = if (existing != null) {
                val newQty = existing.quantity + filledQuantity
                val newAvg = (existing.avgPrice * existing.quantity + filledPrice * filledQuantity) / newQty
                _holdings.value.map { h ->
                    if (h.stock.ticker == request.stockTicker) h.copy(quantity = newQty, avgPrice = newAvg) else h
                }
            } else {
                _holdings.value + HoldingItem(
                    StockItem(request.stockTicker, request.stockName, filledPrice, 0.0),
                    filledQuantity, filledPrice
                )
            }
        } else {
            _availableCash.value += (tradeCost - fee)
            val existing = _holdings.value.find { it.stock.ticker == request.stockTicker }
            if (existing != null) {
                val newQty = existing.quantity - filledQuantity
                _holdings.value = if (newQty <= 0) {
                    _holdings.value.filter { it.stock.ticker != request.stockTicker }
                } else {
                    _holdings.value.map { h ->
                        if (h.stock.ticker == request.stockTicker) h.copy(quantity = newQty) else h
                    }
                }
            }
        }
        viewModelScope.launch {
            // TODO: Firestore batch: tradeRequest 상태 + holdings + familyCash 동시 업데이트
            // TODO: sendFcmViaCloudFunction(childUid, "체결 완료", "...")
        }
    }

    /**
     * [2단계 미체결 처리 - 부모]
     * 증권사에서 체결 실패 시 사유를 입력 후 ACCEPTED → UNFILLED 로 상태 전환.
     * Firestore: tradeRequests/{id}.status = "UNFILLED" + failReason 업데이트.
     */
    fun unfillTradeRequest(requestId: String) {
        val now = System.currentTimeMillis()
        _tradeRequests.value = _tradeRequests.value.map { req ->
            if (req.requestId == requestId)
                req.copy(status = TradeStatus.UNFILLED, completedAt = now)
            else req
        }
        viewModelScope.launch {
            // TODO: Firebase.firestore...tradeRequests/{requestId}
            //           .update(mapOf("status" to "UNFILLED", "completedAt" to now)).await()
            // TODO: sendFcmViaCloudFunction(childUid, "미체결", "거래가 체결되지 않았습니다.")
        }
    }

    fun signOut() {
        // TODO: Firebase.auth.signOut()
        _currentUser.value = null
        _familyData.value = null
        _tradeRequests.value = emptyList()
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
