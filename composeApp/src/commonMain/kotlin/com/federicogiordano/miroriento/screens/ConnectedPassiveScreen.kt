package com.federicogiordano.miroriento.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import androidx.compose.runtime.*

@Composable
fun ConnectedWaitingScreen(navController: NavController) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Connesso al dispositivo del professore",
            style = MaterialTheme.typography.h5,
            color = MaterialTheme.colors.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Segui le indicazioni orali del professore, attendi i quiz",
            style = MaterialTheme.typography.h6,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(48.dp))

        var dotsCount by remember { mutableStateOf(1) }

        LaunchedEffect(key1 = true) {
            while (true) {
                dotsCount = (dotsCount % 3) + 1
                delay(500)
            }
        }

        Text(
            text = ".".repeat(dotsCount),
            fontSize = 48.sp,
            color = MaterialTheme.colors.secondary
        )
    }
}