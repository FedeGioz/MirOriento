package com.federicogiordano.miroriento.data

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json

object PersistenceService {
    private const val STUDENT_DATA_FILE_PREFIX = "student_session_data_"
    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun getStudentDataFileName(studentId: String): String {
        val safeStudentId = studentId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return "$STUDENT_DATA_FILE_PREFIX$safeStudentId.json"
    }

    private fun getCurrentDateString(): String {
        val now = Clock.System.now()
        val date = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
        return "${date.year}-${date.monthNumber.toString().padStart(2, '0')}-${date.dayOfMonth.toString().padStart(2, '0')}"
    }

    fun loadStudentData(studentId: String): StudentPersistedData? {
        if (studentId.isBlank()) {
            println("PersistenceService: Cannot load data for blank studentId.")
            return null
        }
        val fileName = getStudentDataFileName(studentId)
        val jsonData = FileSystem.readTextFromFile(fileName)
        return jsonData?.let {
            try {
                json.decodeFromString<StudentPersistedData>(it)
            } catch (e: Exception) {
                println("PersistenceService: Error decoding student data for '$studentId' from file '$fileName': ${e.message}")
                null
            }
        }
    }

    private fun saveStudentData(data: StudentPersistedData): Boolean {
        val fileName = getStudentDataFileName(data.studentInfo.id)
        return try {
            val jsonData = json.encodeToString(data)
            val success = FileSystem.writeTextToFile(fileName, jsonData)
            if (success) {
                println("PersistenceService: Successfully saved data for student '${data.studentInfo.id}' to '$fileName'.")
            } else {
                println("PersistenceService: Failed to write data for student '${data.studentInfo.id}' to '$fileName'.")
            }
            success
        } catch (e: Exception) {
            println("PersistenceService: Error encoding student data for '${data.studentInfo.id}': ${e.message}")
            false
        }
    }

    fun ensureVisitRecordForToday(studentInfo: StudentInfo, currentQuizTitle: String?): StudentPersistedData {
        val studentId = studentInfo.id
        val todayDateStr = getCurrentDateString()
        var currentData = loadStudentData(studentId)

        if (currentData == null) {
            currentData = StudentPersistedData(
                studentInfo = studentInfo,
                registrationTimestamp = Clock.System.now().toEpochMilliseconds(),
                visits = mutableListOf()
            )
        }

        val visitIndex = currentData.visits.indexOfFirst { it.visitDate == todayDateStr && it.studentId == studentId }

        if (visitIndex == -1) {
            val newVisit = VisitRecord(studentId = studentId, visitDate = todayDateStr, quizTitle = currentQuizTitle)
            currentData.visits.add(newVisit)
            println("PersistenceService: Created new visit record for student '$studentId' for date '$todayDateStr' with quiz title '$currentQuizTitle'.")
        } else {
            val existingVisit = currentData.visits[visitIndex]
            if (existingVisit.quizTitle == null || (currentQuizTitle != null && existingVisit.quizTitle != currentQuizTitle)) {
                val updatedVisit = existingVisit.copy(quizTitle = currentQuizTitle)
                currentData.visits[visitIndex] = updatedVisit
                println("PersistenceService: Updated quiz title for student '$studentId' for date '$todayDateStr' to '$currentQuizTitle'.")
            }
            println("PersistenceService: Found existing visit record for student '$studentId' for date '$todayDateStr'.")
        }
        saveStudentData(currentData)
        return currentData
    }

    fun addAnswerToTodaysVisit(studentId: String, question: Question, studentAnswer: QuizAnswer, quizTitle: String?): StudentPersistedData? {
        if (studentId.isBlank()) {
            println("PersistenceService: Cannot add answer for blank studentId.")
            return null
        }
        val studentData = loadStudentData(studentId)
        if (studentData == null) {
            println("PersistenceService: No student data found for $studentId when trying to add answer. Ensure ensureVisitRecordForToday was called.")
            return null
        }

        val todayDateStr = getCurrentDateString()
        var todaysVisit = studentData.visits.find { it.visitDate == todayDateStr && it.studentId == studentId }

        if (todaysVisit == null) {
            println("PersistenceService: No visit record for today ('$todayDateStr') for student '$studentId'. Creating one now to add answer.")
            todaysVisit = VisitRecord(studentId = studentId, visitDate = todayDateStr, quizTitle = quizTitle)
            studentData.visits.add(todaysVisit)
        } else if (todaysVisit.quizTitle == null && quizTitle != null) {
            val visitIndex = studentData.visits.indexOfFirst { it.visitDate == todayDateStr && it.studentId == studentId }
            if (visitIndex != -1) {
                studentData.visits[visitIndex] = todaysVisit.copy(quizTitle = quizTitle)
                todaysVisit = studentData.visits[visitIndex]
            }
        }

        val persistedAnswer = PersistedQuizAnswer(
            id = studentAnswer.id,
            quizId = studentAnswer.quizId,
            questionId = studentAnswer.questionId,
            questionText = question.text,
            studentId = studentAnswer.studentId,
            studentName = studentAnswer.studentName,
            answer = studentAnswer.answer,
            options = question.options,
            correctAnswerText = question.correctAnswer,
            isCorrect = studentAnswer.isCorrect
        )

        todaysVisit.answers.removeAll { it.id == persistedAnswer.id }
        todaysVisit.answers.add(persistedAnswer)

        if (saveStudentData(studentData)) {
            println("PersistenceService: Added/Updated answer with ID '${persistedAnswer.id}' to visit of '$todayDateStr' for student '$studentId'.")
            return studentData
        }
        return null
    }

    fun updateAnswerInTodaysVisit(studentId: String, evaluatedLiveAnswer: QuizAnswer): StudentPersistedData? {
        if (studentId.isBlank()) {
            println("PersistenceService: Cannot update answer for blank studentId.")
            return null
        }
        val studentData = loadStudentData(studentId)
        if (studentData == null) {
            println("PersistenceService: No data for student '$studentId'. Cannot update answer.")
            return null
        }

        val todayDateStr = getCurrentDateString()
        val visitToUpdate = studentData.visits.find { it.visitDate == todayDateStr && it.studentId == studentId }

        if (visitToUpdate == null) {
            println("PersistenceService: No visit record for today ('$todayDateStr') found for student '$studentId' to update answer with QID '${evaluatedLiveAnswer.questionId}'.")
            return null
        }

        val answerIndex = visitToUpdate.answers.indexOfFirst { it.questionId == evaluatedLiveAnswer.questionId }
        if (answerIndex != -1) {
            val existingPersistedAnswer = visitToUpdate.answers[answerIndex]
            visitToUpdate.answers[answerIndex] = existingPersistedAnswer.copy(
                isCorrect = evaluatedLiveAnswer.isCorrect,
            )
            if (saveStudentData(studentData)) {
                println("PersistenceService: Updated correctness of answer for QID '${evaluatedLiveAnswer.questionId}' in visit of '$todayDateStr' for student '$studentId'.")
                return studentData
            }
        } else {
            println("PersistenceService: Original persisted answer with QID '${evaluatedLiveAnswer.questionId}' not found for student '$studentId' in visit of '$todayDateStr' to update.")
        }
        return null
    }
}