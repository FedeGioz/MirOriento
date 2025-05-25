package com.federicogiordano.miroriento.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SchoolFocus {
    INFORMATICA,
    LOGISTICA,
    ROBOTICA
}

@Serializable
data class StudentInfo(
    val id: String,
    val name: String,
    val city: String,
    val schoolFocus: SchoolFocus
)

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

@Serializable
data class Quiz(
    val id: String,
    val title: String,
    val description: String? = null,
    val questions: List<Question>,
    val createdBy: String? = null
)

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
data class QuizAnswer(
    val id: String,
    val quizId: String,
    val questionId: String,
    val studentId: String,
    val studentName: String,
    val answer: String,
    var isCorrect: Boolean? = null
)

@Serializable
data class RobotPosition(
    @SerialName("x") val x: Float? = null,
    @SerialName("y") val y: Float? = null,
    @SerialName("orientation") val orientation: Float? = null
)

@Serializable
data class RobotVelocity(
    @SerialName("linear") val linear: Float? = null,
    @SerialName("angular") val angular: Float? = null
)

@Serializable
data class RobotStatus(
    @SerialName("mode_id") val modeId: Int,
    @SerialName("mission_queue_id") val missionQueueId: String? = null,
    @SerialName("robot_name") val robotName: String?,
    @SerialName("uptime") val uptime: Long,
    @SerialName("errors") val errors: List<String>,
    @SerialName("batteryPercentage") val batteryPercentage: Float,
    @SerialName("mapId") val mapId: String?,
    @SerialName("mission_text") val missionText: String?,
    @SerialName("state_id") val stateId: Int,
    @SerialName("stateText") val stateText: String?,
    @SerialName("velocity") val velocity: RobotVelocity,
    @SerialName("robot_model") val robotModel: String?,
    @SerialName("mode_text") val modeText: String?,
    @SerialName("batteryTimeRemaining") val batteryTimeRemaining: Long?,
    @SerialName("position") val position: RobotPosition
)