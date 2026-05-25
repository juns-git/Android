package io.github.juns_git.familystockgate.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ════════════════════════════════════════════════════════════════════════════
//  SECTION 1 — Data Models
//  UI 컴포저블은 이 파일의 클래스를 직접 import하여 관찰(observe)합니다.
// ════════════════════════════════════════════════════════════════════════════

enum class UserRole { PARENT, CHILD }

/** 로그인한 유저의 상태 스냅샷 */
data class UserState(
    val uid: String,
    val email: String,
    val nickname: String,
    val role: UserRole,
    val familyId: String?   // 부모 가입 시 UUID 발급 / 자녀는 부모 승인 후 할당
)

/** 마켓 데이터 및 검색 결과용 종목 마스터 */
data class StockItem(
    val ticker: String,
    val name: String,
    val currentPrice: Long,
    val rate: Double        // 실시간 등락률 %, 양수=상승 / 음수=하락
)

/** 자녀별 실제 보유 주식 잔고 */
data class HoldingStock(
    val ticker: String,
    val name: String,
    val quantity: Int,
    val averagePrice: Double    // 수수료·세금이 모두 포함된 실질 매수 평단가
)

/** 거래 요청 워크플로 상태 */
enum class RequestStatus { PENDING, ACCEPTED, COMPLETED, FAILED }

/**
 * 대리 거래 요청 및 장부 카드 단위.
 * 부분 체결 시 잔여 수량은 동일 구조의 새 PENDING 카드로 분리됩니다.
 */
data class TransactionRequest(
    val id: String,
    val childUid: String,
    val childNickname: String,
    val type: String,               // "BUY" | "SELL"
    val ticker: String,
    val name: String,
    val requestPrice: Long,         // 자녀가 희망한 지정가
    val requestQuantity: Int,       // 자녀가 희망한 수량
    val memo: String,               // 투자 근거 (최소 10자 검증)
    val status: RequestStatus,
    val timestamp: Long,            // 상태 마지막 변경 일시 (ms)
    val actualPrice: Long = 0L,     // 부모가 실제 체결시킨 단가
    val actualQuantity: Int = 0     // 부모가 실제 체결시킨 수량
)

/** 부모 증권사 실제 비용 설정 */
data class FeeSettings(
    val brokerFeeRate: Double = 0.0,    // 체결 수수료 % (예: 0.015)
    val tradeTaxRate: Double = 0.0      // 거래 세금 %   (예: 0.18)
)

// ════════════════════════════════════════════════════════════════════════════
//  SECTION 2 — FamilyStockViewModel  (중앙 제어 장치)
// ════════════════════════════════════════════════════════════════════════════

class FamilyStockViewModel : ViewModel() {

    // ────────────────────────────────────────────────────────────────────────
    //  더미 마켓 인덱스 (실제 API 연동 전까지 사용)
    // ────────────────────────────────────────────────────────────────────────

    private val stockMarketIndex: Map<String, StockItem> = mapOf(
        "005930" to StockItem("005930", "삼성전자",    79_500L,  1.23),
        "035720" to StockItem("035720", "카카오",      55_000L,  0.34),
        "AAPL"   to StockItem("AAPL",   "애플",       243_000L,  0.85),
        "000660" to StockItem("000660", "SK하이닉스", 168_000L,  2.45),
        "035420" to StockItem("035420", "NAVER",      192_000L, -0.78),
        "005380" to StockItem("005380", "현대차",     215_000L, -1.12)
    )

    // ────────────────────────────────────────────────────────────────────────
    //  전역 상태 (StateFlow)
    // ────────────────────────────────────────────────────────────────────────

    /** 현재 로그인한 유저 */
    private val _currentUser = MutableStateFlow<UserState?>(null)
    val currentUser: StateFlow<UserState?> = _currentUser.asStateFlow()

    /** PARENT ↔ CHILD 화면 분기 수동 토글 (개발·테스트 전용) */
    private val _currentRoleMode = MutableStateFlow(UserRole.PARENT)
    val currentRoleMode: StateFlow<UserRole> = _currentRoleMode.asStateFlow()

    /** CHILD 모드 테스트 시 사용 중인 자녀 UID */
    private val _activeChildUid = MutableStateFlow("child_001")
    val activeChildUid: StateFlow<String> = _activeChildUid.asStateFlow()

