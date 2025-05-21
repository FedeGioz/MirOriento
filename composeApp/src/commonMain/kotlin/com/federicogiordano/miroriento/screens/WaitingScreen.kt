package com.federicogiordano.miroriento.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.federicogiordano.miroriento.ConnectionState
import com.federicogiordano.miroriento.StudentConnection
import com.federicogiordano.miroriento.WebSocketClient
import com.federicogiordano.miroriento.network.PortScanner
import com.federicogiordano.miroriento.viewmodels.StudentViewModel
import kotlinx.coroutines.delay

enum class Screens(val route: String) {
    Home("home"),
    Registration("registration"),
    Waiting("waiting")
}

@Composable
fun WaitingScreen(navController: NavController, studentViewModel: StudentViewModel) {
    var dotsCount by remember { mutableStateOf(1) }
    var statusMessage by remember { mutableStateOf("Ricerca del dispositivo del professore") }
    val portScanner = remember { PortScanner() }
    val scanStatus by portScanner.scanStatus.collectAsState()
    val scanLog by portScanner.scanLog.collectAsState()

    val studentInfo by studentViewModel.studentInfo.collectAsState()

    val webSocketClient = remember {
        WebSocketClient(
            serverUrl = "",
            port = 8080,
            studentInfo = StudentConnection(
                name = studentInfo?.name ?: "Studente Sconosciuto",
                id = "null"
            )
        )
    }
    val connectionState by webSocketClient.connectionState.collectAsState()

    LaunchedEffect(key1 = true) {
        while (true) {
            dotsCount = (dotsCount % 3) + 1
            delay(500)
        }
    }

    LaunchedEffect(key1 = scanStatus) {
        when (scanStatus) {
            is PortScanner.ScanStatus.Found -> {
                val ipAddress = (scanStatus as PortScanner.ScanStatus.Found).ipAddress
                statusMessage = "Dispositivo del professore trovato. Connessione in corso..."
                webSocketClient.updateConnectionParams(
                    newUrl = ipAddress,
                    newPort = 8080,
                    newStudentInfo = StudentConnection(
                        id = "null",
                        name = studentInfo?.name ?: "Studente Sconosciuto"
                    )
                )
                webSocketClient.connect()
            }
            is PortScanner.ScanStatus.NotFound -> {
                statusMessage = "Nessun dispositivo del professore trovato. Riprovo..."
                delay(2000)
                portScanner.findProfessorDevice()
            }
            is PortScanner.ScanStatus.Error -> {
                statusMessage = "Errore: ${(scanStatus as PortScanner.ScanStatus.Error).message}"
            }
            else -> {}
        }
    }

    LaunchedEffect(key1 = true) {
        portScanner.findProfessorDevice()
    }

    LaunchedEffect(key1 = connectionState) {
        when (connectionState) {
            ConnectionState.CONNECTED -> {
                navController.navigate(Screens.Home.route) {
                    popUpTo(Screens.Registration.route) { inclusive = true }
                }
            }
            ConnectionState.CONNECTING -> {
                statusMessage = "Connessione al dispositivo del professore"
            }
            ConnectionState.ERROR -> {
                statusMessage = "Errore di connessione. Riprovo la scansione..."
                delay(2000)
                portScanner.findProfessorDevice()
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = statusMessage,
            style = MaterialTheme.typography.h5,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = ".".repeat(dotsCount),
                fontSize = 48.sp,
                color = MaterialTheme.colors.primary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = scanLog,
            style = MaterialTheme.typography.caption,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = when (connectionState) {
                ConnectionState.CONNECTING -> "Stabilendo la connessione..."
                ConnectionState.CONNECTED -> "Connesso! Reindirizzamento..."
                ConnectionState.ERROR -> "Errore di connessione, riprovo..."
                else -> "Attendere mentre ti connettiamo"
            },
            style = MaterialTheme.typography.subtitle1
        )
    }
}