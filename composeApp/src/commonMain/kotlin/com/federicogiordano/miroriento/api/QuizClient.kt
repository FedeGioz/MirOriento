package com.federicogiordano.miroriento.api

import com.federicogiordano.miroriento.data.Quiz
import com.federicogiordano.miroriento.data.QuizAnswer
import com.federicogiordano.miroriento.data.RobotStatus
import com.federicogiordano.miroriento.data.SpeedCommand
import com.federicogiordano.miroriento.data.StudentConnection
import com.federicogiordano.miroriento.data.Vector3
import com.federicogiordano.miroriento.data.VelocityCommand
import com.federicogiordano.miroriento.data.VelocityMessage
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
object QuizClient {
    private val clientScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    private var httpClient: HttpClient? = null
    private var webSocketSession: DefaultClientWebSocketSession? = null

    private var currentStudentId: String? = null
    private var currentStudentName: String? = null

    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(
        ConnectionStatus.Disconnected("Awaiting connection attempt")
    )
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _currentQuiz = MutableStateFlow<Quiz?>(null)
    val currentQuiz: StateFlow<Quiz?> = _currentQuiz.asStateFlow()

    private val _submittedAnswers = MutableStateFlow<Map<String, QuizAnswer>>(emptyMap())
    val submittedAnswers: StateFlow<Map<String, QuizAnswer>> = _submittedAnswers.asStateFlow()

    private val _robotStatus = MutableStateFlow<RobotStatus?>(null)
    val robotStatus: StateFlow<RobotStatus?> = _robotStatus.asStateFlow()

    private val _showJoystick = MutableStateFlow(false)
    val showJoystick: StateFlow<Boolean> = _showJoystick.asStateFlow()

    private const val VELOCITY_COMMAND_OP = "publish"
    private const val VELOCITY_COMMAND_TOPIC = "/cmd_vel_joystick"
    private const val DEFAULT_JOYSTICK_TOKEN = "miroriento_joystick_user"

    init {
        println("QuizClient Singleton: Initialized.")
        initializeHttpClient()
    }

    fun configure(studentId: String, studentName: String) {
        this.currentStudentId = studentId
        this.currentStudentName = studentName
        println("QuizClient: Configured with Student ID: '$currentStudentId', Name: '$currentStudentName'")
        _showJoystick.value = false
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
        val studentId = currentStudentId
        val studentName = currentStudentName

        if (studentId.isNullOrBlank() || studentName.isNullOrBlank()) {
            _connectionStatus.value = ConnectionStatus.Error("Student details not configured. Call QuizClient.configure() first.")
            println("QuizClient: Connect failed - student details not configured.")
            return
        }

        println("QuizClient: connect() called. Server: $serverIp:$serverPort$path, Student: $studentId ($studentName)")
        if (connectionStatus.value == ConnectionStatus.Connected || connectionStatus.value is ConnectionStatus.Connecting) {
            println("QuizClient: Already connected or attempting to connect. Current status: ${connectionStatus.value}")
            return
        }

        initializeHttpClient()
        val connectUrl = "ws://$serverIp:$serverPort$path"
        _connectionStatus.value = ConnectionStatus.Connecting("Attempting to connect to $connectUrl")
        resetClientStateForNewSession()

        clientScope.launch {
            try {
                httpClient!!.webSocket(
                    method = HttpMethod.Get, host = serverIp, port = serverPort, path = path
                ) {
                    webSocketSession = this
                    println("QuizClient: WebSocket session established with $connectUrl.")
                    val studentConnectionInfo = StudentConnection(studentId, studentName)
                    val initialMessageJson = json.encodeToString(StudentConnection.serializer(), studentConnectionInfo)
                    send(Frame.Text(initialMessageJson))
                    println("QuizClient: Sent initial StudentConnection message: $initialMessageJson")
                    _connectionStatus.value = ConnectionStatus.Connected
                    println("QuizClient: Status set to Connected. Starting to listen for messages.")
                    listenForMessages()
                }
            } catch (e: CancellationException) {
                println("QuizClient: Connection coroutine cancelled: ${e.message}")
                if (connectionStatus.value !is ConnectionStatus.Disconnected) {
                    _connectionStatus.value = ConnectionStatus.Disconnected("Connection cancelled by client")
                }
            } catch (e: Exception) {
                println("QuizClient: CRITICAL - Failed to connect or error during session with $connectUrl: ${e.message}")
                e.printStackTrace()
                _connectionStatus.value = ConnectionStatus.Error("Connection failed: ${e.message ?: "Unknown error"}")
            } finally {
                println("QuizClient: WebSocket block in connect() finished for $connectUrl. Session: $webSocketSession")
                val wasConnected = connectionStatus.value == ConnectionStatus.Connected
                webSocketSession = null
                if (wasConnected) {
                    _connectionStatus.value = ConnectionStatus.Disconnected("Session ended unexpectedly")
                }
                println("QuizClient: Post-connect block. Final status: ${connectionStatus.value}")
            }
        }
    }

