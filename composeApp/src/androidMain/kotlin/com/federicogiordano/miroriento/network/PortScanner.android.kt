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
        Log.e(TAG, "ApplicationContext is null. Cannot get gateway IP.")
        return null
    }
    Log.d(TAG, "Context obtained successfully.")

    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Log.d(TAG, "Using Android M+ API path.")
        val activeNetwork: Network? = connectivityManager.activeNetwork
        if (activeNetwork == null) {
            Log.w(TAG, "No active network found.")
            return null
        }
        Log.d(TAG, "Active network found: $activeNetwork")

        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        if (networkCapabilities == null) {
            Log.w(TAG, "No network capabilities found for active network.")
            return null
        }
        Log.d(TAG, "Network capabilities: $networkCapabilities")
        if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) Log.d(TAG, "Active network is Wi-Fi.")
        if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) Log.d(TAG, "Active network is Cellular.")
        if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) Log.d(TAG, "Active network is Ethernet.")


        val linkProperties: LinkProperties? = connectivityManager.getLinkProperties(activeNetwork)
        if (linkProperties == null) {
            Log.w(TAG, "No link properties found for active network.")
            return null
        }
        Log.d(TAG, "Link properties found. Routes: ${linkProperties.routes.size}")

        for (routeInfo in linkProperties.routes) {
            Log.d(TAG, "Checking route: $routeInfo")
            if (routeInfo.isDefaultRoute && routeInfo.gateway is Inet4Address) {
                val gatewayAddress = (routeInfo.gateway as Inet4Address).hostAddress
                Log.i(TAG, "Default route with Inet4Address gateway found: $gatewayAddress")
                return gatewayAddress
            } else {
                if (!routeInfo.isDefaultRoute) Log.d(TAG, "Route is not default.")
                if (routeInfo.gateway !is Inet4Address) Log.d(TAG, "Gateway is not Inet4Address: ${routeInfo.gateway}")
            }
        }
        Log.w(TAG, "No default route with an IPv4 gateway found in LinkProperties.")
    } else {
        Log.d(TAG, "Using pre-Android M fallback API path (less reliable).")
        @Suppress("DEPRECATION")
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager?
        if (wifiManager == null) {
            Log.w(TAG, "WifiManager is null (pre-M).")
            return null
        }
        @Suppress("DEPRECATION")
        val dhcpInfo = wifiManager.dhcpInfo
        if (dhcpInfo != null && dhcpInfo.gateway != 0) {
            val gatewayAddress = intToIp(dhcpInfo.gateway)
            Log.i(TAG, "Gateway found via DHCP info (pre-M): $gatewayAddress")
            return gatewayAddress
        } else {
            Log.w(TAG, "Could not get gateway from DHCP info (pre-M). Gateway int: ${dhcpInfo?.gateway}")
        }
    }
    Log.e(TAG, "Failed to determine gateway IP address through all methods.")
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