package com.federicogiordano.miroriento.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import java.net.Inet4Address

object ApplicationContextProvider {
    var applicationContext: Context? = null
}

private const val TAG = "PortScannerAndroid"

internal actual suspend fun getGatewayIpAddress(): String? {
    val context = ApplicationContextProvider.applicationContext
    if (context == null) {
        Log.e(TAG, "ApplicationContext è nullo. Impossibile ottenere l'IP del gateway.")
        return null
    }
    Log.d(TAG, "Contesto ottenuto con successo.")

    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    Log.d(TAG, "Utilizzo del percorso API Android M+.")
    val activeNetwork: Network? = connectivityManager.activeNetwork
    if (activeNetwork == null) {
        Log.w(TAG, "Nessuna rete attiva trovata.")
        return null
    }
    Log.d(TAG, "Rete attiva trovata: $activeNetwork")

    val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
    if (networkCapabilities == null) {
        Log.w(TAG, "Nessuna funzionalità di rete trovata per la rete attiva.")
        return null
    }
    Log.d(TAG, "Funzionalità di rete: $networkCapabilities")
    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) Log.d(TAG, "La rete attiva è Wi-Fi.")
    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) Log.d(TAG, "La rete attiva è cellulare.")
    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) Log.d(TAG, "La rete attiva è Ethernet.")


    val linkProperties: LinkProperties? = connectivityManager.getLinkProperties(activeNetwork)
    if (linkProperties == null) {
        Log.w(TAG, "Nessuna proprietà di collegamento trovata per la rete attiva.")
        return null
    }
    Log.d(TAG, "Proprietà di collegamento trovate. Percorsi: ${linkProperties.routes.size}")

    for (routeInfo in linkProperties.routes) {
        Log.d(TAG, "Controllo del percorso: $routeInfo")
        if (routeInfo.isDefaultRoute && routeInfo.gateway is Inet4Address) {
            val gatewayAddress = (routeInfo.gateway as Inet4Address).hostAddress
            Log.i(TAG, "Percorso predefinito con gateway Inet4Address trovato: $gatewayAddress")
            return gatewayAddress
        } else {
            if (!routeInfo.isDefaultRoute) Log.d(TAG, "Il percorso non è predefinito.")
            if (routeInfo.gateway !is Inet4Address) Log.d(TAG, "Il gateway non è Inet4Address: ${routeInfo.gateway}")
        }
    }
    Log.w(TAG, "Nessun percorso predefinito con un gateway IPv4 trovato in LinkProperties.")
    Log.e(TAG, "Impossibile determinare l'indirizzo IP del gateway con tutti i metodi.")
    return null
}

private fun intToIp(ipAddress: Int): String {
    return String.format(
        "%d.%d.%d.%d",
        ipAddress and 0xff,
        ipAddress shr 8 and 0xff,
        ipAddress shr 16 and 0xff,
        ipAddress shr 24 and 0xff
    )
}