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
        Log.d("MirOrientoApp", "MainActivity onCreate: TOP OF METHOD")

        ApplicationContextProvider.applicationContext = this.applicationContext
        Log.d("MirOrientoApp", "MainActivity: ApplicationContextProvider.applicationContext set to: ${this.applicationContext}")

        try {
            FileSystem.initialize(this.applicationContext)
            Log.d("MirOrientoApp", "MainActivity: FileSystem.initialize CALLED SUCCESSFULLY with context: ${this.applicationContext}")
        } catch (e: Exception) {
            Log.e("MirOrientoApp", "MainActivity: EXCEPTION during FileSystem.initialize", e)
        }

        setContent {
            App()
        }
        Log.d("MirOrientoApp", "MainActivity onCreate: setContent called, setup complete.")
    }

    override fun onStop() {
        super.onStop()
        Log.d("MirOrientoApp", "MainActivity: onStop called.")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MirOrientoApp", "MainActivity: onDestroy. Application is closing.")
    }
}