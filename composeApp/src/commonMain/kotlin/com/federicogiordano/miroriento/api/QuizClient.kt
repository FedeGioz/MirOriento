package com.federicogiordano.miroriento.api

import com.federicogiordano.miroriento.data.PersistenceService
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
    data object Connected : ConnectionStatus()
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
        ConnectionStatus.Disconnected("In attesa del tentativo di connessione")
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

    private val _currentMapBase64 = MutableStateFlow<String?>(null)
    val currentMapBase64: StateFlow<String?> = _currentMapBase64.asStateFlow()

    private const val VELOCITY_COMMAND_OP = "publish"
    private const val VELOCITY_COMMAND_TOPIC = "/cmd_vel_joystick"
    private const val DEFAULT_JOYSTICK_TOKEN = "miroriento_joystick_user"

    init {
        println("QuizClient Singleton: Inizializzato.")
        initializeHttpClient()
    }

    fun configure(studentId: String, studentName: String) {
        this.currentStudentId = studentId
        this.currentStudentName = studentName
        println("QuizClient: Configurato con ID Studente: '$currentStudentId', Nome: '$currentStudentName'")

        _showJoystick.value = false
        _currentQuiz.value = null
        _submittedAnswers.value = emptyMap()
        _robotStatus.value = null
        _currentMapBase64.value = null
    }

    private fun initializeHttpClient() {
        if (httpClient == null) {
            println("QuizClient: Inizializzazione di HttpClient.")
            httpClient = HttpClient(CIO) {
                install(WebSockets)
            }
        }
    }

    fun connect(serverIp: String, serverPort: Int = 8080, path: String) {
        val studentId = currentStudentId
        val studentName = currentStudentName

        if (studentId.isNullOrBlank() || studentName.isNullOrBlank()) {
            _connectionStatus.value = ConnectionStatus.Error("Dettagli studente non configurati. Chiamare prima QuizClient.configure().")
            println("QuizClient: Connessione fallita - dettagli studente non configurati.")
            return
        }

        println("QuizClient: connect() chiamato. Server: $serverIp:$serverPort$path, Studente: $studentId ($studentName)")
        if (connectionStatus.value == ConnectionStatus.Connected || connectionStatus.value is ConnectionStatus.Connecting) {
            println("QuizClient: Già connesso o tentativo di connessione. Stato attuale: ${connectionStatus.value}")
            return
        }

        initializeHttpClient()
        val connectUrl = "ws://$serverIp:$serverPort$path"
        _connectionStatus.value = ConnectionStatus.Connecting("Tentativo di connessione a $connectUrl")
        resetClientStateForNewSession()

        clientScope.launch {
            try {
                httpClient!!.webSocket(
                    method = HttpMethod.Get, host = serverIp, port = serverPort, path = path
                ) {
                    webSocketSession = this
                    println("QuizClient: Sessione WebSocket stabilita con $connectUrl.")
                    val studentConnectionInfo = StudentConnection(studentId, studentName)
                    val initialMessageJson = json.encodeToString(StudentConnection.serializer(), studentConnectionInfo)
                    send(Frame.Text(initialMessageJson))
                    println("QuizClient: Inviato messaggio iniziale StudentConnection: $initialMessageJson")
                    _connectionStatus.value = ConnectionStatus.Connected
                    println("QuizClient: Stato impostato su Connesso. Inizio ad ascoltare i messaggi.")
                    listenForMessages()
                }
            } catch (e: CancellationException) {
                println("QuizClient: Coroutine di connessione annullata: ${e.message}")
                if (connectionStatus.value !is ConnectionStatus.Disconnected) {
                    _connectionStatus.value = ConnectionStatus.Disconnected("Connessione annullata dal client")
                }
            } catch (e: Exception) {
                println("QuizClient: CRITICO - Impossibile connettersi o errore durante la sessione con $connectUrl: ${e.message}")
                e.printStackTrace()
                _connectionStatus.value = ConnectionStatus.Error("Connessione fallita: ${e.message ?: "Errore sconosciuto"}")
            } finally {
                println("QuizClient: Blocco WebSocket in connect() terminato per $connectUrl. Sessione: $webSocketSession")
                val wasConnected = connectionStatus.value == ConnectionStatus.Connected
                val wasConnecting = connectionStatus.value is ConnectionStatus.Connecting
                webSocketSession = null
                if (wasConnected || wasConnecting) {
                    if (_connectionStatus.value !is ConnectionStatus.Error) {
                        _connectionStatus.value = ConnectionStatus.Disconnected("Sessione terminata")
                    }
                }
                _currentMapBase64.value = null
                println("QuizClient: Post-blocco di connessione. Stato finale: ${connectionStatus.value}")
            }
        }
    }

    private fun resetClientStateForNewSession() {
        println("QuizClient: resetClientStateForNewSession chiamato.")
        _currentQuiz.value = null
        _submittedAnswers.value = emptyMap()
        _showJoystick.value = false
        _currentMapBase64.value = null
        _robotStatus.value = null
    }

    private suspend fun DefaultClientWebSocketSession.listenForMessages() {
        try {
            println("QuizClient: Inizio ad ascoltare i messaggi da ${call.request.url}...")
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        println("QuizClient: Messaggio raw ricevuto: $text")
                        try {
                            val webSocketMessage = json.decodeFromString<WebSocketMessage>(text)
                            handleWebSocketMessage(webSocketMessage)
                        } catch (e: SerializationException) {
                            println("QuizClient: CRITICO - Errore durante la deserializzazione di WebSocketMessage: ${e.message}. Testo raw: $text")
                        } catch (e: Exception) {
                            println("QuizClient: CRITICO - Errore inaspettato durante l'elaborazione del contenuto del frame: ${e.message}. Testo raw: $text")
                        }
                    }
                    is Frame.Close -> {
                        val reason = frame.readReason()
                        println("QuizClient: Ricevuto frame di chiusura: ${reason?.code} ${reason?.message}")
                        _showJoystick.value = false
                        _currentMapBase64.value = null
                        if (connectionStatus.value !is ConnectionStatus.Error && connectionStatus.value !is ConnectionStatus.Disconnected) {
                            _connectionStatus.value = ConnectionStatus.Disconnected("Connessione chiusa dal server: ${reason?.message ?: "Nessun motivo"}")
                        }
                        return
                    }
                    else -> {
                        println("QuizClient: Ricevuto altro tipo di frame: ${frame.frameType.name}")
                    }
                }
            }
        } catch (e: ClosedReceiveChannelException) {
            println("QuizClient: Canale WebSocket chiuso dal remoto: ${e.message}")
            if (connectionStatus.value !is ConnectionStatus.Error && connectionStatus.value !is ConnectionStatus.Disconnected) {
                _connectionStatus.value = ConnectionStatus.Disconnected("Connessione chiusa dal server")
            }
        } catch (e: CancellationException) {
            println("QuizClient: Coroutine di ascolto messaggi annullata: ${e.message}")
        } catch (e: Exception) {
            println("QuizClient: CRITICO - Errore nel loop di ascolto messaggi: ${e.message}")
            e.printStackTrace()
            if (connectionStatus.value !is ConnectionStatus.Error && connectionStatus.value !is ConnectionStatus.Disconnected) {
                _connectionStatus.value = ConnectionStatus.Error("Errore nella ricezione dei messaggi: ${e.message ?: "Errore sconosciuto"}")
            }
        } finally {
            println("QuizClient: Interrotto l'ascolto dei messaggi (blocco finally di listenForMessages).")
            if (connectionStatus.value == ConnectionStatus.Connected) {
                _connectionStatus.value = ConnectionStatus.Disconnected("Listener di messaggi interrotto inaspettatamente")
            }
            _currentMapBase64.value = null
        }
    }

    private fun handleWebSocketMessage(message: WebSocketMessage) {
        println("QuizClient: Gestione messaggio di tipo '${message.type}' dal mittente '${message.sender}'.")
        val activeStudentId = currentStudentId

        when (message.type) {
            "QUIZ" -> {
                if (message.sender == "professor") {
                    try {
                        val quiz = json.decodeFromString<Quiz>(message.content)
                        _currentQuiz.value = quiz
                        _submittedAnswers.value = emptyMap()
                        println("QuizClient: Quiz '${quiz.title}' (ID: ${quiz.id}) ricevuto e analizzato con successo. ${quiz.questions.size} domande.")
                    } catch (e: Exception) {
                        println("QuizClient: CRITICO - Errore durante l'analisi del messaggio QUIZ: ${e.message}. Contenuto: ${message.content}")
                        _connectionStatus.value = ConnectionStatus.Error("Impossibile analizzare i dati del quiz: ${e.message}")
                    }
                } else {
                    println("QuizClient: Ricevuto messaggio QUIZ non dal professore. Mittente: '${message.sender}'. Ignorato.")
                }
            }
            "ANSWER_RECEIVED" -> {
                println("QuizClient: Ricevuta conferma 'ANSWER_RECEIVED' dal server. Contenuto: ${message.content}")
            }
            "ANSWER_EVALUATED" -> {
                println("QuizClient: Tentativo di elaborare ANSWER_EVALUATED. Contenuto: ${message.content}")
                try {
                    val evaluatedLiveAnswer = json.decodeFromString<QuizAnswer>(message.content)

                    val finalAnswerState = if (evaluatedLiveAnswer.isCorrect == null) {
                        println("QuizClient: 'isCorrect' è nullo nella risposta valutata per QID '${evaluatedLiveAnswer.questionId}'. Interpretato come 'false'.")
                        evaluatedLiveAnswer.copy(isCorrect = false)
                    } else {
                        evaluatedLiveAnswer
                    }

                    if (finalAnswerState.studentId == activeStudentId && activeStudentId != null) {
                        _submittedAnswers.update { currentAnswers ->
                            currentAnswers + (finalAnswerState.questionId to finalAnswerState)
                        }
                        PersistenceService.updateAnswerInTodaysVisit(activeStudentId, finalAnswerState)
                        println("QuizClient: UI aggiornata e risposta valutata persistita per QID '${finalAnswerState.questionId}'.")
                    } else {
                        println("QuizClient: Ricevuto ANSWER_EVALUATED per lo studente '${finalAnswerState.studentId}', ma il client attuale è '$activeStudentId'. Ignorato per la persistenza/aggiornamento UI qui.")
                    }
                } catch (e: Exception) {
                    println("QuizClient: CRITICO - Errore durante l'elaborazione del messaggio ANSWER_EVALUATED: ${e.message}. Contenuto: ${message.content}")
                }
            }
            "SESSION_ENDED" -> {
                println("QuizClient: Ricevuto SESSION_ENDED da '${message.sender}'. Contenuto: ${message.content}")
                _currentQuiz.value = null
                _connectionStatus.value = ConnectionStatus.Disconnected("Sessione terminata dal server: ${message.content}")
                _showJoystick.value = false
                _currentMapBase64.value = null
            }
            "ALLOW_JOYSTICK" -> {
                if (message.sender == "professor") {
                    println("QuizClient: Messaggio ALLOW_JOYSTICK ricevuto. Abilitazione joystick.")
                    _showJoystick.value = true
                } else {
                    println("QuizClient: Messaggio ALLOW_JOYSTICK non dal professore. Mittente: '${message.sender}'. Ignorato.")
                }
            }
            "DISABLE_JOYSTICK" -> {
                if (message.sender == "professor") {
                    println("QuizClient: Messaggio DISABLE_JOYSTICK ricevuto. Disabilitazione joystick.")
                    _showJoystick.value = false
                } else {
                    println("QuizClient: Messaggio DISABLE_JOYSTICK non dal professore. Mittente: '${message.sender}'. Ignorato.")
                }
            }
            "ROBOT_STATUS" -> {
                if (message.sender == "server" || message.sender == "professor") {
                    println("QuizClient: Ricevuto ROBOT_STATUS da '${message.sender}'. Tentativo di analisi. Contenuto: ${message.content}")
                    try {
                        val parsedRobotStatus = json.decodeFromString<RobotStatus>(message.content)
                        _robotStatus.value = parsedRobotStatus
                        println("QuizClient: ROBOT_STATUS analizzato con successo. Robot: '${parsedRobotStatus.robotName}', Batteria: ${parsedRobotStatus.battery_percentage}%.")
                    } catch (e: Exception) {
                        println("QuizClient: CRITICO - Errore durante l'analisi del messaggio ROBOT_STATUS: ${e.message}. Contenuto: ${message.content}")
                    }
                } else {
                    println("QuizClient: Ricevuto messaggio ROBOT_STATUS non dal server o dal professore. Mittente: '${message.sender}'. Ignorato.")
                }
            }
            "MAP_UPDATE" -> {
                if (message.sender == "server" || message.sender == "professor") {
                    println("QuizClient: Messaggio MAP_UPDATE ricevuto da '${message.sender}'. Lunghezza contenuto: ${message.content.length}")
                    _currentMapBase64.value = message.content
                } else {
                    println("QuizClient: Ricevuto messaggio MAP_UPDATE non da un mittente riconosciuto ('${message.sender}'). Ignorato.")
                }
            }
            else -> {
                println("QuizClient: Ricevuto tipo di messaggio sconosciuto: '${message.type}'. Mittente: '${message.sender}'. Contenuto: ${message.content}")
            }
        }
    }

    suspend fun sendAnswer(questionId: String, selectedOptionValue: String) {
        val studentId = currentStudentId
        val studentName = currentStudentName
        val activeQuiz = _currentQuiz.value

        if (studentId.isNullOrBlank() || studentName.isNullOrBlank() || activeQuiz == null) {
            println("QuizClient: Impossibile inviare la risposta, dettagli studente non configurati o nessun quiz attivo.")
            return
        }
        val session = webSocketSession
        if (session == null || connectionStatus.value != ConnectionStatus.Connected) {
            println("QuizClient: Impossibile inviare la risposta, non connesso. Stato: ${connectionStatus.value}")
            return
        }

        val questionObject = activeQuiz.questions.find { it.id == questionId }
        if (questionObject == null) {
            println("QuizClient: Domanda con ID $questionId non trovata nel quiz attuale. Impossibile persistere i dettagli completi della risposta.")
            return
        }

        val answerId = "ans_${studentId}_${questionId}_${Random.nextInt(1000000)}"
        val liveQuizAnswer = QuizAnswer(
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
                content = json.encodeToString(QuizAnswer.serializer(), liveQuizAnswer),
                sender = studentId
            )
            val messageJson = json.encodeToString(WebSocketMessage.serializer(), wsMessage)
            println("QuizClient: Invio messaggio ANSWER: $messageJson")
            session.send(Frame.Text(messageJson))

            PersistenceService.addAnswerToTodaysVisit(studentId, questionObject, liveQuizAnswer, activeQuiz.title)

            _submittedAnswers.update { currentAnswers ->
                currentAnswers + (liveQuizAnswer.questionId to liveQuizAnswer.copy(isCorrect = null))
            }
            println("QuizClient: Risposta inviata e persistita per la domanda $questionId (ID risposta: $answerId).")
        } catch (e: Exception) {
            println("QuizClient: Errore durante l'invio/persistenza della risposta per la domanda $questionId: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun sendVelocity(linearControl: Float, angularControl: Float) {
        val studentId = currentStudentId
        if (studentId.isNullOrBlank()) {
            println("QuizClient: Impossibile inviare la velocità, studentId non configurato.")
            return
        }
        val session = webSocketSession
        if (session == null || connectionStatus.value != ConnectionStatus.Connected) {
            println("QuizClient: Impossibile inviare la velocità, non connesso. Stato: ${connectionStatus.value}")
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
            println("QuizClient: Errore durante l'invio della velocità: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun disconnect(reason: String = "Disconnessione avviata dall'utente") {
        println("QuizClient: disconnect() chiamato. Motivo: $reason. Stato attuale: ${connectionStatus.value}")
        val sessionToClose = webSocketSession
        webSocketSession = null

        _connectionStatus.value = ConnectionStatus.Disconnected(reason)

        _currentQuiz.value = null
        _submittedAnswers.value = emptyMap()
        _showJoystick.value = false
        _currentMapBase64.value = null
        _robotStatus.value = null

        try {
            sessionToClose?.close(CloseReason(CloseReason.Codes.NORMAL, reason))
            println("QuizClient: Inizializzata la chiusura della sessione WebSocket per $sessionToClose.")
        } catch (e: Exception) {
            println("QuizClient: Errore durante la chiusura della sessione WebSocket: ${e.message}")
        }
        println("QuizClient: Disconnesso. La sessione è ora definitivamente null. Stato: ${_connectionStatus.value}")
    }


    fun cleanup() {
        println("QuizClient Singleton: cleanup() chiamato.")
        clientScope.launch {
            disconnect("Pulizia applicazione")
        }
        httpClient?.close()
        httpClient = null
        println("QuizClient Singleton: client HTTP chiuso. Risorse pulite.")
    }
}