package io.github.juns_git.android.familystockgate.ui.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.juns_git.android.familystockgate.ui.screens.HomeScreen
import io.github.juns_git.android.familystockgate.ui.screens.LeaderboardScreen
import io.github.juns_git.android.familystockgate.ui.screens.LedgerScreen
import io.github.juns_git.android.familystockgate.ui.screens.PendingConnectionScreen
import io.github.juns_git.android.familystockgate.ui.screens.ProfileSetupScreen
import io.github.juns_git.android.familystockgate.ui.screens.SettingsScreen
import io.github.juns_git.android.familystockgate.ui.screens.SplashLoginScreen
import io.github.juns_git.android.familystockgate.ui.screens.StockSearchScreen
import io.github.juns_git.android.familystockgate.ui.screens.TradeScreen
import io.github.juns_git.android.familystockgate.ui.screens.UserPortfolioDetailScreen
import io.github.juns_git.android.familystockgate.ui.viewmodel.AppViewModel
import io.github.juns_git.android.familystockgate.ui.viewmodel.AuthState
import io.github.juns_git.android.familystockgate.ui.viewmodel.FamilyStockViewModel
import io.github.juns_git.android.familystockgate.ui.viewmodel.UserRole

private sealed class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    object Home : BottomNavItem(Screen.Home.route, "홈", Icons.Default.Home)
    object Search : BottomNavItem(Screen.StockSearch.route, "검색", Icons.Default.Search)
    object Ledger : BottomNavItem(Screen.Ledger.route, "장부", Icons.Default.List)
    object Leaderboard : BottomNavItem(Screen.Leaderboard.route, "랭킹", Icons.Default.Star)
    object Settings : BottomNavItem(Screen.Settings.route, "설정", Icons.Default.Settings)
}

private val bottomNavItems = listOf(
    BottomNavItem.Home,
    BottomNavItem.Search,
    BottomNavItem.Ledger,
    BottomNavItem.Leaderboard,
    BottomNavItem.Settings
)

// Trade 화면은 보텀 바를 숨김 (종목 클릭으로만 진입)
private val screensWithBottomNav = setOf(
    Screen.Home.route,
    Screen.StockSearch.route,
    Screen.Ledger.route,
    Screen.Leaderboard.route,
    Screen.Settings.route
)

@Composable
fun AppNavHost(appViewModel: AppViewModel, familyViewModel: FamilyStockViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val authState by familyViewModel.authState.collectAsState()
    val currentUser by familyViewModel.currentUser.collectAsState()
    val role = currentUser?.role ?: UserRole.CHILD

    // Auth-driven navigation — fires whenever authState changes
    // 규칙: 모든 분기는 back-stack을 완전히 정리(popUpTo 0)하여 뒤로가기로 이전 인증 화면 재진입 차단
    LaunchedEffect(authState) {
        val dest = navController.currentDestination?.route
        when (authState) {
            // ✅ 인증 완료 → 홈 화면 (PARENT/CHILD 공통)
            is AuthState.LoggedIn -> {
                if (dest != Screen.Home.route) {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            // ✅ 신규 회원 or 독립 서버 개설 필요 → 프로필/역할 선택 화면 (Frame 1)
            is AuthState.NeedsProfileSetup,
            is AuthState.ServerSetupRequired -> {
                if (dest != Screen.ProfileSetup.route) {
                    navController.navigate(Screen.ProfileSetup.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            // ✅ 자녀 계정 부모 연동 대기 → 대기 화면
            // ProfileSetup에서도, 앱 재시작(checkExistingSession)에서도 이 경로로만 진입
            is AuthState.PendingConnection -> {
                if (dest != Screen.PendingConnection.route) {
                    navController.navigate(Screen.PendingConnection.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            // ✅ 로그아웃 → SplashLogin 복귀
            is AuthState.Idle -> {
                if (dest != null && dest != Screen.SplashLogin.route) {
                    navController.navigate(Screen.SplashLogin.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            else -> {} // Loading, Error: 현재 화면 유지
        }
    }

    // 로그인 전에는 하단 바 전체 숨김; 자녀도 설정 탭 표시
    val isLoggedIn = authState is AuthState.LoggedIn
    val visibleNavItems = bottomNavItems

    Scaffold(
        bottomBar = {
            if (isLoggedIn && currentRoute in screensWithBottomNav) {
                NavigationBar {
                    visibleNavItems.forEach { item ->
                        val selected = navBackStackEntry?.destination?.hierarchy
                            ?.any { it.route == item.route } == true
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.SplashLogin.route
        ) {
            composable(Screen.SplashLogin.route) {
                SplashLoginScreen(
                    authState = authState,
                    onSignInWithGoogle = { idToken -> familyViewModel.signInWithGoogle(idToken) },
                    innerPadding = innerPadding
                )
            }
            composable(Screen.ProfileSetup.route) {
                ProfileSetupScreen(
                    viewModel = familyViewModel,
                    innerPadding = innerPadding
                )
            }
            composable(Screen.PendingConnection.route) {
                PendingConnectionScreen(
                    viewModel = appViewModel,
                    innerPadding = innerPadding,
                    onApproved = {
                        // [DEBUG] authState를 LoggedIn으로 바꾸면 LaunchedEffect(authState)가
                        // 자동으로 Home 으로 이동 + isLoggedIn=true 가 되어 하단 바도 표시됨
                        familyViewModel.debugForceApprove()
                    }
                )
            }
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = appViewModel,
                    innerPadding = innerPadding,
                    onNavigateToTrade = { ticker, stockName, source ->
                        navController.navigate(Screen.Trade.createRoute(ticker, stockName, source))
                    }
                )
            }
            composable(Screen.StockSearch.route) {
                StockSearchScreen(
                    viewModel = appViewModel,
                    innerPadding = innerPadding
                )
            }
            // Trade: ticker + name + source 인자 수신
            composable(
                route = Screen.Trade.route,
                arguments = listOf(
                    navArgument("ticker") { type = NavType.StringType },
                    navArgument("stockName") { type = NavType.StringType },
                    navArgument("source") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val ticker = backStackEntry.arguments?.getString("ticker") ?: ""
                val stockName = Uri.decode(
                    backStackEntry.arguments?.getString("stockName") ?: ""
                )
                val source = backStackEntry.arguments?.getString("source") ?: "watchlist"
                TradeScreen(
                    viewModel = appViewModel,
                    innerPadding = innerPadding,
                    stockTicker = ticker,
                    stockName = stockName,
                    source = source,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Ledger.route) {
                LedgerScreen(viewModel = appViewModel, innerPadding = innerPadding)
            }
            composable(Screen.Leaderboard.route) {
                LeaderboardScreen(
                    viewModel = appViewModel,
                    innerPadding = innerPadding,
                    onUserClick = { uid, nick ->
                        navController.navigate(Screen.UserPortfolioDetail.createRoute(uid, nick))
                    }
                )
            }
            composable(
                route = Screen.UserPortfolioDetail.route,
                arguments = listOf(
                    navArgument("targetUid") { type = NavType.StringType },
                    navArgument("nickname")  { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val targetUid = backStackEntry.arguments?.getString("targetUid") ?: ""
                val nickname  = Uri.decode(backStackEntry.arguments?.getString("nickname") ?: "")
                UserPortfolioDetailScreen(
                    viewModel  = appViewModel,
                    innerPadding = innerPadding,
                    targetUid  = targetUid,
                    nickname   = nickname,
                    onBack     = { navController.popBackStack() }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    appViewModel = appViewModel,
                    familyViewModel = familyViewModel,
                    innerPadding = innerPadding
                )
            }
        }
    }
}
