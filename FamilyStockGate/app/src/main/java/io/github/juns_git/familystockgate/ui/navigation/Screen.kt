package io.github.juns_git.familystockgate.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    object SplashLogin : Screen("splash_login")
    object ProfileSetup : Screen("profile_setup")
    object PendingConnection : Screen("pending_connection")
    object Home : Screen("home")
    object StockSearch : Screen("stock_search")

    // Trade는 보텀 네비가 아닌 종목 클릭으로만 진입 가능
    // source: "holdings" | "watchlist" — 진입 경로에 따라 매수/매도 버튼 분기
    object Trade : Screen("trade/{ticker}/{stockName}/{source}") {
        fun createRoute(ticker: String, stockName: String, source: String): String =
            "trade/$ticker/${Uri.encode(stockName)}/$source"
    }

    object Ledger : Screen("ledger")
    object Leaderboard : Screen("leaderboard")
    object Settings : Screen("settings")
}