    /** 자녀 UID → 예수금 잔액 */
    private val _childAssets = MutableStateFlow<Map<String, Long>>(emptyMap())
    val childAssets: StateFlow<Map<String, Long>> = _childAssets.asStateFlow()

    /** 자녀 UID → 보유 주식 리스트 */
    private val _childStocks = MutableStateFlow<Map<String, List<HoldingStock>>>(emptyMap())
    val childStocks: StateFlow<Map<String, List<HoldingStock>>> = _childStocks.asStateFlow()

    /** 가족 공용 관심 종목 티커 리스트 */
    private val _familyWatchList = MutableStateFlow<List<String>>(emptyList())
    val familyWatchList: StateFlow<List<String>> = _familyWatchList.asStateFlow()

    /** 가족 내 전체 거래 요청 장부 */
    private val _transactionRequests = MutableStateFlow<List<TransactionRequest>>(emptyList())
    val transactionRequests: StateFlow<List<TransactionRequest>> = _transactionRequests.asStateFlow()

    /** 부모 증권사 비용 설정 */
    private val _feeSettings = MutableStateFlow(FeeSettings())
    val feeSettings: StateFlow<FeeSettings> = _feeSettings.asStateFlow()

    // ────────────────────────────────────────────────────────────────────────
    //  ① Init — 테스트용 더미 데이터 적재
    // ────────────────────────────────────────────────────────────────────────

    init {
        loadDummyData()
    }

    private fun loadDummyData() {
        _currentUser.value = UserState(
            uid       = "parent_001",
            email     = "parent@familystock.app",
            nickname  = "테스트 부모",
            role      = UserRole.PARENT,
            familyId  = "family_001"
        )

        // 자녀 2명 예수금
        _childAssets.value = mapOf(
            "child_001" to 1_200_000L,
            "child_002" to 500_000L
        )

        // 자녀별 초기 보유 주식
        _childStocks.value = mapOf(
            "child_001" to listOf(
                HoldingStock("005930", "삼성전자", 5, 72_000.0),
                HoldingStock("035420", "NAVER",   2, 188_000.0)
            ),
            "child_002" to listOf(
                HoldingStock("035720", "카카오", 10, 52_000.0)
            )
        )

        // 관심 종목 3개 (삼성전자 / 카카오 / 애플)
        _familyWatchList.value = listOf("005930", "035720", "AAPL")

        // 수수료·세금 기본값
        _feeSettings.value = FeeSettings(brokerFeeRate = 0.015, tradeTaxRate = 0.18)

        // 대기 중인 거래 요청 2건
        val now = System.currentTimeMillis()
        _transactionRequests.value = listOf(
            TransactionRequest(
                id              = "req_001",
                childUid        = "child_001",
                childNickname   = "홍길동",
                type            = "BUY",
                ticker          = "000660",
                name            = "SK하이닉스",
                requestPrice    = 168_000L,
                requestQuantity = 3,
                memo            = "AI 수요 증가로 HBM 메모리 수요가 폭발적으로 늘어나고 있어 향후 실적 개선이 기대됩니다.",
                status          = RequestStatus.PENDING,
                timestamp       = now - 600_000L
            ),
            TransactionRequest(
                id              = "req_002",
                childUid        = "child_002",
                childNickname   = "김영희",
                type            = "SELL",
                ticker          = "035720",
                name            = "카카오",
                requestPrice    = 55_000L,
                requestQuantity = 3,
                memo            = "목표 수익률 달성으로 일부 차익 실현하려 합니다. 포트폴리오 리밸런싱 목적입니다.",
                status          = RequestStatus.PENDING,
                timestamp       = now - 1_800_000L
            )
        )
    }

    // ────────────────────────────────────────────────────────────────────────
    //  역할 토글 (개발 전용)
    // ────────────────────────────────────────────────────────────────────────

    fun toggleRoleMode() {
        _currentRoleMode.value =
            if (_currentRoleMode.value == UserRole.PARENT) UserRole.CHILD else UserRole.PARENT
    }

    /** CHILD 모드 테스트 시 어느 자녀의 관점으로 볼지 선택 */
    fun setActiveChild(childUid: String) {
        _activeChildUid.value = childUid
    }

    // ────────────────────────────────────────────────────────────────────────
    //  마켓 헬퍼
    // ────────────────────────────────────────────────────────────────────────

    /** 티커로 StockItem 조회 (UI의 watchlist 티커 → 표시 정보 변환에 사용) */
    fun getStockInfo(ticker: String): StockItem? = stockMarketIndex[ticker]

