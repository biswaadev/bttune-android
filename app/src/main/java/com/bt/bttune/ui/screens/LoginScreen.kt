package com.bt.bttune.ui.screens

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.bt.bttune.ui.screens.onboarding.OnboardingScreen

@Composable
fun LoginScreen(navController: NavController) {
    OnboardingScreen(navController = navController)
}
