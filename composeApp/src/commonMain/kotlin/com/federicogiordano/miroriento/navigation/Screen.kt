package com.federicogiordano.miroriento.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home_screen", "Home", Icons.Filled.Home)
    data object Quiz : Screen("quiz_screen", "Quiz", Icons.Filled.Info)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Quiz
)