    /** watchlist 티커 리스트를 StockItem 리스트로 변환 */
    fun resolvedWatchlistStocks(): List<StockItem> =
        _familyWatchList.value.mapNotNull { stockMarketIndex[it] }

    // ────────────────────────────────────────────────────────────────────────
    //  ② 관리자 전용 — 자산 관리 제어판 (Frame 9)
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 특정 자녀의 예수금 잔액을 부모가 강제 수정.
     * Firestore: families/{familyId}/childAssets/{childUid}
     */
    fun updateChildAssetBalance(childUid: String, newBalance: Long) {
        _childAssets.value = _childAssets.value.toMutableMap().apply {
            put(childUid, newBalance)
        }
        // TODO: Firebase.firestore.collection("families").document(familyId)
        //           .collection("childAssets").document(childUid)
        //           .set(mapOf("balance" to newBalance)).await()
    }

    /**
     * 앱 최초 구동 시 자녀의 기존 보유 주식을 부모가 초기 입력.
     * 동일 티커가 이미 있으면 전체 교체 (초기 세팅이므로 override).
     * 이후 주식 변동은 completeTransaction()으로만 반영됩니다.
     */
    fun setChildInitialStock(childUid: String, stock: HoldingStock) {
        val map   = _childStocks.value.toMutableMap()
        val list  = map.getOrDefault(childUid, emptyList()).toMutableList()
        val idx   = list.indexOfFirst { it.ticker == stock.ticker }
        if (idx >= 0) list[idx] = stock else list.add(stock)
        map[childUid] = list
        _childStocks.value = map
        // TODO: Firestore families/{familyId}/childStocks/{childUid} upsert
    }

    // ────────────────────────────────────────────────────────────────────────
    //  ③ 비용 설정 (Frame 9)
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 부모 증권사 실제 수수료율과 거래세율을 저장.
     * 이후 completeTransaction() 정산 시 자동 적용됩니다.
     */
    fun updateFeeSettings(feeRate: Double, taxRate: Double) {
        _feeSettings.value = FeeSettings(brokerFeeRate = feeRate, tradeTaxRate = taxRate)
        // TODO: Firestore families/{familyId}/settings 업데이트
    }

    // ────────────────────────────────────────────────────────────────────────
    //  ④ 관심 종목 토글 (Frame 2, 3, 3-1)
    // ────────────────────────────────────────────────────────────────────────

    /** 관심 종목 추가/해제 토글. 이미 있으면 제거, 없으면 추가. */
    fun toggleWatchList(ticker: String) {
        val list = _familyWatchList.value.toMutableList()
        if (ticker in list) list.remove(ticker) else list.add(ticker)
        _familyWatchList.value = list
        // TODO: Firestore families/{familyId}/watchlist 업데이트
    }

    // ────────────────────────────────────────────────────────────────────────
    //  ⑤ 거래 요청 제출 (Frame 5 — 자녀 전용)
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 자녀가 부모에게 대리 거래를 요청합니다.
     *
     * @return true = 요청 성공 / false = 아래 제약 조건 위반으로 요청 불가
     *
     * 제약 1: memo가 10자 미만이면 false 반환
     * 제약 2: 매수(BUY) 시 [price × quantity > 잔여 예수금] 이면 false 반환
     * 제약 3: 매도(SELL) 시 [quantity > 현재 보유 수량] 이면 false 반환
     */
    fun submitTransactionRequest(
        type: String,
        ticker: String,
        name: String,
        price: Long,
        quantity: Int,
        memo: String
    ): Boolean {
        // ── 제약 1: 투자 메모 최소 10자 ──────────────────────
        if (memo.trim().length < 10) return false

        val childUid = _activeChildUid.value

        // ── 제약 2: 매수 예수금 잔액 검증 ────────────────────
        if (type == "BUY") {
            val balance = _childAssets.value[childUid] ?: 0L
            if (price * quantity > balance) return false
        }

        // ── 제약 3: 매도 보유 수량 검증 ──────────────────────
        if (type == "SELL") {
            val holdingQty = _childStocks.value[childUid]
                ?.find { it.ticker == ticker }?.quantity ?: 0
            if (quantity > holdingQty) return false
        }

        val request = TransactionRequest(
            id              = "req_${System.currentTimeMillis()}",
            childUid        = childUid,
            childNickname   = _currentUser.value?.nickname ?: "자녀",
            type            = type,
            ticker          = ticker,
            name            = name,
            requestPrice    = price,
            requestQuantity = quantity,
            memo            = memo,
            status          = RequestStatus.PENDING,
            timestamp       = System.currentTimeMillis()
        )

        // 최상단 삽입 (최신 순 정렬 유지)
        _transactionRequests.value = listOf(request) + _transactionRequests.value

        // TODO: Firestore families/{familyId}/transactionRequests/{id} 저장
        // TODO: [FCM] Cloud Functions HTTP Callable → 부모 푸시 알림
        //       functions.getHttpsCallable("notifyParentOfRequest")
        //                .call(mapOf("requestId" to request.id, "familyId" to familyId))

        return true
    }

