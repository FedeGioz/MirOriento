package com.federicogiordano.miroriento

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.federicogiordano.miroriento.screens.QuizScreen
import com.federicogiordano.miroriento.screens.Screens
import com.federicogiordano.miroriento.screens.StudentRegistrationScreen
import com.federicogiordano.miroriento.screens.WaitingScreen
import com.federicogiordano.miroriento.viewmodels.StudentViewModel

@Composable
fun App() {
    MaterialTheme {
        val navController = rememberNavController()
        val studentViewModel = remember { StudentViewModel() }

        NavHost(
            navController = navController,
            startDestination = Screens.Registration.route
        ) {
            composable(Screens.Registration.route) {
                StudentRegistrationScreen(navController, studentViewModel)
            }
            composable(Screens.Waiting.route) {
                WaitingScreen(navController, studentViewModel)
            }
            composable(
                route = Screens.Quiz.route,
                arguments = listOf(
                    navArgument("serverIp") { type = NavType.StringType },
                    navArgument("studentId") { type = NavType.StringType },
                    navArgument("studentName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val serverIp = backStackEntry.arguments?.getString("serverIp") ?: ""
                val studentId = backStackEntry.arguments?.getString("studentId") ?: ""
                val studentName = backStackEntry.arguments?.getString("studentName") ?: ""

                QuizScreen(
                    serverIp = serverIp,
                    studentId = studentId,
                    studentName = studentName
                )
            }
        }
    }
}