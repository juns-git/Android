# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

Build and run via Android Studio (Gradle wrapper also works from the project root):

```bash
# Debug build
./gradlew assembleDebug

# Run all unit tests
./gradlew test

# Run a single unit test class
./gradlew :app:testDebugUnitTest --tests "io.github.juns_git.familystockgate.ExampleUnitTest"

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Lint check
./gradlew lint
```

**Sync dependency changes** in Android Studio: File → Sync Project with Gradle Files (or click "Sync Now" when prompted).

## Tech Stack

- **AGP 9.2.1 / Kotlin 2.2.10** — Do NOT add `alias(libs.plugins.kotlin.android)` to `app/build.gradle.kts`. AGP 9.x + `kotlin.compose` handles Kotlin Android compilation without it; adding it breaks `buildFeatures { compose = true }`.
- **Compose BOM 2026.02.01** — All `androidx.compose.*` versions are managed by the BOM; do not pin individual compose library versions.
- **Navigation Compose 2.8.5** — `Screen.Trade` uses route args (`{ticker}/{stockName}`); always use `Screen.Trade.createRoute()` which applies `Uri.encode` on the stock name.
- **Lifecycle ViewModel Compose 2.8.7** — Single `AppViewModel` instance created in `MainActivity` via `viewModel()` and passed down to all screens.

## Frame-scoped Edit Policy

Each screen composable maps to a named Frame (e.g., Frame 2 = `HomeScreen`, Frame 6 = `LedgerScreen`). When a change request targets a specific Frame:

- **Only edit the explicitly named Frame's file(s).** Do not touch other screen files unless the request explicitly names them.
- `AppViewModel.kt` and `AppModels.kt` may be extended (adding new state/functions) but existing functions must not be modified unless required by the named Frame.
- `AppNavHost.kt` and `Screen.kt` must not change unless the request involves navigation structure itself.
- If a change in the named Frame would require touching another Frame to stay consistent, flag it to the user and ask before proceeding.

## Architecture

**One ViewModel, many screens.** `AppViewModel` is the sole ViewModel; it is instantiated once in `MainActivity` and threaded through the entire composable tree. There is no repository layer yet — all Firebase calls are stubs (`// TODO:` comments).

```
MainActivity
  └── AppNavHost(viewModel)          ← owns NavController + Scaffold + BottomNavBar
        ├── SplashLoginScreen
        ├── ProfileSetupScreen
        ├── PendingConnectionScreen
        ├── HomeScreen(onNavigateToTrade)   ← Tab 1: 보유종목 / 관심종목
        ├── StockSearchScreen               ← Tab 2: 검색 → 관심 등록 전용
        ├── TradeScreen(ticker, stockName)  ← NOT in bottom nav; entered via stock card click only
        ├── LedgerScreen                    ← Tab 3: 승인 센터
        └── LeaderboardScreen               ← Tab 4: 랭킹 + FCM 설정
```

### Trade navigation constraint (business rule)
`TradeScreen` is intentionally excluded from `screensWithBottomNav`. The only legal entry path is:
1. `StockSearchScreen` → tap ★ → stock added to `AppViewModel._watchlist`
2. `HomeScreen` (관심/보유 탭) → card click → `navController.navigate(Screen.Trade.createRoute(...))`

Do not add a direct bottom-nav route to Trade.

### State model (`AppModels.kt`)
- `UserData` / `FamilyData` — mirrors Firestore `users/{uid}` and `families/{familyId}` documents.
- `StockItem` — live price data (stub). `HoldingItem` wraps a `StockItem` with quantity + avgPrice and computes `profitRate` / `totalValue` as derived properties.
- `TradeRequest` — pending/approved/rejected approval workflow between CHILD and PARENT.

### Role switching (debug only)
`AppViewModel._debugRole: MutableStateFlow<UserRole>` is the active role during development. It is toggled via the switch in `HomeScreen`'s cash card. When Firebase Auth is wired up, replace all `viewModel.debugRole` reads with `viewModel.currentUser.value?.role`.

### Firebase stubs
Every Firebase operation is a `// TODO:` comment inside a `viewModelScope.launch` block in `AppViewModel`. FCM must be sent server-side via a Cloud Functions HTTP Callable (`sendFcmViaCloudFunction`) — never send directly from the client using a server key.

### Firestore data paths
| Collection | Purpose |
|---|---|
| `users/{uid}` | User profile, role, familyId |
| `families/{familyId}` | adminUid, familyCash map, pendingChildren list |
| `families/{familyId}/tradeRequests/{id}` | Per-family trade approval queue |
| `families/{familyId}/watchlist/{ticker}` | Per-family watchlist |

### `innerPadding` convention
`AppNavHost` owns the single `Scaffold`. The `innerPadding` lambda value is passed directly into every screen composable as a parameter and applied via `Modifier.padding(innerPadding)`. Do not create nested Scaffolds in screen composables — use a plain `Column` or `Box` at the top level.
