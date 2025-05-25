package com.federicogiordano.miroriento.viewmodels

import androidx.lifecycle.ViewModel
import com.federicogiordano.miroriento.data.SchoolFocus
import com.federicogiordano.miroriento.data.StudentInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StudentViewModel : ViewModel() {
    private val _studentInfo = MutableStateFlow<StudentInfo?>(null)
    val studentInfo: StateFlow<StudentInfo?> = _studentInfo.asStateFlow()

    init {
        println("StudentViewModel: Initialized. Current studentInfo: ${_studentInfo.value}")
    }

    fun registerStudent(name: String, city: String, focus: SchoolFocus) {
        val newId = com.federicogiordano.miroriento.utils.generateUUID()
        val info = StudentInfo(id = newId, name = name, city = city, schoolFocus = focus)
        _studentInfo.value = info
        println("StudentViewModel: Student registered and info set: $info")
    }

    fun getStudent(): StudentInfo? = _studentInfo.value
    fun clearStudentSession() {
        _studentInfo.value = null
    }
}