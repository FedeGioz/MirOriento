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
import kotlinx.coroutines.launch
import java.util.UUID

enum class Screens(val route: String) {
    Quiz("quiz/{serverIp}/{studentId}/{studentName}"),
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

    val studentInfoState by studentViewModel.studentInfo.collectAsState()

    val currentStudentId = remember(studentInfoState) {
        studentInfoState?.name?.replace(" ", "_")?.take(20) ?: "student_${UUID.randomUUID().toString().take(8)}"
    }
    val currentStudentName = remember(studentInfoState) {
        studentInfoState?.name ?: "Studente Sconosciuto"
    }

    val webSocketClient = remember(currentStudentId, currentStudentName) {
        WebSocketClient(
            serverUrl = "",
            port = 8080,
            studentInfo = StudentConnection(
                id = currentStudentId,
                name = currentStudentName
            )
        )
    }
    val connectionState by webSocketClient.connectionState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(webSocketClient) {
        onDispose {
            coroutineScope.launch {
                println("WaitingScreen: Disposing WebSocketClient from WaitingScreen.")
                webSocketClient.disconnect()
            }
        }
    }

    LaunchedEffect(key1 = true) {
        while (true) {
            dotsCount = (dotsCount % 3) + 1
            delay(500)
        }
    }

    LaunchedEffect(key1 = scanStatus, studentInfoState) {
        val studentNameForConnection = studentInfoState?.name ?: "Studente Sconosciuto"
        val studentIdForConnection = studentInfoState?.name?.replace(" ", "_")?.take(20) ?: currentStudentId


        when (val currentScanStatus = scanStatus) {
            is PortScanner.ScanStatus.Found -> {
                statusMessage = "Dispositivo del professore trovato. Connessione in corso..."
                webSocketClient.updateConnectionParams(
                    newUrl = currentScanStatus.ipAddress,
                    newPort = 8080,
                    newStudentInfo = StudentConnection(
                        id = studentIdForConnection,
                        name = studentNameForConnection
                    )
                )
                coroutineScope.launch { webSocketClient.connect() }
            }
            is PortScanner.ScanStatus.NotFound -> {
                statusMessage = "Nessun dispositivo del professore trovato. Riprovo..."
                delay(2000)
                if (studentInfoState != null) {
                    coroutineScope.launch { portScanner.findProfessorDevice() }
                }
            }
            is PortScanner.ScanStatus.Error -> {
                statusMessage = "Errore scansione: ${currentScanStatus.message}. Riprovo..."
                delay(2000)
                if (studentInfoState != null) {
                    coroutineScope.launch { portScanner.findProfessorDevice() }
                }
            }
        }
    }

    LaunchedEffect(key1 = studentInfoState) {
        if (studentInfoState != null && scanStatus is PortScanner.ScanStatus.NotStarted) {
            coroutineScope.launch { portScanner.findProfessorDevice() }
        }
    }

    LaunchedEffect(key1 = connectionState, scanStatus, studentInfoState) {
        if (connectionState == ConnectionState.CONNECTED && scanStatus is PortScanner.ScanStatus.Found) {
            val serverIp = (scanStatus as PortScanner.ScanStatus.Found).ipAddress
            val sId = currentStudentId.ifEmpty { "fallbackStudentId_${UUID.randomUUID().toString().take(4)}" }
            val sName = currentStudentName.ifEmpty { "FallbackStudentName" }

            navController.navigate(
                Screens.Quiz.route
                    .replace("{serverIp}", serverIp)
                    .replace("{studentId}", sId)
                    .replace("{studentName}", sName)
            ) {
                popUpTo(Screens.Registration.route) { inclusive = true }
            }
        } else if (connectionState == ConnectionState.ERROR) {
            statusMessage = "Errore di connessione. Riprovo la scansione..."
            delay(2000)
            if (studentInfoState != null) {
                coroutineScope.launch { portScanner.findProfessorDevice() }
            }
        } else if (connectionState == ConnectionState.CONNECTING) {
            statusMessage = "Connessione al dispositivo del professore..."
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
                ConnectionState.CONNECTED -> "Connesso! Reindirizzamento al quiz..."
                ConnectionState.ERROR -> "Errore di connessione, riprovo..."
                ConnectionState.DISCONNECTED -> if (scanStatus is PortScanner.ScanStatus.Scanning || scanStatus is PortScanner.ScanStatus.NotStarted) "Attendere..." else "Disconnesso. Riprovare la scansione."
            },
            style = MaterialTheme.typography.subtitle1
        )
    }
}