package com.federicogiordano.miroriento.screens

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

private const val DEFAULT_SERVER_PORT = 8080
private const val DEFAULT_SERVER_PATH = "/connect"

@OptIn(ExperimentalMaterial3Api::class)
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

        println("HomePage LE (Sequenza di Connessione): Attivato con segnale $triggerValue. Studente: ${student?.id}")

        if (student == null) {
            currentOperationMessage = "Registrazione studente richiesta."
            println("HomePage LE: Nessuna informazione studente, annullamento sequenza di connessione.")
            return@LaunchedEffect
        }

        val currentConnStatusSnapshot = QuizClient.connectionStatus.value
        if (!(currentConnStatusSnapshot is ConnectionStatus.Disconnected || currentConnStatusSnapshot is ConnectionStatus.Error)) {
            println("HomePage LE: Sequenza di connessione saltata per il segnale $triggerValue. Lo stato è $currentConnStatusSnapshot, non Disconnesso/Errore.")
            return@LaunchedEffect
        }

        currentOperationMessage = "Avvio procedura di connessione..."
        println("HomePage LE: Avvio sequenza di connessione completa per il segnale $triggerValue. Stato attuale: $currentConnStatusSnapshot")

        currentOperationMessage = "Configurazione sessione per ${student.name}..."
        QuizClient.configure(student.id, student.name)

        currentOperationMessage = "Ricerca dispositivo professore sulla rete..."
        val foundIp = portScanner.findProfessorDevice()
        println("HomePage LE: Scansione porte terminata. IP Trovato: $foundIp")

        if (foundIp != null) {
            currentOperationMessage = "Professore trovato ($foundIp). Tentativo di connessione..."
            QuizClient.connect(
                serverIp = foundIp,
                serverPort = DEFAULT_SERVER_PORT,
                path = DEFAULT_SERVER_PATH
            )
        } else {
            currentOperationMessage = "Dispositivo professore non trovato. Puoi ritentare."
        }
        println("HomePage LE: Sequenza di connessione terminata per il segnale $triggerValue.")
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
        topBar = {
            TopAppBar(
                title = { Text("MirOriento - Home") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
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
                    Text(
                        currentOperationMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (scanStatus == PortScanner.ScanStatus.Scanning || connectionStatus is ConnectionStatus.Connecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(vertical = 16.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    ConnectionStatusDisplay(connectionStatus)
                    RobotInfoDisplay(robotStatus)

                    Spacer(modifier = Modifier.height(16.dp))

                    if (connectionStatus !is ConnectionStatus.Connected &&
                        connectionStatus !is ConnectionStatus.Connecting &&
                        scanStatus != PortScanner.ScanStatus.Scanning) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                println("HomePage: Pulsante 'Ritenta Scansione e Connessione' cliccato (connectionTrigger prima: $connectionTrigger).")
                                coroutineScope.launch {
                                    currentOperationMessage = "Reset e nuova scansione in corso..."
                                    QuizClient.disconnect("User requested retry from HomePage")
                                    connectionTrigger++
                                    println("HomePage: connectionTrigger dopo incremento: $connectionTrigger")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(0.8f).height(50.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            )
                        ) {
                            Text(
                                "Ritenta Scansione e Connessione",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    if (!showJoystick || connectionStatus != ConnectionStatus.Connected) {
                        Spacer(Modifier.weight(1f))
                    }

                } else {
                    Text(
                        currentOperationMessage,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
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
                        val robotAngularControl = -angularFromJoystick
                        isJoystickCurrentlyActive = abs(linearFromJoystick) > 0.01f || abs(robotAngularControl) > 0.01f
                        sendZeroVelocityJob.value?.cancel()
                        if (isJoystickCurrentlyActive) {
                            coroutineScope.launch { QuizClient.sendVelocity(
                                linearFromJoystick,
                                robotAngularControl
                            ) }
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
        is ConnectionStatus.Connected -> "Stato Connessione: Connesso!" to MaterialTheme.colorScheme.primary
        is ConnectionStatus.Connecting -> "Stato Connessione: Connessione (${status.message})..." to MaterialTheme.colorScheme.onSurface
        is ConnectionStatus.Disconnected -> "Stato Connessione: Disconnesso (${status.reason})" to MaterialTheme.colorScheme.onSurfaceVariant
        is ConnectionStatus.Error -> "Stato Connessione: Errore (${status.message})" to MaterialTheme.colorScheme.error
    }
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun RobotInfoDisplay(robotStatus: RobotStatus?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = robotStatus?.robotName ?: "Robot: N/A",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val batteryText = robotStatus?.let { "${it.battery_percentage.toInt()}%" } ?: "N/A"
            val stateText = robotStatus?.stateText ?: "N/A"
            val positionText = robotStatus?.position?.let { pos ->
                val x = pos.x?.let { kotlin.math.round(it * 100) / 100.0 }?.toString() ?: "N/A"
                val y = pos.y?.let { kotlin.math.round(it * 100) / 100.0 }?.toString() ?: "N/A"
                val o = pos.orientation?.let { kotlin.math.round(it * 10) / 10.0 }?.toString() ?: "N/A"
                "X: $x, Y: $y, \u03B8: $o°"
            } ?: "N/A"

            InfoRow(label = "Stato:", value = stateText)
            InfoRow(label = "Batteria:", value = batteryText)
            InfoRow(label = "Posizione:", value = positionText)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.6f)
        )
    }
}