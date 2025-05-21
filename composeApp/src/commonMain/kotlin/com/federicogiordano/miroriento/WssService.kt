package com.federicogiordano.miroriento

import io.ktor.client.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
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
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

class WebSocketClient(
    var serverUrl: String,
    var port: Int,
    var studentInfo: StudentConnection
) {
    private val client = HttpClient {
        install(WebSockets) {
            pingInterval = 15.seconds
        }

        install(HttpTimeout) {
            connectTimeoutMillis = 10000
            requestTimeoutMillis = 30000
        }
    }

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
    private val scope = CoroutineScope(Dispatchers.IO)

    suspend fun connect() {
        if (_connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.CONNECTING
        ) {
            return
        }

        _connectionState.value = ConnectionState.CONNECTING
        _errorMessage.value = null
        _errorDetails.value = null

        try {
            println("Tentativo di connessione a WebSocket su $serverUrl:$port/connect")

            connectionJob = scope.launch {
                try {
                    client.webSocket(
                        method = HttpMethod.Get,
                        host = serverUrl,
                        port = port,
                        path = "/connect"
                    ) {
                        webSocketSession = this

                        send(Frame.Text(Json.encodeToString(studentInfo)))

                        _connectionState.value = ConnectionState.CONNECTED

                        try {
                            for (frame in incoming) {
                                when (frame) {
                                    is Frame.Text -> {
                                        val text = frame.readText()
                                        val message = Json.decodeFromString<WebSocketMessage>(text)
                                        _receivedMessages.emit(message)
                                    }
                                    else -> {}
                                }
                            }
                        } catch (e: Exception) {
                            _errorMessage.value = "Errore nella ricezione del messaggio: ${e.message}"
                            _connectionState.value = ConnectionState.ERROR
                        }
                    }
                } catch (e: Exception) {
                    val errorMsg = "Errore di connessione: ${e.message}"
                    _errorMessage.value = errorMsg
                    _errorDetails.value = e.stackTraceToString()

                    println("Errore WebSocket: ${e.message}")
                    println(e.stackTraceToString())

                    _connectionState.value = ConnectionState.ERROR
                } finally {
                    if (_connectionState.value != ConnectionState.ERROR) {
                        _connectionState.value = ConnectionState.DISCONNECTED
                    }
                }
            }
        } catch (e: Exception) {
            _errorMessage.value = "Impossibile avviare la connessione: ${e.message}"
            _connectionState.value = ConnectionState.ERROR
        }
    }

    suspend fun disconnect() {
        try {
            webSocketSession?.close(CloseReason(CloseReason.Codes.NORMAL, "Client disconnesso"))
            connectionJob?.cancelAndJoin()
        } catch (e: Exception) {
            _errorMessage.value = "Errore durante la disconnessione: ${e.message}"
        } finally {
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    suspend fun sendMessage(message: WebSocketMessage) {
        val session = webSocketSession
        if (session != null && _connectionState.value == ConnectionState.CONNECTED) {
            try {
                session.send(Frame.Text(Json.encodeToString(message)))
            } catch (e: Exception) {
                _errorMessage.value = "Errore durante l'invio del messaggio: ${e.message}"
                _connectionState.value = ConnectionState.ERROR
            }
        }
    }

    suspend fun sendQuizAnswer(answer: String) {
        sendMessage(
            WebSocketMessage(
                type = "QUIZ_ANSWER",
                content = answer,
                sender = studentInfo.id
            )
        )
    }

    suspend fun requestHelp(message: String = "Ho bisogno di assistenza") {
        sendMessage(
            WebSocketMessage(
                type = "HELP_REQUEST",
                content = message,
                sender = studentInfo.id
            )
        )
    }

    suspend fun sendChatMessage(content: String) {
        sendMessage(
            WebSocketMessage(
                type = "CHAT",
                content = content,
                sender = studentInfo.id
            )
        )
    }

    fun updateConnectionParams(newUrl: String, newPort: Int, newStudentInfo: StudentConnection) {
        if (_connectionState.value != ConnectionState.DISCONNECTED) {
            return
        }
        serverUrl = newUrl
        port = newPort
        studentInfo = newStudentInfo
    }
}