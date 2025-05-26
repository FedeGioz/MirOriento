package com.federicogiordano.miroriento.viewmodels

import androidx.lifecycle.ViewModel
import com.federicogiordano.miroriento.api.QuizClient
import com.federicogiordano.miroriento.data.PersistenceService
import com.federicogiordano.miroriento.data.SchoolFocus
import com.federicogiordano.miroriento.data.StudentInfo
import com.federicogiordano.miroriento.data.StudentPersistedData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StudentViewModel : ViewModel() {
    private val _studentInfo = MutableStateFlow<StudentInfo?>(null)
    val studentInfo: StateFlow<StudentInfo?> = _studentInfo.asStateFlow()

    private val _studentPersistedData = MutableStateFlow<StudentPersistedData?>(null)
    val studentPersistedData: StateFlow<StudentPersistedData?> = _studentPersistedData.asStateFlow()

    private val currentQuizTitle: String?
        get() = QuizClient.currentQuiz.value?.title

    init {
        println("StudentViewModel: Initialized.")
    }

    fun registerStudent(name: String, city: String, focus: SchoolFocus) {
        val studentId = com.federicogiordano.miroriento.utils.generateUUID()
        val info = StudentInfo(id = studentId, name = name, city = city, schoolFocus = focus)
        _studentInfo.value = info

        val persistedData = PersistenceService.ensureVisitRecordForToday(info, currentQuizTitle)
        _studentPersistedData.value = persistedData

        println("StudentViewModel: Student registered: $info. Visit record for today ensured.")
        println("StudentViewModel: Current Persisted Data: $persistedData")
    }

    fun loadStudentSession(studentId: String) {
        if (studentId.isBlank()) {
            println("StudentViewModel: Attempted to load session with blank studentId.")
            _studentInfo.value = null
            _studentPersistedData.value = null
            return
        }
        var data = PersistenceService.loadStudentData(studentId)
        if (data != null) {
            data = PersistenceService.ensureVisitRecordForToday(data.studentInfo, currentQuizTitle)
        }
        _studentPersistedData.value = data
        _studentInfo.value = data?.studentInfo


        if (data != null) {
            println("StudentViewModel: Loaded session for student '${data.studentInfo.name}'. Persisted data: ${_studentPersistedData.value}")
        } else {
            println("StudentViewModel: No persisted data found for student ID '$studentId'.")
        }
    }

    fun ensureDataSaved() {
        _studentPersistedData.value?.let {
            println("StudentViewModel: ensureDataSaved called. Data should be up-to-date via PersistenceService actions.")
        }
    }

    fun getStudent(): StudentInfo? = _studentInfo.value

    fun clearStudentSession() {
        _studentInfo.value = null
        _studentPersistedData.value = null
        println("StudentViewModel: Student session cleared (logged out).")
    }
}