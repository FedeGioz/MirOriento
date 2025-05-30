package com.federicogiordano.miroriento.network

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal expect suspend fun getGatewayIpAddress(): String?

class PortScanner {
    private val _scanStatus = MutableStateFlow<ScanStatus>(ScanStatus.NotStarted)
    val scanStatus: StateFlow<ScanStatus> = _scanStatus

    private val _scanLog = MutableStateFlow("Avvio scansione...")
    val scanLog: StateFlow<String> = _scanLog

    private fun log(message: String) {
        println("PortScanner: $message")
        _scanLog.value = message
    }

    suspend fun findProfessorDevice(): String? {
        _scanStatus.value = ScanStatus.Scanning
        log("Avvio scansione del dispositivo del professore...")

        val gatewayIp = getGatewayIpAddress()

        if (gatewayIp != null) {
            log("IP del gateway ottenuto: $gatewayIp. Verifica porta...")
            if (isPortOpen(gatewayIp, 8080)) {
                _scanStatus.value = ScanStatus.Found(gatewayIp)
                log("Dispositivo del professore trovato all'IP del gateway: $gatewayIp")
                return gatewayIp
            } else {
                log("Porta 8080 non aperta sull'IP del gateway: $gatewayIp")
            }
        } else {
            log("Impossibile determinare l'IP del gateway. Controlla le implementazioni 'actual' di getGatewayIpAddress().")
        }

        log("Dispositivo del professore non trovato (controllato IP gateway).")
        _scanStatus.value = ScanStatus.NotFound
        return null
    }

    private suspend fun isPortOpen(ip: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            withTimeoutOrNull(500L) {
                try {
                    val socket = aSocket(SelectorManager(Dispatchers.IO))
                        .tcp()
                        .connect(InetSocketAddress(ip, port)) {
                            socketTimeout = 300
                        }
                    log("Connesso a $ip:$port con successo ✓")
                    socket.close()
                    true
                } catch (e: kotlinx.io.IOException) {
                    val errorMessage = e.message?.lowercase() ?: ""
                    when {
                        errorMessage.contains("connection refused") ->
                            log("Connessione rifiutata su $ip:$port (host attivo, porta chiusa o firewall)")
                        errorMessage.contains("no route to host") ->
                            log("Nessun percorso per l'host $ip:$port (host irraggiungibile o rete mal configurata)")
                        errorMessage.contains("timed out") || errorMessage.contains("timeout") ->
                            log("Timeout di connessione o del socket su $ip:$port (host non risponde, porta filtrata, o timeout specifico di Ktor)")
                        else ->
                            log("Fallito tentativo di connessione a $ip:$port (IOException): ${e::class.simpleName} - ${e.message}")
                    }
                    false
                } catch (e: Exception) {
                    log("Fallito tentativo di connessione a $ip:$port: ${e::class.simpleName} - ${e.message}")
                    false
                }
            } ?: run {
                log("Timeout generale (withTimeoutOrNull) durante il tentativo di connessione a $ip:$port")
                false
            }
        } catch (e: Exception) {
            log("Errore imprevisto durante la verifica di $ip:$port: ${e::class.simpleName} - ${e.message}")
            false
        }
    }

    sealed class ScanStatus {
        data object NotStarted : ScanStatus()
        data object Scanning : ScanStatus()
        data object NotFound : ScanStatus()
        data class Found(val ipAddress: String) : ScanStatus()
        data class Error(val message: String) : ScanStatus()
    }
}