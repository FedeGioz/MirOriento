package com.federicogiordano.miroriento

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.federicogiordano.miroriento.data.FileSystem
import com.federicogiordano.miroriento.network.ApplicationContextProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MirOrientoApp", "MainActivity onCreate: INIZIO DEL METODO")

        ApplicationContextProvider.applicationContext = this.applicationContext
        Log.d("MirOrientoApp", "MainActivity: ApplicationContextProvider.applicationContext impostato su: ${this.applicationContext}")

        try {
            FileSystem.initialize(this.applicationContext)
            Log.d("MirOrientoApp", "MainActivity: FileSystem.initialize CHIAMATO CON SUCCESSO con contesto: ${this.applicationContext}")
        } catch (e: Exception) {
            Log.e("MirOrientoApp", "MainActivity: ECCEZIONE durante FileSystem.initialize", e)
        }

        setContent {
            App()
        }
        Log.d("MirOrientoApp", "MainActivity onCreate: setContent chiamato, configurazione completata.")
    }

    override fun onStop() {
        super.onStop()
        Log.d("MirOrientoApp", "MainActivity: onStop chiamato.")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MirOrientoApp", "MainActivity: onDestroy. L'applicazione si sta chiudendo.")
    }
}