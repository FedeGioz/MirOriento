package com.federicogiordano.miroriento.network

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PortScanner {
    private val _scanStatus = MutableStateFlow<ScanStatus>(ScanStatus.NotStarted)
    val scanStatus: StateFlow<ScanStatus> = _scanStatus

    private val _scanLog = MutableStateFlow<String>("Avvio scansione...")
    val scanLog: StateFlow<String> = _scanLog

    private fun log(message: String) {
        println("PortScanner: $message")
        _scanLog.value = message
    }

    suspend fun findProfessorDevice(): String? {
        _scanStatus.value = ScanStatus.Scanning
        log("Avvio scansione di rete per il dispositivo del professore")

        return try {
            val deviceIPs = getLocalIpAddresses()
            log("Trovati i seguenti IP sul dispositivo: ${deviceIPs.joinToString()}")

            val networkAddresses = getNetworkAddresses(deviceIPs)
            if (networkAddresses.isEmpty()) {
                log("⚠️ Impossibile determinare gli indirizzi di rete")
                _scanStatus.value = ScanStatus.Error("Nessuna interfaccia di rete trovata")
                return null
            }

            log("Verranno scansionate le seguenti reti: ${networkAddresses.joinToString { "${it.first}/${it.second}" }}")

            for ((networkPrefix, subnetMask) in networkAddresses) {
                log("Scansione della rete: $networkPrefix/$subnetMask")

                val ipList = generateIpRange(networkPrefix, subnetMask)
                log("Generati ${ipList.size} IP da scansionare in questa sottorete")

                for (ip in ipList) {
                    log("Controllo $ip:8080...")

                    if (isPortOpen(ip, 8080)) {
                        log("✓ Trovato il dispositivo del professore a $ip:8080")
                        _scanStatus.value = ScanStatus.Found(ip)
                        return ip
                    }
                }
            }

            log("❌ Nessun dispositivo del professore trovato su alcuna rete")
            _scanStatus.value = ScanStatus.NotFound
            null
        } catch (e: Exception) {
            log("⚠️ Errore durante la scansione: ${e.message}")
            e.printStackTrace()
            _scanStatus.value = ScanStatus.Error(e.message ?: "Errore sconosciuto")
            null
        }
    }

    private suspend fun isPortOpen(ip: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            withTimeoutOrNull(10_000) {
                try {
                    val socket = aSocket(SelectorManager(Dispatchers.IO))
                        .tcp()
                        .connect(InetSocketAddress(ip, port)) {
                            socketTimeout = 500
                        }
                    log("Connesso a $ip:$port con successo ✓")
                    socket.close()
                    true
                } catch (e: Exception) {
                    when {
                        e.message?.contains("Connection refused") == true ->
                            log("Connessione rifiutata su $ip:$port (l'host esiste ma la porta è chiusa)")
                        e.message?.contains("timed out") == true ->
                            log("Timeout su $ip:$port (l'host probabilmente non esiste)")
                        else ->
                            log("Fallito tentativo di connessione a $ip:$port: ${e.message}")
                    }
                    false
                }
            } ?: run {
                log("⏱ Operazione scaduta dopo 10 secondi per $ip:$port")
                false
            }
        } catch (e: Exception) {
            log("⚠️ Errore inaspettato durante il controllo di $ip:$port: ${e.message}")
            false
        }
    }

    private fun getLocalIpAddresses(): List<String> {
        return listOf("192.168.1.5", "10.0.2.15")
    }

    private fun getNetworkAddresses(ips: List<String>): List<Pair<String, Int>> {
        val result = mutableListOf<Pair<String, Int>>()

        for (ip in ips) {
            val parts = ip.split(".")
            if (parts.size == 4) {
                val network = "${parts[0]}.${parts[1]}.${parts[2]}"
                result.add(Pair(network, 24))
            }
        }

        return result
    }

    private fun generateIpRange(networkPrefix: String, cidr: Int): List<String> {
        return (1..254).map { "$networkPrefix.$it" }
    }

    sealed class ScanStatus {
        object NotStarted : ScanStatus()
        object Scanning : ScanStatus()
        object NotFound : ScanStatus()
        data class Found(val ipAddress: String) : ScanStatus()
        data class Error(val message: String) : ScanStatus()
    }
}