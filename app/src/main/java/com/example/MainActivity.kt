package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ThemeMode
import com.example.data.preferences.ThemePreferencesRepository
import com.example.firebase.configureAppCheck
import com.example.ui.AppNavigation
import com.example.ui.theme.LumoreTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check before UI, ViewModels, or any Firebase operations.
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            val themeRepo = remember { ThemePreferencesRepository(applicationContext) }
            val themeMode by themeRepo.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val scope = rememberCoroutineScope()

            LumoreTheme(themeMode = themeMode) {
                AppNavigation(
                    themeMode = themeMode,
                    onSelectThemeMode = { mode ->
                        scope.launch { themeRepo.setThemeMode(mode) }
                    },
                    onToggleLightDark = { currentlyDark ->
                        scope.launch { themeRepo.toggleLightDark(currentlyDark) }
                    }
                )
            }
        }
    }
}
