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
            println("PersistenceService: Impossibile caricare i dati per un studentId vuoto.")
            return null
        }
        val fileName = getStudentDataFileName(studentId)
        val jsonData = FileSystem.readTextFromFile(fileName)
        return jsonData?.let {
            try {
                json.decodeFromString<StudentPersistedData>(it)
            } catch (e: Exception) {
                println("PersistenceService: Errore durante la decodifica dei dati dello studente per '$studentId' dal file '$fileName': ${e.message}")
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
                println("PersistenceService: Dati salvati con successo per lo studente '${data.studentInfo.id}' in '$fileName'.")
            } else {
                println("PersistenceService: Impossibile scrivere i dati per lo studente '${data.studentInfo.id}' in '$fileName'.")
            }
            success
        } catch (e: Exception) {
            println("PersistenceService: Errore durante la codifica dei dati dello studente per '${data.studentInfo.id}': ${e.message}")
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
            println("PersistenceService: Creato nuovo record di visita per lo studente '$studentId' per la data '$todayDateStr' con titolo del quiz '$currentQuizTitle'.")
        } else {
            val existingVisit = currentData.visits[visitIndex]
            if (existingVisit.quizTitle == null || (currentQuizTitle != null && existingVisit.quizTitle != currentQuizTitle)) {
                val updatedVisit = existingVisit.copy(quizTitle = currentQuizTitle)
                currentData.visits[visitIndex] = updatedVisit
                println("PersistenceService: Aggiornato il titolo del quiz per lo studente '$studentId' per la data '$todayDateStr' a '$currentQuizTitle'.")
            }
            println("PersistenceService: Trovato record di visita esistente per lo studente '$studentId' per la data '$todayDateStr'.")
        }
        saveStudentData(currentData)
        return currentData
    }

    fun addAnswerToTodaysVisit(studentId: String, question: Question, studentAnswer: QuizAnswer, quizTitle: String?): StudentPersistedData? {
        if (studentId.isBlank()) {
            println("PersistenceService: Impossibile aggiungere la risposta per un studentId vuoto.")
            return null
        }
        val studentData = loadStudentData(studentId)
        if (studentData == null) {
            println("PersistenceService: Nessun dato studente trovato per $studentId durante il tentativo di aggiungere la risposta. Assicurarsi che ensureVisitRecordForToday sia stato chiamato.")
            return null
        }

        val todayDateStr = getCurrentDateString()
        var todaysVisit = studentData.visits.find { it.visitDate == todayDateStr && it.studentId == studentId }

        if (todaysVisit == null) {
            println("PersistenceService: Nessun record di visita per oggi ('$todayDateStr') per lo studente '$studentId'. Creazione di uno ora per aggiungere la risposta.")
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
            println("PersistenceService: Risposta con ID '${persistedAnswer.id}' aggiunta/aggiornata alla visita del '$todayDateStr' per lo studente '$studentId'.")
            return studentData
        }
        return null
    }

    fun updateAnswerInTodaysVisit(studentId: String, evaluatedLiveAnswer: QuizAnswer): StudentPersistedData? {
        if (studentId.isBlank()) {
            println("PersistenceService: Impossibile aggiornare la risposta per un studentId vuoto.")
            return null
        }
        val studentData = loadStudentData(studentId)
        if (studentData == null) {
            println("PersistenceService: Nessun dato per lo studente '$studentId'. Impossibile aggiornare la risposta.")
            return null
        }

        val todayDateStr = getCurrentDateString()
        val visitToUpdate = studentData.visits.find { it.visitDate == todayDateStr && it.studentId == studentId }

        if (visitToUpdate == null) {
            println("PersistenceService: Nessun record di visita per oggi ('$todayDateStr') trovato per lo studente '$studentId' per aggiornare la risposta con QID '${evaluatedLiveAnswer.questionId}'.")
            return null
        }

        val answerIndex = visitToUpdate.answers.indexOfFirst { it.questionId == evaluatedLiveAnswer.questionId }
        if (answerIndex != -1) {
            val existingPersistedAnswer = visitToUpdate.answers[answerIndex]
            visitToUpdate.answers[answerIndex] = existingPersistedAnswer.copy(
                isCorrect = evaluatedLiveAnswer.isCorrect,
            )
            if (saveStudentData(studentData)) {
                println("PersistenceService: Correttezza della risposta per QID '${evaluatedLiveAnswer.questionId}' aggiornata nella visita del '$todayDateStr' per lo studente '$studentId'.")
                return studentData
            }
        } else {
            println("PersistenceService: Risposta persistita originale con QID '${evaluatedLiveAnswer.questionId}' non trovata per lo studente '$studentId' nella visita del '$todayDateStr' da aggiornare.")
        }
        return null
    }
}