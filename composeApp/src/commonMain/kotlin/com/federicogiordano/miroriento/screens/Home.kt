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
import com.federicogiordano.miroriento.utils.JoystickController
import com.federicogiordano.miroriento.viewmodels.StudentViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val DEFAULT_SERVER_PORT = 8080
private const val DEFAULT_SERVER_PATH = "/connect"

@Composable
fun HomePage(
    studentViewModel: StudentViewModel = viewModel()
) {
    val currentStudentInfo by studentViewModel.studentInfo.collectAsState()
    val connectionStatus by QuizClient.connectionStatus.collectAsState()
    val robotStatus by QuizClient.robotStatus.collectAsState()
    val showJoystick by QuizClient.showJoystick.collectAsState()

    val portScanner = remember { PortScanner() }
    val scanStatus by portScanner.scanStatus.collectAsState()
    val scanLog by portScanner.scanLog.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    var currentOperationMessage by remember { mutableStateOf("In attesa di registrazione studente...") }

    var autoConnectSequenceTriggered by remember(currentStudentInfo?.id) { mutableStateOf(false) }

    var isJoystickCurrentlyActive by remember { mutableStateOf(false) }
    val sendZeroVelocityJob = remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(currentStudentInfo, connectionStatus) {
        val student = currentStudentInfo
        val currentConnStatus = connectionStatus

        println("HomePage LE: StudentID=${student?.id}, ConnStatus=$currentConnStatus, TriggerFlag=$autoConnectSequenceTriggered")

        if (student != null && currentConnStatus is ConnectionStatus.Disconnected && !autoConnectSequenceTriggered) {
            autoConnectSequenceTriggered = true

            currentOperationMessage = "Configurazione sessione per ${student.name} (ID: ${student.id})..."
            QuizClient.configure(student.id, student.name)
            println("HomePage LE: QuizClient configured for ${student.id}")
            delay(200)

            currentOperationMessage = "Ricerca dispositivo professore sulla rete..."
            println("HomePage LE: Starting port scan.")
            val foundIp = portScanner.findProfessorDevice()
            println("HomePage LE: Port scan finished. IP Found: $foundIp")

            if (foundIp != null) {
                currentOperationMessage = "Professore trovato a $foundIp. Connessione in corso..."
                println("HomePage LE: Calling QuizClient.connect to $foundIp")
                QuizClient.connect(
                    serverIp = foundIp,
                    serverPort = DEFAULT_SERVER_PORT,
                    path = DEFAULT_SERVER_PATH
                )
            } else {
                currentOperationMessage = "Dispositivo professore non trovato. Controlla la rete Wi-Fi e che il professore abbia avviato la sessione. Puoi ritentare."
            }
        } else if (student != null && currentConnStatus == ConnectionStatus.Connected) {
            currentOperationMessage = "Connesso come ${student.name}!"
            autoConnectSequenceTriggered = true
        } else if (student == null) {
            currentOperationMessage = "Dati studente non trovati. Completa la registrazione."
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("MirOriento - Home / Controllo") }) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                if (currentStudentInfo != null) {
                    val currentScanStatusVal = scanStatus
                    val currentConnectionStatusVal = connectionStatus

                    if (currentScanStatusVal == PortScanner.ScanStatus.Scanning || currentConnectionStatusVal is ConnectionStatus.Connecting) {
                        CircularProgressIndicator(modifier = Modifier.padding(vertical = 16.dp))
                    }

                    ConnectionStatusDisplay(currentConnectionStatusVal)
                    RobotInfoDisplay(robotStatus)

                    if (currentConnectionStatusVal !is ConnectionStatus.Connected &&
                        currentConnectionStatusVal !is ConnectionStatus.Connecting &&
                        currentScanStatusVal != PortScanner.ScanStatus.Scanning
                    ) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                autoConnectSequenceTriggered = false
                                currentOperationMessage = "Ritentando la procedura automatica..."
                            },
                            modifier = Modifier.fillMaxWidth(0.8f).height(50.dp)
                        ) {
                            Text("Ritenta Scansione e Connessione", style = MaterialTheme.typography.button)
                        }
                    }
                    if (!showJoystick || currentConnectionStatusVal != ConnectionStatus.Connected) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            if (showJoystick && connectionStatus == ConnectionStatus.Connected) {
                JoystickController(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp, start = 32.dp, end = 32.dp),
                    size = 180.dp,
                    onVelocityChanged = { linearFromJoystick, angularFromJoystick ->
                        val robotLinearControl = linearFromJoystick
                        val robotAngularControl = -angularFromJoystick

                        isJoystickCurrentlyActive = abs(robotLinearControl) > 0.01f || abs(robotAngularControl) > 0.01f
                        sendZeroVelocityJob.value?.cancel()

                        if (isJoystickCurrentlyActive) {
                            coroutineScope.launch {
                                QuizClient.sendVelocity(robotLinearControl, robotAngularControl)
                            }
                        } else {
                            sendZeroVelocityJob.value = coroutineScope.launch {
                                delay(100)
                                QuizClient.sendVelocity(0f, 0f)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ConnectionStatusDisplay(status: ConnectionStatus) {
    val (text, color) = when (status) {
        is ConnectionStatus.Connected -> "Stato Connessione: Connesso!" to Color(0xFF4CAF50)
        is ConnectionStatus.Connecting -> "Stato Connessione: Connessione (${status.message})..." to MaterialTheme.colors.onSurface
        is ConnectionStatus.Disconnected -> "Stato Connessione: Disconnesso (${status.reason})" to MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
        is ConnectionStatus.Error -> "Stato Connessione: Errore (${status.message})" to Color(0xFFF44336)
    }
    Text(text, color = color, style = MaterialTheme.typography.subtitle1, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun RobotInfoDisplay(robotStatus: RobotStatus?) {
    val batteryText = robotStatus?.let { "Batteria Robot: ${it.battery_percentage.toInt()}%" } ?: "Batteria Robot: N/A"
    val positionText = robotStatus?.let { "Coordinate Robot: ${it.position.x}, ${it.position.y}, ${it.position.orientation}" } ?: "Coordinate Robot: N/A"
    val robotNameText = robotStatus?.let { "Nome Robot: ${it.robotName}" } ?: "Nome Robot: N/A"
    val uptimeText = robotStatus?.let { "Tempo funzionamento: ${it.uptime} min" } ?: "Tempo funzionamento: N/A"
    val stateText = robotStatus?.let { "Stato: ${it.stateText}" } ?: "Stato: N/A"

    Text(batteryText, style = MaterialTheme.typography.body1, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 8.dp))
    Text(positionText, style = MaterialTheme.typography.body1, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 8.dp))
    Text(robotNameText, style = MaterialTheme.typography.body1, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 8.dp))
    Text(uptimeText, style = MaterialTheme.typography.body1, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 8.dp))
    Text(stateText, style = MaterialTheme.typography.body1, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 8.dp))
}