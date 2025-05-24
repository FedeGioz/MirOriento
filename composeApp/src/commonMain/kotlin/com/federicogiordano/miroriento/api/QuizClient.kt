package com.federicogiordano.miroriento.api

import com.federicogiordano.miroriento.data.Quiz
import com.federicogiordano.miroriento.data.QuizAnswer
import com.federicogiordano.miroriento.data.StudentConnection
import com.federicogiordano.miroriento.data.WebSocketMessage
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

sealed class ConnectionStatus {
    data class Disconnected(val reason: String) : ConnectionStatus()
    data class Connecting(val message: String) : ConnectionStatus()
    object Connected : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()
}

@OptIn(ExperimentalTime::class)
class QuizClient(
    private val studentId: String,
    private val studentName: String
) {
    private val clientScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private var httpClient: HttpClient? = null
    private var webSocketSession: DefaultClientWebSocketSession? = null

    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(
        ConnectionStatus.Disconnected(
            "Not connected yet"
        )
    )
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _currentQuiz = MutableStateFlow<Quiz?>(null)
    val currentQuiz: StateFlow<Quiz?> = _currentQuiz.asStateFlow()

    private val _submittedAnswers = MutableStateFlow<Map<String, QuizAnswer>>(emptyMap())
    val submittedAnswers: StateFlow<Map<String, QuizAnswer>> = _submittedAnswers.asStateFlow()

    init {
        println("QuizClient: Initialized for Student ID: '$studentId', Name: '$studentName'")
    }

    private fun initializeHttpClient() {
        if (httpClient == null) {
            println("QuizClient: Initializing HttpClient.")
            httpClient = HttpClient(CIO) {
                install(WebSockets)
            }
        }
    }

    suspend fun connect(serverIp: String, serverPort: Int = 8080, path: String) {
        println("QuizClient: connect() called. Server: $serverIp:$serverPort$path, Student: $studentId ($studentName)")
        if (_connectionStatus.value is ConnectionStatus.Connected || _connectionStatus.value is ConnectionStatus.Connecting) {
            println("QuizClient: Already connected or attempting to connect. Current status: ${_connectionStatus.value}")
            return
        }

        initializeHttpClient()
        val connectUrl = "ws://$serverIp:$serverPort$path"
        _connectionStatus.value =
            ConnectionStatus.Connecting("Attempting to connect to $connectUrl")
        println("QuizClient: Status set to Connecting. Resetting client state for new session.")
        resetClientStateForNewSession()

        clientScope.launch {
            try {
                println("QuizClient: Attempting WebSocket connection to $connectUrl")
                httpClient!!.webSocket(
                    method = HttpMethod.Get,
                    host = serverIp,
                    port = serverPort,
                    path = path
                ) {
                    webSocketSession = this
                    println("QuizClient: WebSocket session established with $connectUrl.")

                    val studentConnectionInfo = StudentConnection(this@QuizClient.studentId, this@QuizClient.studentName)
                    val initialMessageJson = json.encodeToString(studentConnectionInfo)
                    send(Frame.Text(initialMessageJson))
                    println("QuizClient: Sent initial StudentConnection message: $initialMessageJson")


                    _connectionStatus.value = ConnectionStatus.Connected
                    println("QuizClient: Status set to Connected. Starting to listen for messages.")
                    listenForMessages()
                }
            } catch (e: CancellationException) {
                println("QuizClient: Connection coroutine cancelled: ${e.message}")
                _connectionStatus.value = ConnectionStatus.Disconnected("Connection cancelled")
            } catch (e: Exception) {
                println("QuizClient: CRITICAL - Failed to connect or error during session with $connectUrl: ${e.message}")
                e.printStackTrace()
                _connectionStatus.value =
                    ConnectionStatus.Error("Connection failed: ${e.message ?: "Unknown error"}")
            } finally {
                println("QuizClient: WebSocket block in connect() finished for $connectUrl.")
                webSocketSession = null
                if (_connectionStatus.value is ConnectionStatus.Connected) {
                    _connectionStatus.value =
                        ConnectionStatus.Disconnected("Session ended or listener stopped")
                }
            }
        }
    }

    private fun resetClientStateForNewSession() {
        println("QuizClient: resetClientStateForNewSession called.")
        _currentQuiz.value = null
        _submittedAnswers.value = emptyMap()
    }

    private suspend fun DefaultClientWebSocketSession.listenForMessages() {
        try {
            println("QuizClient: Starting to listen for messages from ${call.request.url}...")
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    println("QuizClient: Raw message received: $text")
                    try {
                        val webSocketMessage = json.decodeFromString<WebSocketMessage>(text)
                        handleWebSocketMessage(webSocketMessage)
                    } catch (e: SerializationException) {
                        println("QuizClient: CRITICAL - Error deserializing WebSocketMessage: ${e.message}. Raw text: $text")
                    } catch (e: Exception) {
                        println("QuizClient: CRITICAL - Unexpected error processing frame content: ${e.message}. Raw text: $text")
                    }
                } else if (frame is Frame.Close) {
                    println("QuizClient: Received Close frame: ${frame.readReason()}")
                }
            }
        } catch (e: ClosedReceiveChannelException) {
            println("QuizClient: WebSocket channel closed by remote: ${e.message}")
            if (_connectionStatus.value !is ConnectionStatus.Disconnected && _connectionStatus.value !is ConnectionStatus.Error) {
                _connectionStatus.value =
                    ConnectionStatus.Disconnected("Connection closed by server")
            }
        } catch (e: CancellationException) {
            println("QuizClient: Message listening coroutine cancelled: ${e.message}")
        } catch (e: Exception) {
            println("QuizClient: CRITICAL - Error in message listening loop: ${e.message}")
            e.printStackTrace()
            if (_connectionStatus.value !is ConnectionStatus.Disconnected && _connectionStatus.value !is ConnectionStatus.Error) {
                _connectionStatus.value =
                    ConnectionStatus.Error("Error receiving messages: ${e.message}")
            }
        } finally {
            println("QuizClient: Stopped listening for messages (listenForMessages finally block).")
        }
    }

    private fun handleWebSocketMessage(message: WebSocketMessage) {
        println("QuizClient: Handling message type '${message.type}' from sender '${message.sender}'.")

        when (message.type) {
            "QUIZ" -> {
                if (message.sender == "professor") {
                    try {
                        val quiz = json.decodeFromString<Quiz>(message.content)
                        _currentQuiz.value = quiz
                        _submittedAnswers.value = emptyMap()
                        println("QuizClient: Quiz '${quiz.title}' (ID: ${quiz.id}) received and parsed successfully. ${quiz.questions.size} questions.")
                    } catch (e: Exception) {
                        println("QuizClient: CRITICAL - Error parsing QUIZ message: ${e.message}. Content: ${message.content}")
                        _connectionStatus.value =
                            ConnectionStatus.Error("Failed to parse quiz data: ${e.message}")
                    }
                } else {
                    println("QuizClient: Received QUIZ message not from professor. Sender: ${message.sender}. Ignoring.")
                }
            }
            "ANSWER_RECEIVED" -> {
                println("QuizClient: Received 'ANSWER_RECEIVED' confirmation from server. Content: ${message.content}")
            }
            "ANSWER_EVALUATED" -> {
                println("QuizClient: Attempting to process ANSWER_EVALUATED. Content: ${message.content}")
                try {
                    val rawEvaluatedAnswer = json.decodeFromString<QuizAnswer>(message.content)
                    println("QuizClient: Successfully deserialized ANSWER_EVALUATED for Answer ID '${rawEvaluatedAnswer.id}', Question ID '${rawEvaluatedAnswer.questionId}'. Raw 'isCorrect': ${rawEvaluatedAnswer.isCorrect}, Student: '${rawEvaluatedAnswer.studentId}'.")

                    val finalAnswerState = if (rawEvaluatedAnswer.isCorrect == null) {
                        println("QuizClient: 'isCorrect' is null in evaluated answer for QID '${rawEvaluatedAnswer.questionId}'. Interpreting as 'false'.")
                        rawEvaluatedAnswer.copy(isCorrect = false)
                    } else {
                        rawEvaluatedAnswer
                    }

                    if (finalAnswerState.studentId == this.studentId) {
                        _submittedAnswers.update { currentAnswers ->
                            val newAnswers = currentAnswers.toMutableMap()
                            newAnswers[finalAnswerState.questionId] = finalAnswerState
                            println("QuizClient: Updated submittedAnswers for questionId '${finalAnswerState.questionId}' with evaluated answer (isCorrect: ${finalAnswerState.isCorrect}).")
                            newAnswers.toMap()
                        }
                    } else {
                        println("QuizClient: Received ANSWER_EVALUATED for another student ('${finalAnswerState.studentId}'). Ignoring for this client ('${this.studentId}').")
                    }
                } catch (e: Exception) {
                    println("QuizClient: CRITICAL - Error processing ANSWER_EVALUATED message: ${e.message}. Content: ${message.content}")
                }
            }
            "SESSION_ENDED" -> {
                println("QuizClient: Received SESSION_ENDED message from sender '${message.sender}'. Content: ${message.content}")
                _currentQuiz.value = null
                _connectionStatus.value =
                    ConnectionStatus.Disconnected("Session ended by professor: ${message.content}")
            }
            else -> {
                println("QuizClient: Received unknown message type: '${message.type}'. Sender: '${message.sender}'. Content: ${message.content}")
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    suspend fun sendAnswer(questionId: String, selectedOptionValue: String) {
        val activeQuiz = _currentQuiz.value ?: run {
            println("QuizClient: Cannot send answer, no active quiz.")
            return
        }
        val session = webSocketSession ?: run {
            println("QuizClient: Cannot send answer, not connected.")
            _connectionStatus.value = ConnectionStatus.Error("Not connected. Cannot send answer.")
            return
        }

        val answerId = "ans_${this.studentId}_${questionId}_${Clock.System.now().toEpochMilliseconds()}_${Random.nextInt(10000)}"

        val quizAnswer = QuizAnswer(
            id = answerId,
            quizId = activeQuiz.id,
            questionId = questionId,
            studentId = this.studentId,
            studentName = this.studentName,
            answer = selectedOptionValue,
            isCorrect = null
        )

        try {
            val message = WebSocketMessage(
                type = "ANSWER",
                content = json.encodeToString(quizAnswer),
                sender = this.studentId
            )
            val messageJson = json.encodeToString(message)
            println("QuizClient: Sending raw ANSWER message: $messageJson")
            session.send(Frame.Text(messageJson))

            _submittedAnswers.update { currentAnswers ->
                currentAnswers + (quizAnswer.questionId to quizAnswer)
            }
            println("QuizClient: Sent answer for question $questionId (Answer ID: $answerId). Optimistically updated UI using questionId '${quizAnswer.questionId}' as key.")
        } catch (e: Exception) {
            println("QuizClient: Error sending answer for question $questionId: ${e.message}")
            _connectionStatus.value =
                ConnectionStatus.Error("Failed to send answer: ${e.message ?: "Unknown error"}")
        }
    }

    suspend fun disconnect() {
        println("QuizClient: disconnect() called. Current status: ${_connectionStatus.value}")
        if (_connectionStatus.value is ConnectionStatus.Disconnected && webSocketSession == null) {
            println("QuizClient: Already disconnected and session is null.")
            return
        }
        val previousStatus = _connectionStatus.value
        _connectionStatus.value = ConnectionStatus.Disconnected("User initiated disconnect")
        try {
            webSocketSession?.close(CloseReason(CloseReason.Codes.NORMAL, "Client initiated disconnect"))
            println("QuizClient: WebSocket session close initiated.")
        } catch (e: Exception) {
            println("QuizClient: Error during WebSocket session close: ${e.message}")
        }
        webSocketSession = null
        println("QuizClient: Disconnected. Previous status: $previousStatus. Session is now null.")
    }

    fun cleanup() {
        println("QuizClient: cleanup() called for Student ID '$studentId'. Cancelling scope and closing HTTP client.")
        clientScope.launch {
            disconnect()
        }
        clientScope.cancel("QuizClient cleanup initiated")

        httpClient?.close()
        httpClient = null
        println("QuizClient: HTTP client closed. Resources cleaned up for Student ID '$studentId'.")
    }
}