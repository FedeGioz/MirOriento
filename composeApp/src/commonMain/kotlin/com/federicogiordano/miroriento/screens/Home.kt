package com.federicogiordano.miroriento.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.federicogiordano.miroriento.api.ConnectionStatus
import com.federicogiordano.miroriento.api.QuizClient
import com.federicogiordano.miroriento.data.RobotStatus
import com.federicogiordano.miroriento.network.PortScanner
import com.federicogiordano.miroriento.viewmodels.StudentViewModel
import kotlinx.coroutines.delay

private const val DEFAULT_SERVER_PORT = 8080
private const val DEFAULT_SERVER_PATH = "/connect"

@Composable
fun HomePage(
    studentViewModel: StudentViewModel = viewModel()
) {
    val currentStudentInfo by studentViewModel.studentInfo.collectAsState()
    val connectionStatus by QuizClient.connectionStatus.collectAsState()
    val robotStatus by QuizClient.robotStatus.collectAsState()

    val portScanner = remember { PortScanner() }
    val scanStatus by portScanner.scanStatus.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    var currentOperationMessage by remember { mutableStateOf("In attesa di configurazione studente...") }
    var autoProcessAttemptedThisSession by remember { mutableStateOf(false) }


    LaunchedEffect(currentStudentInfo, connectionStatus) {
        val student = currentStudentInfo

        if (student != null && connectionStatus is ConnectionStatus.Disconnected) {
            if (!autoProcessAttemptedThisSession) {
                autoProcessAttemptedThisSession = true

                currentOperationMessage = "Configuring session for ${student.name} (ID: ${student.id})..."
                QuizClient.configure(student.id, student.name)
                delay(200)

                currentOperationMessage = "Scanning for professor's device..."
                val foundIp = portScanner.findProfessorDevice()

                if (foundIp != null) {
                    currentOperationMessage = "Professor found at $foundIp. Connecting..."
                    QuizClient.connect(
                        serverIp = foundIp,
                        serverPort = DEFAULT_SERVER_PORT,
                        path = DEFAULT_SERVER_PATH
                    )
                } else {
                    currentOperationMessage = "Professor's device not found. Check Wi-Fi and if professor started session. Retry if needed."
                }
            }
        } else if (student != null && connectionStatus == ConnectionStatus.Connected) {
            currentOperationMessage = "Connesso come ${student.name}!"
            autoProcessAttemptedThisSession = true
        } else if (student == null) {
            currentOperationMessage = "Errore: Dati studente non trovati. Completa la registrazione."
            autoProcessAttemptedThisSession = false
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("MirOriento - Connessione") }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (currentOperationMessage.isNotBlank()) {
                Text(
                    currentOperationMessage,
                    style = MaterialTheme.typography.h6,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }

            val currentScanStatus = scanStatus
            val currentConnectionStatus = connectionStatus

            if (currentScanStatus == PortScanner.ScanStatus.Scanning || currentConnectionStatus is ConnectionStatus.Connecting) {
                CircularProgressIndicator(modifier = Modifier.padding(vertical = 16.dp))
            }

            if (currentScanStatus != PortScanner.ScanStatus.NotStarted && !(currentScanStatus is PortScanner.ScanStatus.Found && currentConnectionStatus == ConnectionStatus.Connected)) {
                val (scanMsg, scanColor) = when (currentScanStatus) {
                    is PortScanner.ScanStatus.Found -> "Scansione IP: Trovato a ${currentScanStatus.ipAddress}" to Color.Green
                    is PortScanner.ScanStatus.NotFound -> "Scansione IP: Dispositivo professore non trovato." to Color.Red
                    is PortScanner.ScanStatus.Scanning -> "Scansione IP: In corso..." to MaterialTheme.colors.onSurface
                    is PortScanner.ScanStatus.Error -> "Scansione IP Errore: ${currentScanStatus.message}" to Color.Red
                    else -> "" to Color.Transparent
                }
                if(scanMsg.isNotBlank()) Text(scanMsg, color = scanColor, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }

            val (connMsg, connColor) = when (currentConnectionStatus) {
                is ConnectionStatus.Connected -> "Stato Connessione: Connesso!" to Color(0xFF4CAF50)
                is ConnectionStatus.Connecting -> "Stato Connessione: Connessione (${currentConnectionStatus.message})..." to MaterialTheme.colors.onSurface
                is ConnectionStatus.Disconnected -> "Stato Connessione: Disconnesso (${currentConnectionStatus.reason})" to MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
                is ConnectionStatus.Error -> "Stato Connessione: Errore (${currentConnectionStatus.message})" to Color(0xFFF44336)
            }
            Text(connMsg, color = connColor, style = MaterialTheme.typography.subtitle1, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))


            robotStatus?.let {
                Text("Batteria Robot: ${it.batteryPercentage}%", style = MaterialTheme.typography.body2, modifier = Modifier.padding(top = 8.dp))
            }

            if (currentStudentInfo != null &&
                currentConnectionStatus !is ConnectionStatus.Connected &&
                currentConnectionStatus !is ConnectionStatus.Connecting &&
                currentScanStatus != PortScanner.ScanStatus.Scanning
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        autoProcessAttemptedThisSession = false
                        currentOperationMessage = "Ritentando la procedura automatica..."
                    },
                ) {
                    Text("Ritenta Scansione e Connessione", style = MaterialTheme.typography.button)
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusDisplay(status: ConnectionStatus) {
    val (text, color) = when (status) {
        is ConnectionStatus.Connected -> "Status: Connected!" to Color(0xFF4CAF50)
        is ConnectionStatus.Connecting -> "Status: Connecting... (${status.message})" to MaterialTheme.colors.onSurface
        is ConnectionStatus.Disconnected -> "Status: Disconnected (${status.reason})" to MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
        is ConnectionStatus.Error -> "Status: Error (${status.message})" to Color(0xFFF44336)
    }
    Text(text, color = color, style = MaterialTheme.typography.subtitle1, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun RobotBatteryDisplay(robotStatus: RobotStatus?) {
    val text = robotStatus?.let { "Robot Battery: ${it.batteryPercentage}%" } ?: "Robot Battery: N/A"
    Text(text, style = MaterialTheme.typography.body1, modifier = Modifier.padding(bottom = 8.dp))
}