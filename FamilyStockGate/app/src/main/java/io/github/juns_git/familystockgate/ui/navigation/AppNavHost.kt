package io.github.juns_git.familystockgate.ui.navigation

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
import io.github.juns_git.familystockgate.ui.screens.HomeScreen
import io.github.juns_git.familystockgate.ui.screens.LeaderboardScreen
import io.github.juns_git.familystockgate.ui.screens.LedgerScreen
import io.github.juns_git.familystockgate.ui.screens.PendingConnectionScreen
import io.github.juns_git.familystockgate.ui.screens.ProfileSetupScreen
import io.github.juns_git.familystockgate.ui.screens.SplashLoginScreen
import io.github.juns_git.familystockgate.data.model.UserRole
import io.github.juns_git.familystockgate.ui.screens.SettingsScreen
import io.github.juns_git.familystockgate.ui.screens.StockSearchScreen
import io.github.juns_git.familystockgate.ui.screens.TradeScreen
import io.github.juns_git.familystockgate.ui.viewmodel.AppViewModel

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
fun AppNavHost(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val role by viewModel.debugRole.collectAsState()

    // 자녀 모드에서는 Settings 탭 완전 숨김
    val visibleNavItems = if (role == UserRole.PARENT) {
        bottomNavItems
    } else {
        bottomNavItems.filter { it !is BottomNavItem.Settings }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in screensWithBottomNav) {
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
                    innerPadding = innerPadding,
                    onLoginSuccess = { navController.navigate(Screen.ProfileSetup.route) }
                )
            }
            composable(Screen.ProfileSetup.route) {
                ProfileSetupScreen(
                    viewModel = viewModel,
                    innerPadding = innerPadding,
                    onParentSetupComplete = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.SplashLogin.route) { inclusive = true }
                        }
                    },
                    onChildPendingConnection = {
                        navController.navigate(Screen.PendingConnection.route)
                    }
                )
            }
            composable(Screen.PendingConnection.route) {
                PendingConnectionScreen(
                    viewModel = viewModel,
                    innerPadding = innerPadding,
                    onApproved = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.SplashLogin.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    innerPadding = innerPadding,
                    onNavigateToTrade = { ticker, stockName, source ->
                        navController.navigate(Screen.Trade.createRoute(ticker, stockName, source))
                    }
                )
            }
            composable(Screen.StockSearch.route) {
                StockSearchScreen(
                    viewModel = viewModel,
                    innerPadding = innerPadding
                )
            }
            // Trade: ticker + name + source(holdings|watchlist) 인자 수신
            composable(
                route = Screen.Trade.route,
                arguments = listOf(
                    navArgument("ticker") { type = NavType.StringType },
                    navArgument("stockName") { type = NavType.StringType },
                    navArgument("source") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val ticker = backStackEntry.arguments?.getString("ticker") ?: ""
                val stockName = Uri.decode(backStackEntry.arguments?.getString("stockName") ?: "")
                val source = backStackEntry.arguments?.getString("source") ?: "watchlist"
                TradeScreen(
                    viewModel = viewModel,
                    innerPadding = innerPadding,
                    stockTicker = ticker,
                    stockName = stockName,
                    source = source,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Ledger.route) {
                LedgerScreen(viewModel = viewModel, innerPadding = innerPadding)
            }
            composable(Screen.Leaderboard.route) {
                LeaderboardScreen(viewModel = viewModel, innerPadding = innerPadding)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = viewModel, innerPadding = innerPadding)
            }
        }
    }
}
