package com.nophubbing.presenceai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.nophubbing.presenceai.ui.screens.PermissionScreen
import com.nophubbing.presenceai.ui.screens.DashboardScreen
import com.nophubbing.presenceai.ui.theme.PresenceAITheme
import com.nophubbing.presenceai.utils.OnboardingManager

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val context = this

        val onboardingDone =
            OnboardingManager.isOnboardingComplete(context)

        setContent {

            PresenceAITheme {

                var started by remember {
                    mutableStateOf(onboardingDone)
                }

                if (!started) {

                    PermissionScreen {

                        OnboardingManager.setOnboardingComplete(context)

                        started = true
                    }

                } else {

                    DashboardScreen()
                }
            }
        }
    }
}