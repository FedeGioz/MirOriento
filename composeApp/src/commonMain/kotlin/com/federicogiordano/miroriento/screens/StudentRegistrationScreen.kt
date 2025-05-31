package com.federicogiordano.miroriento.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.federicogiordano.miroriento.data.SchoolFocus
import com.federicogiordano.miroriento.viewmodels.StudentViewModel

@OptIn(ExperimentalMaterialApi::class)
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
        Text("Benvenuto a", style = MaterialTheme.typography.h4, modifier = Modifier.padding(bottom = 16.dp).align(Alignment.CenterHorizontally))
        Text("MirOriento!", style = MaterialTheme.typography.h4, modifier = Modifier.padding(bottom = 16.dp).align(Alignment.CenterHorizontally))
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

        ExposedDropdownMenuBox(
            expanded = schoolFocusDropdownExpanded,
            onExpandedChange = {
                println("ExposedDropdownMenuBox onExpandedChange: $it. Current expanded: $schoolFocusDropdownExpanded")
                schoolFocusDropdownExpanded = it
                println("schoolFocusDropdownExpanded è ora: $schoolFocusDropdownExpanded")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedFocus.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                onValueChange = {},
                label = { Text("Indirizzo Scolastico (*)") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = schoolFocusDropdownExpanded)
                }
            )

            ExposedDropdownMenu(
                expanded = schoolFocusDropdownExpanded,
                onDismissRequest = {
                    println("ExposedDropdownMenu onDismissRequest. Impostazione schoolFocusDropdownExpanded su false.")
                    schoolFocusDropdownExpanded = false
                    println("schoolFocusDropdownExpanded è ora: $schoolFocusDropdownExpanded")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                println("--- ExposedDropdownMenu contenuto lambda INIZIO. schoolFocusDropdownExpanded: $schoolFocusDropdownExpanded ---")
                val focusValues = SchoolFocus.entries
                println("ExposedDropdownMenu: conteggio focusValues = ${focusValues.size}")

                if (focusValues.isEmpty()) {
                    DropdownMenuItem(onClick = {
                        println("ExposedDropdownMenu: Cliccato 'Nessun indirizzo'.")
                        schoolFocusDropdownExpanded = false
                    }, enabled = false) {
                        Text("Nessun indirizzo disponibile")
                    }
                } else {
                    focusValues.forEach { focus ->
                        DropdownMenuItem(onClick = {
                            println("ExposedDropdownMenu: Elemento '${focus.name}' cliccato.")
                            selectedFocus = focus
                            schoolFocusDropdownExpanded = false
                            println("ExposedDropdownMenu: schoolFocusDropdownExpanded impostato su false dal click dell'elemento.")
                        }) {
                            Text(focus.name.replace('_', ' ').lowercase().split(" ").joinToString(" ") { str -> str.replaceFirstChar(Char::titlecase) })
                        }
                    }
                }
                println("--- ExposedDropdownMenu contenuto lambda FINE ---")
            }
        }
        LaunchedEffect(schoolFocusDropdownExpanded) {
            println("LaunchedEffect: schoolFocusDropdownExpanded cambiato a: $schoolFocusDropdownExpanded")
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