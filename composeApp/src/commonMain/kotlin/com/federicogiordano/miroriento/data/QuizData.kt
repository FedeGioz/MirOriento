package com.federicogiordano.miroriento.data

import kotlinx.serialization.Serializable

@Serializable
data class Question(
    val id: String,
    val text: String,
    val options: List<String>,
    val correctAnswer: String? = null,
    val correctOptionIndex: Int? = null,
    val points: Int = 1
)

@Serializable
data class Quiz(
    val id: String,
    val title: String,
    val description: String? = null,
    val questions: List<Question>,
    val createdBy: String? = null
)

@Serializable
data class QuizAnswer(
    val id: String,
    val quizId: String,
    val questionId: String,
    val studentId: String,
    val studentName: String,
    val selectedOption: String,
    var isCorrect: Boolean? = null
)

@Serializable
data class StudentQuizResult(
    val studentId: String,
    val studentName: String,
    val quizId: String,
    val score: Int,
    val totalPoints: Int,
    val answers: List<QuizAnswer>
)

@Serializable
data class WebSocketMessage(
    val type: String,
    val content: String,
    val sender: String
)

@Serializable
data class StudentInfo(
    val name: String,
    val city: String,
    val schoolFocus: SchoolFocus
)

@Serializable
enum class SchoolFocus {
    INFORMATICA,
    LOGISTICA,
    ROBOTICA
}

@Serializable
data class StudentConnection(
    val id: String,
    val name: String
)