    // ────────────────────────────────────────────────────────────────────────
    //  ⑥ 대리 거래 2단계 승인 정산 엔진 (Frame 7 ★)
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 1단계: PENDING → ACCEPTED
     * 부모가 자녀 요청을 확인하고 실제 증권사 앱에 주문을 넣었음을 기록.
     */
    fun processToAccepted(requestId: String) {
        _transactionRequests.value = _transactionRequests.value.map { req ->
            if (req.id == requestId && req.status == RequestStatus.PENDING)
                req.copy(status = RequestStatus.ACCEPTED)
            else req
        }
        // TODO: Firestore 상태 업데이트 → "ACCEPTED"
    }

    /**
     * 2단계: ACCEPTED → COMPLETED  (체결 정산 메인 엔진)
     *
     * [부분 체결 처리]
     * actualQuantity < requestQuantity 인 경우:
     *   - 현재 카드: COMPLETED (입력된 수량만큼 정산)
     *   - 잔여 수량: 동일 조건의 새 PENDING 카드로 분리 → 장부 최상단 재등록
     *
     * [금융 정산 수식]
     * 매수) 차감 = actualPrice × actualQuantity × (1 + (수수료% + 세금%) / 100)
     *             → 평단가(이동평균) = (기존평단가×기존수량 + 총체결비용) / 신규총수량
     * 매도) 증가 = actualPrice × actualQuantity × (1 - (수수료% + 세금%) / 100)
     *             → 보유수량 차감, 0이 되면 리스트 삭제
     */
    fun completeTransaction(requestId: String, actualPrice: Long, actualQuantity: Int) {
        val request = _transactionRequests.value.find { it.id == requestId } ?: return
        if (request.status != RequestStatus.ACCEPTED) return

        val now       = System.currentTimeMillis()
        val fee       = _feeSettings.value
        val isPartial = actualQuantity in 1 until request.requestQuantity

        // 현재 카드 → COMPLETED
        val updated = _transactionRequests.value.map { req ->
            if (req.id == requestId) req.copy(
                status         = RequestStatus.COMPLETED,
                actualPrice    = actualPrice,
                actualQuantity = actualQuantity,
                timestamp      = now
            ) else req
        }.toMutableList()

        // 부분 체결 → 잔여 수량을 새 PENDING 카드로 분리
        if (isPartial) {
            val remainingQty = request.requestQuantity - actualQuantity
            val pendingRemainder = request.copy(
                id              = "${requestId}_rem_${now}",
                requestQuantity = remainingQty,
                status          = RequestStatus.PENDING,
                actualPrice     = 0L,
                actualQuantity  = 0,
                timestamp       = now
            )
            updated.add(0, pendingRemainder)   // 장부 최상단에 추가
        }

        _transactionRequests.value = updated

        // 금융 정산 실행
        if (request.type == "BUY") {
            settleBuy(
                childUid       = request.childUid,
                ticker         = request.ticker,
                name           = request.name,
                actualPrice    = actualPrice,
                actualQuantity = actualQuantity,
                fee            = fee
            )
        } else {
            settleSell(
                childUid       = request.childUid,
                ticker         = request.ticker,
                actualPrice    = actualPrice,
                actualQuantity = actualQuantity,
                fee            = fee
            )
        }

        // TODO: Firestore batch write:
        //   - transactionRequests/{id} 상태 업데이트
        //   - childAssets/{childUid} 잔액 업데이트
        //   - childStocks/{childUid} 보유 주식 업데이트
        // TODO: [FCM] Cloud Functions → 자녀 체결 완료 알림
    }