    private fun resetClientStateForNewSession() {
        println("QuizClient: resetClientStateForNewSession called.")
        _currentQuiz.value = null
        _submittedAnswers.value = emptyMap()
        _showJoystick.value = false
    }

    private suspend fun DefaultClientWebSocketSession.listenForMessages() {
        try {
            println("QuizClient: Starting to listen for messages from ${call.request.url}...")
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
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
                    }
                    is Frame.Close -> {
                        val reason = frame.readReason()
                        println("QuizClient: Received Close frame: ${reason?.code} ${reason?.message}")
                        _showJoystick.value = false
                        if (connectionStatus.value !is ConnectionStatus.Error && connectionStatus.value !is ConnectionStatus.Disconnected) {
                            _connectionStatus.value = ConnectionStatus.Disconnected("Connection closed by server: ${reason?.message ?: "No reason"}")
                        }
                        return
                    }
                    else -> {
                        println("QuizClient: Received other frame type: ${frame.frameType.name}")
                    }
                }
            }
        } catch (e: ClosedReceiveChannelException) {
            println("QuizClient: WebSocket channel closed by remote: ${e.message}")
            if (connectionStatus.value !is ConnectionStatus.Error && connectionStatus.value !is ConnectionStatus.Disconnected) {
                _connectionStatus.value = ConnectionStatus.Disconnected("Connection closed by server")
            }
        } catch (e: CancellationException) {
            println("QuizClient: Message listening coroutine cancelled: ${e.message}")
        } catch (e: Exception) {
            println("QuizClient: CRITICAL - Error in message listening loop: ${e.message}")
            e.printStackTrace()
            if (connectionStatus.value !is ConnectionStatus.Error && connectionStatus.value !is ConnectionStatus.Disconnected) {
                _connectionStatus.value = ConnectionStatus.Error("Error receiving messages: ${e.message ?: "Unknown error"}")
            }
        } finally {
            println("QuizClient: Stopped listening for messages (listenForMessages finally block).")
        }
    }

    private fun handleWebSocketMessage(message: WebSocketMessage) {
        println("QuizClient: Handling message type '${message.type}' from sender '${message.sender}'.")
        val studentId = currentStudentId

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
                        _connectionStatus.value = ConnectionStatus.Error("Failed to parse quiz data: ${e.message}")
                    }
                } else {
                    println("QuizClient: Received QUIZ message not from professor. Sender: '${message.sender}'. Ignoring.")
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

                    if (finalAnswerState.studentId == studentId) {
                        _submittedAnswers.update { currentAnswers ->
                            val newAnswers = currentAnswers.toMutableMap()
                            newAnswers[finalAnswerState.questionId] = finalAnswerState
                            println("QuizClient: Updated submittedAnswers for questionId '${finalAnswerState.questionId}' with evaluated answer (isCorrect: ${finalAnswerState.isCorrect}).")
                            newAnswers.toMap()
                        }
                    } else {
                        println("QuizClient: Received ANSWER_EVALUATED for another student ('${finalAnswerState.studentId}'). Ignoring for this client ('$studentId').")
                    }
                } catch (e: Exception) {
                    println("QuizClient: CRITICAL - Error processing ANSWER_EVALUATED message: ${e.message}. Content: ${message.content}")
                }
            }
            "SESSION_ENDED" -> {
                println("QuizClient: Received SESSION_ENDED from '${message.sender}'. Content: ${message.content}")
                _currentQuiz.value = null
                _connectionStatus.value = ConnectionStatus.Disconnected("Session ended by server: ${message.content}")
                _showJoystick.value = false
            }
            "ALLOW_JOYSTICK" -> {
                if (message.sender == "professor") {
                    println("QuizClient: ALLOW_JOYSTICK message received. Enabling joystick.")
                    _showJoystick.value = true
                } else {
                    println("QuizClient: ALLOW_JOYSTICK message not from professor. Sender: '${message.sender}'. Ignoring.")
                }
            }
            "DISABLE_JOYSTICK" -> {
                if (message.sender == "professor") {
                    println("QuizClient: DISABLE_JOYSTICK message received. Disabling joystick.")
                    _showJoystick.value = false
                } else {
                    println("QuizClient: DISABLE_JOYSTICK message not from professor. Sender: '${message.sender}'. Ignoring.")
                }
            }
            "ROBOT_STATUS" -> {
                if (message.sender == "server") {
                    println("QuizClient: Received ROBOT_STATUS from server. Attempting to parse. Content: ${message.content}")
                    try {
                        val parsedRobotStatus = json.decodeFromString<RobotStatus>(message.content)
                        _robotStatus.value = parsedRobotStatus
                        println("QuizClient: ROBOT_STATUS parsed successfully. Robot: '${parsedRobotStatus.robotName ?: "N/A"}', Battery: ${parsedRobotStatus.battery_percentage}%.")
                    } catch (e: Exception) {
                        println("QuizClient: CRITICAL - Error parsing ROBOT_STATUS message: ${e.message}. Content: ${message.content}")
                    }
                } else {
                    println("QuizClient: Received ROBOT_STATUS message not from server. Sender: '${message.sender}'. Ignoring.")
                }
            }
            else -> {
                println("QuizClient: Received unknown message type: '${message.type}'. Sender: '${message.sender}'. Content: ${message.content}")
            }
        }
    }

    suspend fun sendAnswer(questionId: String, selectedOptionValue: String) {
        val studentId = currentStudentId
        val studentName = currentStudentName
        val activeQuiz = _currentQuiz.value

        if (studentId.isNullOrBlank() || studentName.isNullOrBlank()) {
            println("QuizClient: Cannot send answer, student details not configured.")
            return
        }
        if (activeQuiz == null) {
            println("QuizClient: Cannot send answer, no active quiz.")
            return
        }
        val session = webSocketSession
        if (session == null || connectionStatus.value != ConnectionStatus.Connected) {
            println("QuizClient: Cannot send answer, not connected. Status: ${connectionStatus.value}")
            _connectionStatus.value = ConnectionStatus.Error("Not connected. Cannot send answer.")
            return
        }

        val answerId = "ans_${studentId}_${questionId}_${Clock.System.now().toEpochMilliseconds()}_${Random.nextInt(10000)}"
        val quizAnswer = QuizAnswer(
            id = answerId,
            quizId = activeQuiz.id,
            questionId = questionId,
            studentId = studentId,
            studentName = studentName,
            answer = selectedOptionValue,
            isCorrect = null
        )

        try {
            val wsMessage = WebSocketMessage(
                type = "ANSWER",
                content = json.encodeToString(QuizAnswer.serializer(), quizAnswer),
                sender = studentId
            )
            val messageJson = json.encodeToString(WebSocketMessage.serializer(), wsMessage)
            println("QuizClient: Sending ANSWER message: $messageJson")
            session.send(Frame.Text(messageJson))

            _submittedAnswers.update { currentAnswers ->
                currentAnswers + (quizAnswer.questionId to quizAnswer.copy(isCorrect = null))
            }
            println("QuizClient: Sent answer for question $questionId (Answer ID: $answerId). Optimistically updated UI.")
        } catch (e: Exception) {
            println("QuizClient: Error sending answer for question $questionId: ${e.message}")
            _connectionStatus.value = ConnectionStatus.Error("Failed to send answer: ${e.message ?: "Unknown error"}")
        }
    }

    suspend fun sendVelocity(linearControl: Float, angularControl: Float) {
        val studentId = currentStudentId
        if (studentId.isNullOrBlank()) {
            println("QuizClient: Cannot send velocity, studentId not configured.")
            return
        }
        val session = webSocketSession
        if (session == null || connectionStatus.value != ConnectionStatus.Connected) {
            println("QuizClient: Cannot send velocity, not connected. Status: ${connectionStatus.value}")
            return
        }

        try {
            val linearVec = Vector3(x = linearControl, y = 0f, z = 0f)
            val angularVec = Vector3(x = 0f, y = 0f, z = angularControl)

            val speedCmd = SpeedCommand(linear = linearVec, angular = angularVec)

            val token = currentStudentId ?: DEFAULT_JOYSTICK_TOKEN
            val velocityMsg = VelocityMessage(joystick_token = token, speed_command = speedCmd)

            val commandId = "vel_cmd_${Clock.System.now().toEpochMilliseconds()}_${Random.nextInt(1000)}"
            val velocityCommand = VelocityCommand(
                op = VELOCITY_COMMAND_OP,
                id = commandId,
                topic = VELOCITY_COMMAND_TOPIC,
                msg = velocityMsg,
                latch = false
            )

            val wsMessage = WebSocketMessage(
                type = "ROBOT_CONTROL_VELOCITY",
                content = json.encodeToString(VelocityCommand.serializer(), velocityCommand),
                sender = studentId
            )
            val messageJson = json.encodeToString(WebSocketMessage.serializer(), wsMessage)

            session.send(Frame.Text(messageJson))

        } catch (e: Exception) {
            println("QuizClient: Error sending velocity: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun disconnect(reason: String = "User initiated disconnect") {
        println("QuizClient: disconnect() called. Reason: $reason. Current status: ${connectionStatus.value}")
        val sessionToClose = webSocketSession
        webSocketSession = null

        if (connectionStatus.value is ConnectionStatus.Disconnected && sessionToClose == null) {
            println("QuizClient: Already disconnected and session is null.")
            _showJoystick.value = false
            return
        }
        _connectionStatus.value = ConnectionStatus.Disconnected(reason)
        _showJoystick.value = false
        try {
            sessionToClose?.close(CloseReason(CloseReason.Codes.NORMAL, reason))
            println("QuizClient: WebSocket session close initiated.")
        } catch (e: Exception) {
            println("QuizClient: Error during WebSocket session close: ${e.message}")
        }
        println("QuizClient: Disconnected. Session is now definitively null.")
    }

    fun cleanup() {
        println("QuizClient Singleton: cleanup() called.")
        clientScope.launch {
            disconnect("Application cleanup")
        }
        clientScope.cancel("QuizClient cleanup initiated by app")
        httpClient?.close()
        httpClient = null
        println("QuizClient Singleton: HTTP client closed. Resources cleaned up.")
    }
}