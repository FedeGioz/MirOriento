package com.federicogiordano.miroriento

import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.federicogiordano.miroriento.navigation.Screen
import com.federicogiordano.miroriento.navigation.bottomNavItems
import com.federicogiordano.miroriento.screens.HomePage
import com.federicogiordano.miroriento.screens.QuizScreen
import com.federicogiordano.miroriento.screens.StudentRegistrationScreen
import com.federicogiordano.miroriento.viewmodels.StudentViewModel

@Composable
fun App() {
    val studentViewModel: StudentViewModel = viewModel()
    val currentStudentInfo by studentViewModel.studentInfo.collectAsState()

    MaterialTheme {
        if (currentStudentInfo == null) {
            StudentRegistrationScreen(
                studentViewModel = studentViewModel,
                onRegistrationComplete = {
                    println("App: Registration signal received. StudentInfo updated.")
                }
            )
        } else {
            MainAppNavigation(studentViewModel = studentViewModel)
        }
    }
}

@Composable
fun MainAppNavigation(studentViewModel: StudentViewModel) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            BottomNavigation {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                bottomNavItems.forEach { screen ->
                    BottomNavigationItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        selectedContentColor = MaterialTheme.colors.secondary,
                        unselectedContentColor = LocalContentColor.current.copy(alpha = ContentAlpha.medium),
                        alwaysShowLabel = true,
                        onClick = {
                            navController.navigate(screen.route) {
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
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomePage(studentViewModel = studentViewModel)
            }
            composable(Screen.Quiz.route) {
                QuizScreen()
            }
        }
    }
}