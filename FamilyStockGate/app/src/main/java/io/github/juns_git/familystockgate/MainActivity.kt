package io.github.juns_git.familystockgate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.juns_git.familystockgate.ui.navigation.AppNavHost
import io.github.juns_git.familystockgate.ui.theme.FamilyStockGateTheme
import io.github.juns_git.familystockgate.ui.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FamilyStockGateTheme {
                val appViewModel: AppViewModel = viewModel()
                AppNavHost(viewModel = appViewModel)
            }
        }
    }
}
