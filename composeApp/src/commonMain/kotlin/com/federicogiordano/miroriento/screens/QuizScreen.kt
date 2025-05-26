package com.federicogiordano.miroriento.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.federicogiordano.miroriento.data.RobotStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen() {
    val connectionStatus by QuizClient.connectionStatus.collectAsState()
    val currentQuiz by QuizClient.currentQuiz.collectAsState()
    val submittedAnswersMap by QuizClient.submittedAnswers.collectAsState()
    val robotStatus by QuizClient.robotStatus.collectAsState()

    val currentSelections = remember { mutableStateMapOf<String, String>() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(currentQuiz) {
        currentSelections.clear()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quiz Mode") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ConnectionStatusDisplayQuiz(connectionStatus)
            RobotBatteryDisplayQuiz(robotStatus)

            Spacer(modifier = Modifier.height(16.dp))

            when (val status = connectionStatus) {
                is ConnectionStatus.Connected -> {
                    currentQuiz?.let { quiz ->
                        QuizContentView(
                            quiz = quiz,
                            submittedAnswersMap = submittedAnswersMap,
                            currentSelections = currentSelections,
                            onSelectionChange = { qId, option -> currentSelections[qId] = option },
                            onSubmitAnswer = { questionId ->
                                currentSelections[questionId]?.let { selectedOption ->
                                    coroutineScope.launch {
                                        QuizClient.sendAnswer(questionId, selectedOption)
                                    }
                                }
                            }
                        )
                    } ?: Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                        Text("Waiting for the professor to send a quiz...", style = MaterialTheme.typography.titleMedium)
                        Text("(Connected to server)", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator()
                    }
                }
                is ConnectionStatus.Connecting -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                    Text("Connecting to quiz server... (${status.message})", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator()
                }
                is ConnectionStatus.Disconnected -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                    Text("Disconnected from quiz server.", style = MaterialTheme.typography.titleMedium)
                    Text("Reason: ${status.reason}", style = MaterialTheme.typography.bodySmall)
                    Text("Please connect via the Home page.", style = MaterialTheme.typography.bodySmall)
                }
                is ConnectionStatus.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                    Text("Connection Error.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    Text(status.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Text("Please try reconnecting via the Home page.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun QuizContentView(
    quiz: Quiz,
    submittedAnswersMap: Map<String, QuizAnswer>,
    currentSelections: Map<String, String>,
    onSelectionChange: (questionId: String, option: String) -> Unit,
    onSubmitAnswer: (questionId: String) -> Unit
) {
    Column {
        Text(quiz.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp).align(Alignment.CenterHorizontally))
        quiz.description?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 16.dp))
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(quiz.questions, key = { question -> question.id }) { question ->
                QuestionItemView(
                    question = question,
                    submittedAnswer = submittedAnswersMap[question.id],
                    selectedOption = currentSelections[question.id],
                    onOptionSelected = { option -> onSelectionChange(question.id, option) },
                    onSubmitAnswer = { onSubmitAnswer(question.id) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor =
            if (submittedAnswer?.isCorrect == true) Color(0xFFC8E6C9)
            else if (submittedAnswer?.isCorrect == false) Color(0xFFFFCDD2)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(question.text, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            question.options.forEach { option ->
                val isCurrentlySelected = selectedOption == option
                val isThisOptionSubmitted = submittedAnswer?.answer == option

                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = isCurrentlySelected,
                            onClick = { if (submittedAnswer == null) onOptionSelected(option) },
                            enabled = submittedAnswer == null
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isCurrentlySelected,
                        onClick = { if (submittedAnswer == null) onOptionSelected(option) },
                        enabled = submittedAnswer == null,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = if (submittedAnswer != null && isThisOptionSubmitted && submittedAnswer.isCorrect == true) Color(0xFF388E3C)
                            else if (submittedAnswer != null && isThisOptionSubmitted && submittedAnswer.isCorrect == false) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                            unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            disabledSelectedColor = if (submittedAnswer != null && isThisOptionSubmitted && submittedAnswer.isCorrect == true) Color(0xFF388E3C).copy(alpha = 0.5f)
                            else if (submittedAnswer != null && isThisOptionSubmitted && submittedAnswer.isCorrect == false) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            disabledUnselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = option,
                        color = if (submittedAnswer != null && isThisOptionSubmitted && submittedAnswer.isCorrect == true) Color.Black
                        else if (submittedAnswer != null && isThisOptionSubmitted && submittedAnswer.isCorrect == false) MaterialTheme.colorScheme.error
                        else LocalContentColor.current
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (submittedAnswer == null) {
                Button(
                    onClick = onSubmitAnswer,
                    enabled = selectedOption != null,
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Submit Answer") }
            } else {
                val resultText = when (submittedAnswer.isCorrect) {
                    true -> "Correct!"
                    false -> "Incorrect."
                    null -> "Answer Sent. Awaiting Evaluation..."
                }
                val resultColor = when (submittedAnswer.isCorrect) {
                    true -> Color(0xFF388E3C)
                    false -> MaterialTheme.colorScheme.error
                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(resultText, color = resultColor, style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.End))
                Text("Your answer: ${submittedAnswer.answer}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.End).padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun ConnectionStatusDisplayQuiz(status: ConnectionStatus) {
    val (text, color) = when (status) {
        is ConnectionStatus.Connected -> "Status: Connected" to Color(0xFF388E3C)
        is ConnectionStatus.Connecting -> "Status: Connecting..." to MaterialTheme.colorScheme.onSurface
        is ConnectionStatus.Disconnected -> "Status: Disconnected (${status.reason})" to MaterialTheme.colorScheme.onSurfaceVariant
        is ConnectionStatus.Error -> "Status: Error (${status.message})" to MaterialTheme.colorScheme.error
    }
    Text(text, color = color, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun RobotBatteryDisplayQuiz(robotStatus: RobotStatus?) {
    val text = robotStatus?.let { "Robot: ${it.battery_percentage.toInt()}%" } ?: "Robot: N/A"
    Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 8.dp))
}