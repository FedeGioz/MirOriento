package com.federicogiordano.miroriento.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.federicogiordano.miroriento.data.SchoolFocus
import com.federicogiordano.miroriento.viewmodels.StudentViewModel

@Composable
fun StudentRegistrationScreen(
    studentViewModel: StudentViewModel,
    onRegistrationComplete: () -> Unit
) {
    var nameInput by remember { mutableStateOf("") }
    var cityInput by remember { mutableStateOf("") }
    var selectedFocus by remember { mutableStateOf(SchoolFocus.INFORMATICA) }
    var schoolFocusDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Benvenuto a MirOriento!", style = MaterialTheme.typography.h4, modifier = Modifier.padding(bottom = 16.dp))
        Text("Per iniziare, inserisci i tuoi dati:", style = MaterialTheme.typography.subtitle1, modifier = Modifier.padding(bottom = 32.dp))

        OutlinedTextField(
            value = nameInput,
            onValueChange = { nameInput = it },
            label = { Text("Il tuo Nome Completo (*)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = cityInput,
            onValueChange = { cityInput = it },
            label = { Text("La tua Città di Residenza (*)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text("Seleziona il tuo indirizzo scolastico:", style = MaterialTheme.typography.subtitle2)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = selectedFocus.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                onValueChange = { },
                label = { Text("Indirizzo Scolastico (*)") },
                modifier = Modifier.fillMaxWidth().clickable { schoolFocusDropdownExpanded = true },
                readOnly = true,
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, "Seleziona Indirizzo") }
            )
            DropdownMenu(
                expanded = schoolFocusDropdownExpanded,
                onDismissRequest = { schoolFocusDropdownExpanded = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                val focusValues = try { SchoolFocus.entries } catch (e: Exception) {
                    SchoolFocus.entries
                }
                focusValues.forEach { focus ->
                    DropdownMenuItem(onClick = {
                        selectedFocus = focus
                        schoolFocusDropdownExpanded = false
                    }) {
                        Text(focus.name.replace('_', ' ').lowercase().split(" ").joinToString(" ") { it.replaceFirstChar(Char::titlecase) })
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                studentViewModel.registerStudent(
                    name = nameInput.trim(),
                    city = cityInput.trim(),
                    focus = selectedFocus
                )
                onRegistrationComplete()
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = nameInput.isNotBlank() && cityInput.isNotBlank()
        ) {
            Text("Conferma e Inizia Sessione", style = MaterialTheme.typography.h6)
        }
    }
}