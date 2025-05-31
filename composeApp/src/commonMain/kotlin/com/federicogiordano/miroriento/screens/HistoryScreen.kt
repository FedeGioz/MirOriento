package com.federicogiordano.miroriento.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.federicogiordano.miroriento.data.PersistedQuizAnswer
import com.federicogiordano.miroriento.data.VisitRecord
import com.federicogiordano.miroriento.viewmodels.StudentViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(studentViewModel: StudentViewModel) {
    val studentId = studentViewModel.getStudent()?.id

    LaunchedEffect(studentId) {
        if (!studentId.isNullOrBlank()) {
            println("HistoryScreen: LaunchedEffect - Richiesta ricarica sessione studente per ID: $studentId")
            studentViewModel.loadStudentSession(studentId)
        }
    }

    val studentPersistedData by studentViewModel.studentPersistedData.collectAsState()
    val lastVisit = studentPersistedData?.visits?.filter { it.studentId == studentId }?.maxByOrNull { it.visitDate }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storico Ultima Visita") },
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
                .padding(16.dp)
        ) {
            if (studentId == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nessun studente attivo.", style = MaterialTheme.typography.headlineSmall)
                }
            } else if (lastVisit == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nessuno storico visite trovato per ${studentViewModel.getStudent()?.name ?: "questo studente"}.", style = MaterialTheme.typography.headlineSmall)
                }
            } else {
                VisitDetailsView(visitRecord = lastVisit)
            }
        }
    }
}

@Composable
private fun VisitDetailsView(visitRecord: VisitRecord) {
    val uriHandler = LocalUriHandler.current
    val brochureLink = "https://orientamento.itiscuneo.edu.it/"

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(
                text = "Data Visita: ${visitRecord.visitDate}",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Button(
                onClick = {
                    try {
                        uriHandler.openUri(brochureLink)
                    } catch (e: Exception) {
                        println("HistoryScreen: Impossibile aprire il link della brochure - $e")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text("Brochure Digitale")
            }
        }

        if (visitRecord.answers.isEmpty()) {
            item {
                Text("Nessuna risposta registrata per questa visita.")
            }
        } else {
            items(visitRecord.answers, key = { it.id }) { answer ->
                AnswerItemView(answer = answer)
            }
        }
    }
}

@Composable
private fun AnswerItemView(answer: PersistedQuizAnswer) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (answer.isCorrect) {
                true -> Color(0xFFC8E6C9)
                false -> Color(0xFFFFCDD2)
                null -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Domanda: ${answer.questionText}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text("Opzioni:", style = MaterialTheme.typography.labelMedium)
            answer.options.forEach { option ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    RadioButton(
                        selected = (option == answer.answer),
                        onClick = null,
                        modifier = Modifier.size(20.dp),
                        colors = RadioButtonDefaults.colors(
                            selectedColor = if (option == answer.answer) {
                                when (answer.isCorrect) {
                                    true -> MaterialTheme.colorScheme.primary
                                    false -> MaterialTheme.colorScheme.error
                                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            } else if (option == answer.correctAnswerText && answer.isCorrect == false) {
                                Color(0xFF388E3C)
                            }
                            else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            },
                            unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = option,
                        color = if (option == answer.answer) {
                            when (answer.isCorrect) {
                                true -> MaterialTheme.colorScheme.primary
                                false -> MaterialTheme.colorScheme.error
                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        } else if (option == answer.correctAnswerText && answer.isCorrect == false) {
                            Color(0xFF388E3C)
                        }
                        else {
                            LocalContentColor.current
                        },
                        fontWeight = if (option == answer.answer) FontWeight.SemiBold else FontWeight.Normal,
                        textDecoration = if (option == answer.correctAnswerText && answer.isCorrect == false && option != answer.answer) {
                            androidx.compose.ui.text.style.TextDecoration.Underline
                        } else null
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            Text("La tua risposta: ${answer.answer}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)

            if (answer.isCorrect == false && answer.correctAnswerText != null && answer.answer != answer.correctAnswerText) {
                Text("Risposta corretta: ${answer.correctAnswerText}", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF388E3C))
            }
            Spacer(modifier = Modifier.height(4.dp))

            val resultText = when (answer.isCorrect) {
                true -> "Corretta"
                false -> "Sbagliata"
                null -> "Non valutata"
            }
            val resultColor = when (answer.isCorrect) {
                true -> Color(0xFF388E3C)
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                text = "Esito: $resultText",
                style = MaterialTheme.typography.bodyMedium,
                color = resultColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}