    /**
     * 매수 정산 내부 함수.
     *
     * 총 체결 비용(수수료 포함) = actualPrice × actualQuantity × (1 + (feeRate + taxRate) / 100)
     * 이동평균 평단가 = (기존평단가 × 기존수량 + 총체결비용) / 신규총수량
     *
     * 신규 종목일 경우: averagePrice = 총체결비용 / actualQuantity
     */
    private fun settleBuy(
        childUid: String,
        ticker: String,
        name: String,
        actualPrice: Long,
        actualQuantity: Int,
        fee: FeeSettings
    ) {
        val tradeCost = actualPrice.toDouble() * actualQuantity
        val feeCost   = tradeCost * (fee.brokerFeeRate + fee.tradeTaxRate) / 100.0
        val totalCost = tradeCost + feeCost     // 수수료·세금 포함 실질 체결 비용

        // 예수금 차감
        val assets = _childAssets.value.toMutableMap()
        assets[childUid] = (assets[childUid] ?: 0L) - totalCost.toLong()
        _childAssets.value = assets

        // 보유 주식 갱신 (이동평균 평단가)
        val stocks = _childStocks.value.toMutableMap()
        val list   = stocks.getOrDefault(childUid, emptyList()).toMutableList()
        val idx    = list.indexOfFirst { it.ticker == ticker }

        if (idx >= 0) {
            val existing    = list[idx]
            val newQty      = existing.quantity + actualQuantity
            // 이동평균: 기존 보유 총비용(평단가 기반) + 신규 체결 총비용
            val newAvgPrice = (existing.averagePrice * existing.quantity + totalCost) / newQty
            list[idx] = existing.copy(quantity = newQty, averagePrice = newAvgPrice)
        } else {
            list.add(
                HoldingStock(
                    ticker       = ticker,
                    name         = name,
                    quantity     = actualQuantity,
                    averagePrice = totalCost / actualQuantity   // 수수료 포함 첫 평단가
                )
            )
        }

        stocks[childUid] = list
        _childStocks.value = stocks
    }

    /**
     * 매도 정산 내부 함수.
     *
     * 실수령액 = actualPrice × actualQuantity × (1 - (feeRate + taxRate) / 100)
     * 보유 수량 차감, 잔여가 0 이하이면 리스트에서 완전 제거.
     * 매도 시 평단가(averagePrice)는 변경하지 않습니다.
     */
    private fun settleSell(
        childUid: String,
        ticker: String,
        actualPrice: Long,
        actualQuantity: Int,
        fee: FeeSettings
    ) {
        val tradeProceed = actualPrice.toDouble() * actualQuantity
        val feeCost      = tradeProceed * (fee.brokerFeeRate + fee.tradeTaxRate) / 100.0
        val netProceed   = tradeProceed - feeCost   // 수수료·세금 차감 후 실수령액

        // 예수금 증가
        val assets = _childAssets.value.toMutableMap()
        assets[childUid] = (assets[childUid] ?: 0L) + netProceed.toLong()
        _childAssets.value = assets

        // 보유 수량 차감
        val stocks = _childStocks.value.toMutableMap()
        val list   = stocks.getOrDefault(childUid, emptyList()).toMutableList()
        val idx    = list.indexOfFirst { it.ticker == ticker }

        if (idx >= 0) {
            val remaining = list[idx].quantity - actualQuantity
            if (remaining <= 0) list.removeAt(idx)
            else list[idx] = list[idx].copy(quantity = remaining)
        }

        stocks[childUid] = list
        _childStocks.value = stocks
    }

    /**
     * 미체결 처리: ACCEPTED → FAILED
     * 사유 입력 없이 즉시 완료 탭으로 이동. timestamp를 현재 시각으로 갱신.
     */
    fun failTransaction(requestId: String) {
        val now = System.currentTimeMillis()
        _transactionRequests.value = _transactionRequests.value.map { req ->
            if (req.id == requestId && req.status == RequestStatus.ACCEPTED)
                req.copy(status = RequestStatus.FAILED, timestamp = now)
            else req
        }
        // TODO: Firestore status → "FAILED" 업데이트
        // TODO: [FCM] Cloud Functions → 자녀 미체결 알림
    }

    // ────────────────────────────────────────────────────────────────────────
    //  인증 스텁
    // ────────────────────────────────────────────────────────────────────────

    fun signOut() {
        _currentUser.value = null
        // TODO: Firebase.auth.signOut()
    }
}
