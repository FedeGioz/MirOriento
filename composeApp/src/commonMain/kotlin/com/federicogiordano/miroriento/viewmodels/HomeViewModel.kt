package com.federicogiordano.miroriento.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.federicogiordano.miroriento.api.ConnectionStatus
import com.federicogiordano.miroriento.api.QuizClient
import com.federicogiordano.miroriento.data.RobotStatus
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    val connectionStatus: StateFlow<ConnectionStatus> = QuizClient.connectionStatus
    val robotStatus: StateFlow<RobotStatus?> = QuizClient.robotStatus

    fun configureQuizClient(studentId: String, studentName: String) {
        if (studentId.isNotBlank() && studentName.isNotBlank()) {
            QuizClient.configure(studentId, studentName)
        } else {
            println("HomeViewModel: Student ID or Name is blank, cannot configure.")
        }
    }

    fun connectToServer(serverIp: String, serverPort: Int, path: String) {
        if (serverIp.isNotBlank() && path.isNotBlank()) {
            viewModelScope.launch {
                QuizClient.connect(serverIp, serverPort, path)
            }
        } else {
            println("HomeViewModel: Server IP or Path is blank, cannot connect.")
        }
    }

    fun disconnectFromServer() {
        viewModelScope.launch {
            QuizClient.disconnect("User initiated disconnect from HomePage")
        }
    }
}