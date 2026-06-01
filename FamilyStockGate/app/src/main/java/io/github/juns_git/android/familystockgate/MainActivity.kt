package io.github.juns_git.android.familystockgate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.juns_git.android.familystockgate.ui.navigation.AppNavHost
import io.github.juns_git.android.familystockgate.ui.theme.FamilyStockGateTheme
import io.github.juns_git.android.familystockgate.ui.viewmodel.AppViewModel
import io.github.juns_git.android.familystockgate.ui.viewmodel.FamilyStockViewModel
import io.github.juns_git.android.familystockgate.utils.FirebaseConfigManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // SharedPreferences에 커스텀 Firebase 키가 있으면 즉시 초기화.
        // setContent() 호출 전에 실행해야 ViewModel.db getter가 올바른 인스턴스를 반환한다.
        FirebaseConfigManager.initCustomFirebaseApp(this)

        enableEdgeToEdge()
        setContent {
            val appViewModel: AppViewModel = viewModel()
            val familyViewModel: FamilyStockViewModel = viewModel()
            val appTheme by appViewModel.appTheme.collectAsState()
            FamilyStockGateTheme(appTheme = appTheme) {
                AppNavHost(appViewModel = appViewModel, familyViewModel = familyViewModel)
            }
        }
    }
}
