package com.federicogiordano.miroriento

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.federicogiordano.miroriento.screens.ConnectedWaitingScreen
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
            composable(Screens.Home.route) {
                ConnectedWaitingScreen(navController)
            }
        }
    }
}