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

@Composable
fun WebSocketTestScreen() {
    var serverUrl by remember { mutableStateOf("192.168.181.178") }
    var port by remember { mutableStateOf("8080") }
    var studentId by remember { mutableStateOf("student1") }
    var studentName by remember { mutableStateOf("Test Student") }
    var messageContent by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val client = remember {
        WebSocketClient(
            serverUrl = serverUrl,
            port = port.toIntOrNull() ?: 8080,
            studentInfo = StudentConnection(id = studentId, name = studentName)
        )
    }

    val connectionState by client.connectionState.collectAsState(ConnectionState.DISCONNECTED)
    val receivedMessages = remember { mutableStateListOf<WebSocketMessage>() }

    LaunchedEffect(Unit) {
        client.receivedMessages.collect { message ->
            receivedMessages.add(0, message)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("WebSocket Test Client", style = MaterialTheme.typography.h5)

        TextField("Server URL", serverUrl) { serverUrl = it }
        TextField("Port", port) { port = it }

        TextField("Student ID", studentId) { studentId = it }
        TextField("Student Name", studentName) { studentName = it }

        ConnectionStatusBar(connectionState)

        Row {
            Button(
                onClick = {
                    scope.launch {
                        client.connect()
                    }
                },
                enabled = connectionState != ConnectionState.CONNECTED &&
                        connectionState != ConnectionState.CONNECTING
            ) {
                Text("Connect")
            }

            Button(
                onClick = {
                    scope.launch {
                        client.disconnect()
                    }
                },
                enabled = connectionState == ConnectionState.CONNECTED
            ) {
                Text("Disconnect")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Send Message", style = MaterialTheme.typography.subtitle1)

        TextField("Message", messageContent) { messageContent = it }

        Row {
            Button(
                onClick = {
                    scope.launch {
                        client.sendChatMessage(messageContent)
                        messageContent = ""
                    }
                },
                enabled = connectionState == ConnectionState.CONNECTED
            ) {
                Text("Chat")
            }

            Button(
                onClick = {
                    scope.launch {
                        client.sendQuizAnswer(messageContent)
                        messageContent = ""
                    }
                },
                enabled = connectionState == ConnectionState.CONNECTED
            ) {
                Text("Quiz Answer")
            }

            Button(
                onClick = {
                    scope.launch {
                        client.requestHelp(messageContent)
                        messageContent = ""
                    }
                },
                enabled = connectionState == ConnectionState.CONNECTED
            ) {
                Text("Help")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Received Messages", style = MaterialTheme.typography.subtitle1)

        LazyColumn {
            items(receivedMessages) { message ->
                MessageItem(message)
            }
        }
    }
}

@Composable
private fun TextField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}

@Composable
private fun ConnectionStatusBar(connectionState: ConnectionState) {
    val (color, text) = when(connectionState) {
        ConnectionState.DISCONNECTED -> Color.Gray to "Disconnected"
        ConnectionState.CONNECTING -> Color.Yellow to "Connecting..."
        ConnectionState.CONNECTED -> Color.Green to "Connected"
        ConnectionState.ERROR -> Color.Red to "Error"
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(color, shape = CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Status: $text")
    }
}

@Composable
private fun MessageItem(message: WebSocketMessage) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        elevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Type: ${message.type}",
                    style = MaterialTheme.typography.subtitle2
                )
                Text(
                    text = "From: ${message.sender}",
                    style = MaterialTheme.typography.caption
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(message.content)
        }
    }
}

@Composable
fun App() {
    MaterialTheme {
        WebSocketTestScreen()
    }
}