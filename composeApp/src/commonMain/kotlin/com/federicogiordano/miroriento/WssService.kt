package com.federicogiordano.miroriento

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.parameter
import io.ktor.http.HttpMethod
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

    private val _receivedMessages = MutableSharedFlow<WebSocketMessage>()

    private var webSocketSession: DefaultClientWebSocketSession? = null
    private var connectionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun connect() {
        if (_connectionState.value == ConnectionState.CONNECTED || _connectionState.value == ConnectionState.CONNECTING) {
            println("WebSocketClient: Già connesso o in fase di connessione.")
            return
        }
        _connectionState.value = ConnectionState.CONNECTING
        _errorMessage.value = null
        _errorDetails.value = null
        println("WebSocketClient: Tentativo di connessione a ws://$serverUrl:$port/connect?studentId=${studentInfo.id}&studentName=${studentInfo.name}")

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
                    println("WebSocketClient: Connesso con successo a ws://$serverUrl:$port/connect")

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
                                println("WebSocketClient: Messaggio raw ricevuto: $text")
                                try {
                                    val msg = json.decodeFromString<WebSocketMessage>(text)
                                    _receivedMessages.emit(msg)
                                } catch (e: Exception) {
                                    println("WebSocketClient: Errore durante la deserializzazione del messaggio: $text, Errore: ${e.message}")
                                    _errorMessage.value = "Errore durante l'elaborazione del messaggio: ${e.message}"
                                }
                            }
                        }
                    } catch (e: ClosedReceiveChannelException) {
                        println("WebSocketClient: Connessione chiusa dal server: ${e.message}")
                        if (_connectionState.value != ConnectionState.ERROR) {
                            _connectionState.value = ConnectionState.DISCONNECTED
                        }
                    } catch (e: Exception) {
                        val errorMsg = "Errore durante la sessione attiva: ${e.message}"
                        _errorMessage.value = errorMsg
                        _errorDetails.value = e.stackTraceToString()
                        println("WebSocketClient: $errorMsg")
                        println(e.stackTraceToString())
                        _connectionState.value = ConnectionState.ERROR
                    }
                }
            } catch (e: Exception) {
                val errorMsg = "Errore di connessione: ${e.message}"
                _errorMessage.value = errorMsg
                _errorDetails.value = e.stackTraceToString()
                println("WebSocketClient: $errorMsg")
                println(e.stackTraceToString())
                _connectionState.value = ConnectionState.ERROR
            } finally {
                webSocketSession = null
                if (_connectionState.value != ConnectionState.ERROR && _connectionState.value != ConnectionState.CONNECTING) {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    println("WebSocketClient: Disconnesso (blocco finally).")
                } else if (_connectionState.value == ConnectionState.CONNECTING) {
                    _connectionState.value = ConnectionState.ERROR
                    if (_errorMessage.value == null) _errorMessage.value = "Impossibile stabilire la connessione."
                    println("WebSocketClient: Tentativo di connessione fallito (blocco finally).")
                }
            }
        }
    }

    suspend fun disconnect() {
        println("WebSocketClient: disconnect() chiamato.")
        _connectionState.value = ConnectionState.DISCONNECTED
        connectionJob?.cancelAndJoin()
        try {
            webSocketSession?.close(CloseReason(CloseReason.Codes.NORMAL, "Client disconnesso"))
            println("WebSocketClient: Inizializzata la chiusura della sessione WebSocket.")
        } catch (e: Exception) {
            _errorMessage.value = "Errore durante la disconnessione: ${e.message}"
            println("WebSocketClient: Errore durante la chiusura della sessione WebSocket: ${e.message}")
        } finally {
            webSocketSession = null
            println("WebSocketClient: Disconnesso. La sessione è ora nulla.")
        }
    }

    private suspend fun sendMessage(message: WebSocketMessage) {
        val session = webSocketSession
        if (session != null && _connectionState.value == ConnectionState.CONNECTED) {
            try {
                val jsonMessage = json.encodeToString(message)
                println("WebSocketClient: Invio messaggio raw: $jsonMessage")
                session.send(Frame.Text(jsonMessage))
            } catch (e: Exception) {
                _errorMessage.value = "Errore durante l'invio del messaggio: ${e.message}"
                _errorDetails.value = e.stackTraceToString()
                println("WebSocketClient: Errore durante l'invio del messaggio: ${e.message}")
            }
        } else {
            val reason = if (session == null) "la sessione è nulla" else "non connesso (stato: ${_connectionState.value})"
            _errorMessage.value = "Impossibile inviare il messaggio: $reason"
            println("WebSocketClient: Impossibile inviare il messaggio, $reason.")
        }
    }

    fun updateConnectionParams(newUrl: String, newPort: Int, newStudentInfo: StudentConnection) {
        serverUrl = newUrl
        port = newPort
        studentInfo = newStudentInfo
        println("WebSocketClient: Parametri di connessione aggiornati. Nuova URL: $serverUrl, Porta: $port, Studente: ${studentInfo.name}")
    }

}