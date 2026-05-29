package io.github.juns_git.android.familystockgate.data.model

enum class UserRole { PARENT, CHILD }

data class UserData(
    val uid: String = "",
    val email: String = "",
    val nickname: String = "",
    val role: UserRole = UserRole.CHILD,
    val familyId: String? = null
)

data class FamilyData(
    val familyId: String = "",
    val adminUid: String = "",
    val familyCash: Map<String, Long> = emptyMap(),
    val pendingChildren: List<String> = emptyList()
)

data class TradeRequest(
    val requestId: String = "",
    val childUid: String = "",
    val childNickname: String = "",
    val stockName: String = "",
    val stockTicker: String = "",
    val quantity: Int = 0,
    val pricePerShare: Long = 0,
    val memo: String = "",
    val type: TradeType = TradeType.BUY,
    val status: TradeStatus = TradeStatus.PENDING,
    val timestamp: Long = 0L,
    // 2단계 체결 워크플로 추가 필드
    val failReason: String = "",
    val filledPrice: Long = 0,
    val filledQuantity: Int = 0,
    val completedAt: Long = 0L,
    val remainingQuantity: Int = 0
)

enum class TradeType { BUY, SELL, INTEREST, DIVIDEND, WITHDRAWAL }
// PENDING → ACCEPTED → FILLED | PARTIAL_FILLED | UNFILLED
enum class TradeStatus { PENDING, ACCEPTED, FILLED, PARTIAL_FILLED, UNFILLED, APPROVED, REJECTED }

data class StockItem(
    val ticker: String,
    val name: String,
    val currentPrice: Long,
    val changeRate: Double  // +1.23 = +1.23%, -0.78 = -0.78%
)

data class HoldingItem(
    val stock: StockItem,
    val quantity: Int,
    val avgPrice: Long
) {
    val totalValue: Long get() = stock.currentPrice * quantity
    val profitRate: Double get() = (stock.currentPrice - avgPrice).toDouble() / avgPrice * 100
    val profitLoss: Long get() = (stock.currentPrice - avgPrice) * quantity
}

// 리더보드 항목: 가족 내 자녀별 수익률 랭킹
data class LeaderboardEntry(
    val childUid: String,
    val nickname: String,
    val totalAsset: Long,        // 예수금 + 보유주식 평가금액
    val initialBudget: Long,     // 부모가 최초 세팅한 기초 자산
    val profitRate: Double,      // (totalAsset - initialBudget) / initialBudget × 100
    val isCurrentUser: Boolean
)
