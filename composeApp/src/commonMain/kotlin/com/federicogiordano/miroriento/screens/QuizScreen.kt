package com.federicogiordano.miroriento.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.federicogiordano.miroriento.api.ConnectionStatus
import com.federicogiordano.miroriento.api.QuizClient
import com.federicogiordano.miroriento.data.Question
import com.federicogiordano.miroriento.data.Quiz
import com.federicogiordano.miroriento.data.QuizAnswer
import kotlinx.coroutines.launch

@Composable
fun QuizScreen(
    serverIp: String,
    studentId: String,
    studentName: String
) {
    println("QuizScreen: Composable entered. Server IP: '$serverIp', Student ID: '$studentId', Student Name: '$studentName'")
    val quizClient = remember(studentId, studentName) {
        println("QuizScreen: Remembering QuizClient for Student ID: '$studentId', Name: '$studentName'")
        QuizClient(studentId = studentId, studentName = studentName)
    }
    val connectionStatus by quizClient.connectionStatus.collectAsState()
    val currentQuiz by quizClient.currentQuiz.collectAsState()
    val submittedAnswers by quizClient.submittedAnswers.collectAsState()

    val currentSelections = remember { mutableStateMapOf<String, String>() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(key1 = serverIp, key2 = studentId, key3 = quizClient) {
        println("QuizScreen: LaunchedEffect for connection triggered. Server IP: '$serverIp', Student ID: '$studentId'")
        if (serverIp.isNotBlank() && studentId.isNotBlank()) {
            println("QuizScreen: serverIp and studentId are valid. Attempting to connect QuizClient to path /connect.")
            quizClient.connect(serverIp = serverIp, path = "/connect")
        } else {
            println("QuizScreen: Skipping QuizClient.connect because serverIp ('$serverIp') or studentId ('$studentId') is blank.")
        }
    }

    DisposableEffect(key1 = quizClient) {
        onDispose {
            println("QuizScreen: Disposing. Cleaning up QuizClient for Student ID: '$studentId'.")
            coroutineScope.launch {
                quizClient.disconnect()
            }
            quizClient.cleanup()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Quiz Mode") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ConnectionStatusView(connectionStatus)
            println("QuizScreen: Current ConnectionStatus: $connectionStatus, CurrentQuiz is null: ${currentQuiz == null}")

            when (val status = connectionStatus) {
                is ConnectionStatus.Connected -> {
                    currentQuiz?.let { quiz ->
                        println("QuizScreen: Quiz data available. Title: ${quiz.title}. Displaying QuizContentView.")
                        QuizContentView(
                            quiz = quiz,
                            submittedAnswers = submittedAnswers.values.toList(),
                            currentSelections = currentSelections,
                            onSelectionChange = { qId, option ->
                                currentSelections[qId] = option
                            },
                            onSubmitAnswer = { qId ->
                                currentSelections[qId]?.let { selectedOption ->
                                    coroutineScope.launch {
                                        quizClient.sendAnswer(qId, selectedOption)
                                    }
                                }
                            }
                        )
                    } ?: Text("Waiting for the professor to send a quiz... (Connected)").also {
                        println("QuizScreen: Connected, but currentQuiz is null.")
                    }
                }
                is ConnectionStatus.Connecting -> Text("Connecting to quiz server... (${status.message})").also {
                    println("QuizScreen: Status is Connecting.")
                }
                is ConnectionStatus.Disconnected -> Text("Disconnected: ${status.reason}").also {
                    println("QuizScreen: Status is Disconnected.")
                }
                is ConnectionStatus.Error -> Text("Connection Error: ${status.message}", color = Color.Red).also {
                    println("QuizScreen: Status is Error.")
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusView(status: ConnectionStatus) {
    val statusText = when (status) {
        is ConnectionStatus.Connected -> "Status: Connected"
        is ConnectionStatus.Connecting -> "Status: Connecting... (${status.message})"
        is ConnectionStatus.Disconnected -> "Status: Disconnected (${status.reason})"
        is ConnectionStatus.Error -> "Status: Error (${status.message})"
    }
    Text(statusText, style = MaterialTheme.typography.caption, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun QuizContentView(
    quiz: Quiz,
    submittedAnswers: List<QuizAnswer>,
    currentSelections: Map<String, String>,
    onSelectionChange: (questionId: String, option: String) -> Unit,
    onSubmitAnswer: (questionId: String) -> Unit
) {
    Text(quiz.title, style = MaterialTheme.typography.h5, modifier = Modifier.padding(bottom = 8.dp))
    quiz.description?.takeIf { it.isNotBlank() }?.let {
        Text(it, style = MaterialTheme.typography.body1, modifier = Modifier.padding(bottom = 16.dp))
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(quiz.questions) { question ->
            QuestionItemView(
                question = question,
                submittedAnswer = submittedAnswers.find { it.questionId == question.id },
                selectedOption = currentSelections[question.id],
                onOptionSelected = { option -> onSelectionChange(question.id, option) },
                onSubmitAnswer = { onSubmitAnswer(question.id) }
            )
            Divider(modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun QuestionItemView(
    question: Question,
    submittedAnswer: QuizAnswer?,
    selectedOption: String?,
    onOptionSelected: (String) -> Unit,
    onSubmitAnswer: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), elevation = 2.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(question.text, style = MaterialTheme.typography.subtitle1)
            Spacer(modifier = Modifier.height(8.dp))

            question.options.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = (selectedOption == option),
                            onClick = { if (submittedAnswer == null) onOptionSelected(option) },
                            enabled = submittedAnswer == null
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (selectedOption == option),
                        onClick = { if (submittedAnswer == null) onOptionSelected(option) },
                        enabled = submittedAnswer == null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(option)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (submittedAnswer == null) {
                Button(
                    onClick = onSubmitAnswer,
                    enabled = selectedOption != null,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Submit Answer")
                }
            } else {
                val resultText = when (submittedAnswer.isCorrect) {
                    true -> "Correct!"
                    false -> "Incorrect."
                    null -> "Answer sent, awaiting evaluation..."
                }
                val resultColor = when (submittedAnswer.isCorrect) {
                    true -> Color.Green.copy(alpha = 0.7f)
                    false -> Color.Red.copy(alpha = 0.7f)
                    null -> MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
                }
                Text(
                    resultText,
                    color = resultColor,
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.align(Alignment.End)
                )
                submittedAnswer.isCorrect?.let {
                    Text(
                        "Your answer: ${submittedAnswer.selectedOption}",
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                    )
                }
            }
        }
    }
}