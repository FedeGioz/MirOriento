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
    val name: String,
    val hasJoystickPermission: Boolean = false
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
data class PersistedQuizAnswer(
    val id: String,
    val quizId: String,
    val questionId: String,
    val questionText: String,
    val studentId: String,
    val studentName: String,
    val answer: String,
    val options: List<String>,
    val correctAnswerText: String?,
    var isCorrect: Boolean? = null
)

@Serializable
data class VisitRecord(
    val studentId: String,
    val visitDate: String,
    val quizTitle: String?,
    val answers: MutableList<PersistedQuizAnswer> = mutableListOf()
)

@Serializable
data class StudentPersistedData(
    val studentInfo: StudentInfo,
    val registrationTimestamp: Long,
    val visits: MutableList<VisitRecord> = mutableListOf()
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
    @SerialName("mode_id") val modeId: Int = -1,
    @SerialName("mission_queue_id") val missionQueueId: String? = null,
    @SerialName("robot_name") val robotName: String = "",
    @SerialName("uptime") val uptime: Long = -1,
    @SerialName("errors") val errors: List<String> = emptyList(),
    @SerialName("battery_percentage") val battery_percentage: Float = 0f,
    @SerialName("map_id") val mapId: String = "",
    @SerialName("mission_text") val missionText: String = "",
    @SerialName("state_id") val stateId: Int = -1,
    @SerialName("state_text") val stateText: String = "",
    @SerialName("velocity") val velocity: Velocity = Velocity(),
    @SerialName("robot_model") val robotModel: String = "",
    @SerialName("mode_text") val modeText: String = "",
    @SerialName("battery_time_remaining") val batteryTimeRemaining: Long = -1,
    @SerialName("position") val position: Position = Position()
)

@Serializable
data class Velocity(
    @SerialName("linear") val linear: Float? = null,
    @SerialName("angular") val angular: Float? = null
)

@Serializable
data class Position(
    @SerialName("x") val x: Float? = null,
    @SerialName("y") val y: Float? = null,
    @SerialName("orientation") val orientation: Float? = null
)

@Serializable
data class VelocityMessage(
    val joystick_token: String,
    val speed_command: SpeedCommand
)

@Serializable
data class SpeedCommand(
    val linear: Vector3,
    val angular: Vector3
)

@Serializable
data class Vector3(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f
)

@Serializable
data class VelocityCommand(
    @SerialName("op") val op: String,
    @SerialName("id") val id: String,
    @SerialName("topic") val topic: String,
    @SerialName("msg") val msg: VelocityMessage,
    @SerialName("latch") val latch: Boolean = false
)