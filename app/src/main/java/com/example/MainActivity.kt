package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.Screen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
        setContent {
            val viewModel: MainViewModel = viewModel()
            val activeTheme = viewModel.activeTheme

            MyApplicationTheme(theme = activeTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    AppNavigationWrapper(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AppNavigationWrapper(viewModel: MainViewModel) {
    val currentScreen = viewModel.currentScreen

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            fadeIn(animationSpec = tween(300)) with fadeOut(animationSpec = tween(200))
        },
        label = "screen_routing"
    ) { screen ->
        when (screen) {
            is Screen.Login, is Screen.Register -> {
                LoginScreen(viewModel = viewModel)
            }
            is Screen.Dashboard -> {
                DashboardScreen(
                    viewModel = viewModel,
                    onOpenAdmin = {
                         viewModel.navigateTo(Screen.Admin)
                    }
                )
            }
            is Screen.Admin -> {
                AdminScreen(
                    viewModel = viewModel,
                    onBack = {
                         viewModel.navigateTo(Screen.Dashboard)
                    }
                )
            }
        }
    }
}
