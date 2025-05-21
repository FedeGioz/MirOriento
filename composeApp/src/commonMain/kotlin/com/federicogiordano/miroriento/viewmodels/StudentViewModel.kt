package com.federicogiordano.miroriento.viewmodels

import androidx.lifecycle.ViewModel
import com.federicogiordano.miroriento.data.StudentInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StudentViewModel : ViewModel() {
    private val _studentInfo = MutableStateFlow<StudentInfo?>(null)
    val studentInfo: StateFlow<StudentInfo?> = _studentInfo.asStateFlow()

    fun saveStudentInfo(info: StudentInfo) {
        _studentInfo.value = info
    }
}