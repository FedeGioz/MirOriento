package com.federicogiordano.miroriento.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import com.federicogiordano.miroriento.utils.rememberBase64ImageBitmap

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
    val currentMapBase64 by QuizClient.currentMapBase64.collectAsState()

    val portScanner = remember { PortScanner() }
    val scanStatus by portScanner.scanStatus.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    var currentOperationMessage by remember { mutableStateOf("In attesa di registrazione studente...") }

    var connectionTrigger by remember(currentStudentInfo?.id) { mutableStateOf(1) }

    var isJoystickCurrentlyActive by remember { mutableStateOf(false) }
    val sendZeroVelocityJob = remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(connectionTrigger, currentStudentInfo?.id) {
        val student = currentStudentInfo
        val triggerValue = connectionTrigger

        println("HomePage LE (Connection Sequence): Triggered with signal $triggerValue. Student: ${student?.id}")

        if (student == null) {
            currentOperationMessage = "Registrazione studente richiesta."
            println("HomePage LE: No student info, aborting connection sequence.")
            return@LaunchedEffect
        }

        val currentConnStatusSnapshot = QuizClient.connectionStatus.value
        if (!(currentConnStatusSnapshot is ConnectionStatus.Disconnected || currentConnStatusSnapshot is ConnectionStatus.Error)) {
            println("HomePage LE: Skipped connection sequence for signal $triggerValue. Status is $currentConnStatusSnapshot, not Disconnected/Error.")
            return@LaunchedEffect
        }

        currentOperationMessage = "Avvio procedura di connessione (Signal: $triggerValue)..."
        println("HomePage LE: Starting full connection sequence for signal $triggerValue. Current status: $currentConnStatusSnapshot")

        currentOperationMessage = "Configurazione sessione per ${student.name}..."
        QuizClient.configure(student.id, student.name)

        currentOperationMessage = "Ricerca dispositivo professore sulla rete..."
        val foundIp = portScanner.findProfessorDevice()
        println("HomePage LE: Port scan finished. IP Found: $foundIp (Signal: $triggerValue)")

        if (foundIp != null) {
            currentOperationMessage = "Professore trovato ($foundIp). Tentativo di connessione..."
            QuizClient.connect(
                serverIp = foundIp,
                serverPort = DEFAULT_SERVER_PORT,
                path = DEFAULT_SERVER_PATH
            )
        } else {
            currentOperationMessage = "Dispositivo professore non trovato (Signal: $triggerValue). Puoi ritentare."
        }
        println("HomePage LE: Connection sequence finished for signal $triggerValue.")
    }

    LaunchedEffect(connectionStatus, currentStudentInfo) {
        val student = currentStudentInfo
        val currentConnStatus = connectionStatus

        if (student != null) {
            if (currentOperationMessage.contains("(Signal:") &&
                (currentConnStatus is ConnectionStatus.Connecting || scanStatus == PortScanner.ScanStatus.Scanning)) {
            } else {
                when (currentConnStatus) {
                    is ConnectionStatus.Connected -> currentOperationMessage = "Connesso come ${student.name}!"
                    is ConnectionStatus.Connecting -> currentOperationMessage = "Connessione in corso: ${currentConnStatus.message}..."
                    is ConnectionStatus.Disconnected -> {
                        if (!currentOperationMessage.startsWith("Dispositivo professore non trovato")) {
                            currentOperationMessage = "Disconnesso: ${currentConnStatus.reason}. Puoi ritentare."
                        }
                    }
                    is ConnectionStatus.Error -> {
                        if (!currentOperationMessage.startsWith("Dispositivo professore non trovato")) {
                            currentOperationMessage = "Errore: ${currentConnStatus.message}. Puoi ritentare."
                        }
                    }
                }
            }
        } else {
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
                    Text(currentOperationMessage, style = MaterialTheme.typography.subtitle2, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 8.dp))

                    if (scanStatus == PortScanner.ScanStatus.Scanning || connectionStatus is ConnectionStatus.Connecting) {
                        CircularProgressIndicator(modifier = Modifier.padding(vertical = 16.dp))
                    }

                    ConnectionStatusDisplay(connectionStatus)
                    RobotInfoDisplay(robotStatus)

                    Spacer(modifier = Modifier.height(16.dp))
                    MapDisplay(mapBase64 = currentMapBase64)

                    if (connectionStatus !is ConnectionStatus.Connected &&
                        connectionStatus !is ConnectionStatus.Connecting &&
                        scanStatus != PortScanner.ScanStatus.Scanning) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                println("HomePage: 'Ritenta Scansione e Connessione' button clicked (connectionTrigger before: $connectionTrigger).")
                                coroutineScope.launch {
                                    currentOperationMessage = "Reset e nuova scansione in corso..."
                                    QuizClient.disconnect("User requested retry from HomePage")
                                    connectionTrigger++
                                    println("HomePage: connectionTrigger after increment: $connectionTrigger")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(0.8f).height(50.dp)
                        ) {
                            Text("Ritenta Scansione e Connessione", style = MaterialTheme.typography.button)
                        }
                    }

                    if (!showJoystick || connectionStatus != ConnectionStatus.Connected) {
                        Spacer(Modifier.weight(1f))
                    }

                } else {
                    Text(currentOperationMessage, style = MaterialTheme.typography.h6, textAlign = TextAlign.Center)
                }
            }

            if (showJoystick && connectionStatus == ConnectionStatus.Connected) {
                JoystickController(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp, start = 32.dp, end = 32.dp)
                        .fillMaxWidth(),
                    size = 180.dp,
                    onVelocityChanged = { linearFromJoystick, angularFromJoystick ->
                        val robotLinearControl = linearFromJoystick
                        val robotAngularControl = -angularFromJoystick
                        isJoystickCurrentlyActive = abs(robotLinearControl) > 0.01f || abs(robotAngularControl) > 0.01f
                        sendZeroVelocityJob.value?.cancel()
                        if (isJoystickCurrentlyActive) {
                            coroutineScope.launch { QuizClient.sendVelocity(robotLinearControl, robotAngularControl) }
                        } else {
                            sendZeroVelocityJob.value = coroutineScope.launch {
                                delay(100)
                                if (!isJoystickCurrentlyActive) QuizClient.sendVelocity(0f, 0f)
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
    val stateText = robotStatus?.let { "Stato Robot: ${it.stateText}" } ?: "Stato Robot: N/A"
    val positionText = robotStatus?.position?.let { pos ->
        val x = pos.x?.let { kotlin.math.round(it * 100) / 100.0 }?.toString() ?: "N/A"
        val y = pos.y?.let { kotlin.math.round(it * 100) / 100.0 }?.toString() ?: "N/A"
        val o = pos.orientation?.let { kotlin.math.round(it * 10) / 10.0 }?.toString() ?: "N/A"
        "Posizione: X:$x, Y:$y, \u03B8:$o°"
    } ?: "Posizione Robot: N/A"

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(robotStatus?.robotName ?: "Robot: N/A", style = MaterialTheme.typography.h6, textAlign = TextAlign.Center)
        Text(batteryText, style = MaterialTheme.typography.body1, textAlign = TextAlign.Center)
        Text(stateText, style = MaterialTheme.typography.body1, textAlign = TextAlign.Center)
        Text(positionText, style = MaterialTheme.typography.body1, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 8.dp))
    }
}

@Composable
private fun MapDisplay(mapBase64: String?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Text("Mappa Robot", style = MaterialTheme.typography.h6, modifier = Modifier.padding(bottom = 8.dp))
        val imageBitmap = rememberBase64ImageBitmap(mapBase64)
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Mappa del Robot",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.77f)
                    .background(Color.DarkGray)
                    .padding(2.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.77f)
                    .background(Color.Gray.copy(alpha = 0.2f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Mappa: N/A", style = MaterialTheme.typography.subtitle1)
            }
        }
    }
}