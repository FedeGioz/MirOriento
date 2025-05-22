package com.federicogiordano.miroriento.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.federicogiordano.miroriento.viewmodels.StudentViewModel
import com.federicogiordano.miroriento.data.StudentInfo
import com.federicogiordano.miroriento.data.SchoolFocus

@Composable
fun StudentRegistrationScreen(navController: NavController, studentViewModel: StudentViewModel) {
    var name by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var selectedFocus by remember { mutableStateOf(SchoolFocus.INFORMATICA) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Registrazione Studente",
            style = MaterialTheme.typography.h4,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nome Completo") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = city,
            onValueChange = { city = it },
            label = { Text("Città di Residenza") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Seleziona il tuo indirizzo scolastico desiderato:",
            style = MaterialTheme.typography.subtitle1,
            modifier = Modifier.align(Alignment.Start)
        )

        Column {
            SchoolFocus.values().forEach { focus ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    RadioButton(
                        selected = selectedFocus == focus,
                        onClick = { selectedFocus = focus }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(focus.name.lowercase().capitalize())
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                studentViewModel.saveStudentInfo(
                    StudentInfo(name, city, selectedFocus)
                )
                navController.navigate(Screens.Waiting.route)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = name.isNotBlank() && city.isNotBlank()
        ) {
            Text("Continua")
        }
    }
}