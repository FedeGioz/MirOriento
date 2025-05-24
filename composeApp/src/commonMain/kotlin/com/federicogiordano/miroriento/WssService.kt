package com.federicogiordano.miroriento

import io.ktor.client.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.parameter
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

@Serializable
data class StudentConnection(
    val id: String,
    val name: String
)

@Serializable
data class WebSocketMessage(
    val type: String,
    val content: String,
    val sender: String
)

enum class ConnectionState {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    ERROR
}

class WebSocketClient(
    var serverUrl: String,
    var port: Int,
    var studentInfo: StudentConnection
) {
    private val client = HttpClient {
        install(WebSockets)
        install(HttpTimeout) {
            requestTimeoutMillis = 5000
            connectTimeoutMillis = 5000
            socketTimeoutMillis = 5000
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _errorDetails = MutableStateFlow<String?>(null)
    val errorDetails: StateFlow<String?> = _errorDetails.asStateFlow()

    private val _receivedMessages = MutableSharedFlow<WebSocketMessage>()
    val receivedMessages: SharedFlow<WebSocketMessage> = _receivedMessages.asSharedFlow()

    private var webSocketSession: DefaultClientWebSocketSession? = null
    private var connectionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun connect() {
        if (_connectionState.value == ConnectionState.CONNECTED || _connectionState.value == ConnectionState.CONNECTING) {
            println("WebSocketClient: Already connected or connecting.")
            return
        }
        _connectionState.value = ConnectionState.CONNECTING
        _errorMessage.value = null
        _errorDetails.value = null
        println("WebSocketClient: Attempting to connect to ws://$serverUrl:$port/connect?studentId=${studentInfo.id}&studentName=${studentInfo.name}")

        connectionJob = scope.launch {
            try {
                client.webSocket(
                    method = HttpMethod.Get,
                    host = serverUrl,
                    port = port,
                    path = "/connect",
                    request = {
                        parameter("studentId", studentInfo.id)
                        parameter("studentName", studentInfo.name)
                    }
                ) {
                    webSocketSession = this
                    _connectionState.value = ConnectionState.CONNECTED
                    println("WebSocketClient: Connected successfully to ws://$serverUrl:$port/connect")

                    val initialMessage = WebSocketMessage(
                        type = "STUDENT_CONNECTION",
                        content = json.encodeToString(studentInfo),
                        sender = studentInfo.id
                    )
                    sendMessage(initialMessage)

                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                println("WebSocketClient: Raw message received: $text")
                                try {
                                    val msg = json.decodeFromString<WebSocketMessage>(text)
                                    _receivedMessages.emit(msg)
                                } catch (e: Exception) {
                                    println("WebSocketClient: Error deserializing message: $text, Error: ${e.message}")
                                    _errorMessage.value = "Error processing message: ${e.message}"
                                }
                            }
                        }
                    } catch (e: ClosedReceiveChannelException) {
                        println("WebSocketClient: Connection closed by server: ${e.message}")
                        if (_connectionState.value != ConnectionState.ERROR) {
                            _connectionState.value = ConnectionState.DISCONNECTED
                        }
                    } catch (e: Exception) {
                        val errorMsg = "Error during active session: ${e.message}"
                        _errorMessage.value = errorMsg
                        _errorDetails.value = e.stackTraceToString()
                        println("WebSocketClient: $errorMsg")
                        println(e.stackTraceToString())
                        _connectionState.value = ConnectionState.ERROR
                    }
                }
            } catch (e: Exception) {
                val errorMsg = "Connection error: ${e.message}"
                _errorMessage.value = errorMsg
                _errorDetails.value = e.stackTraceToString()
                println("WebSocketClient: $errorMsg")
                println(e.stackTraceToString())
                _connectionState.value = ConnectionState.ERROR
            } finally {
                webSocketSession = null
                if (_connectionState.value != ConnectionState.ERROR && _connectionState.value != ConnectionState.CONNECTING) {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    println("WebSocketClient: Disconnected (finally block).")
                } else if (_connectionState.value == ConnectionState.CONNECTING) {
                    _connectionState.value = ConnectionState.ERROR
                    if (_errorMessage.value == null) _errorMessage.value = "Failed to establish connection."
                    println("WebSocketClient: Connection attempt failed (finally block).")
                }
            }
        }
    }

    suspend fun disconnect() {
        println("WebSocketClient: disconnect() called.")
        _connectionState.value = ConnectionState.DISCONNECTED
        connectionJob?.cancelAndJoin()
        try {
            webSocketSession?.close(CloseReason(CloseReason.Codes.NORMAL, "Client disconnected"))
            println("WebSocketClient: WebSocket session close initiated.")
        } catch (e: Exception) {
            _errorMessage.value = "Error during disconnection: ${e.message}"
            println("WebSocketClient: Error during WebSocket session close: ${e.message}")
        } finally {
            webSocketSession = null
            println("WebSocketClient: Disconnected. Session is now null.")
        }
    }

    suspend fun sendMessage(message: WebSocketMessage) {
        val session = webSocketSession
        if (session != null && _connectionState.value == ConnectionState.CONNECTED) {
            try {
                val jsonMessage = json.encodeToString(message)
                println("WebSocketClient: Sending raw message: $jsonMessage")
                session.send(Frame.Text(jsonMessage))
            } catch (e: Exception) {
                _errorMessage.value = "Error sending message: ${e.message}"
                _errorDetails.value = e.stackTraceToString()
                println("WebSocketClient: Error sending message: ${e.message}")
            }
        } else {
            val reason = if (session == null) "session is null" else "not connected (state: ${_connectionState.value})"
            _errorMessage.value = "Cannot send message: $reason"
            println("WebSocketClient: Cannot send message, $reason.")
        }
    }

    fun updateConnectionParams(newUrl: String, newPort: Int, newStudentInfo: StudentConnection) {
        serverUrl = newUrl
        port = newPort
        studentInfo = newStudentInfo
        println("WebSocketClient: Connection parameters updated. New URL: $serverUrl, Port: $port, Student: ${studentInfo.name}")
    }

    fun clearError() {
        _errorMessage.value = null
        _errorDetails.value = null
    }
}