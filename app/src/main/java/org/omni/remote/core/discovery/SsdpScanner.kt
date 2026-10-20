package org.omni.remote.core.discovery

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

data class DiscoveredTv(val name: String, val ipAddress: String, val brand: String)

object SsdpScanner {
    private const val SSDP_IP = "239.255.255.250"
    private const val SSDP_PORT = 1900

    suspend fun scan(context: Context): List<DiscoveredTv> = withContext(Dispatchers.IO) {
        val discovered = mutableListOf<DiscoveredTv>()
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        
        // CRITICAL FIX: Explicitly acquire MulticastLock so Android kernel doesn't drop SSDP packets
        val multicastLock = wifiManager?.createMulticastLock("omni_remote_ssdp")?.apply {
            setReferenceCounted(true)
            acquire()
        }

        // Comprehensive discovery query matching SSDP, DIAL, and UPnP root devices
        val query = """
            M-SEARCH * HTTP/1.1
            HOST: 239.255.255.250:1900
            MAN: "ssdp:discover"
            MX: 3
            ST: ssdp:all
        """.trimIndent().replace("\n", "\r\n") + "\r\n\r\n"

        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 2800
                val data = query.toByteArray()
                val packet = DatagramPacket(data, data.size, InetAddress.getByName(SSDP_IP), SSDP_PORT)
                socket.send(packet)

                val buffer = ByteArray(2048)
                val deadline = System.currentTimeMillis() + 2800
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val receivePacket = DatagramPacket(buffer, buffer.size)
                        socket.receive(receivePacket)
                        val response = String(receivePacket.data, 0, receivePacket.length)
                        val senderIp = receivePacket.address.hostAddress ?: continue

                        // Multi-brand recognition for all Smart TV options
                        val brand = when {
                            response.contains("roku", ignoreCase = true) -> "Roku"
                            response.contains("webos", ignoreCase = true) || response.contains("lge", ignoreCase = true) -> "LG"
                            response.contains("samsung", ignoreCase = true) || response.contains("tizen", ignoreCase = true) -> "Samsung"
                            response.contains("sony", ignoreCase = true) || response.contains("bravia", ignoreCase = true) -> "Sony"
                            response.contains("firetv", ignoreCase = true) || response.contains("amazon", ignoreCase = true) -> "FireTV"
                            response.contains("googlecast", ignoreCase = true) || response.contains("androidtv", ignoreCase = true) -> "AndroidTV"
                            response.contains("vizio", ignoreCase = true) || response.contains("smartcast", ignoreCase = true) -> "Vizio"
                            else -> null
                        }

                        if (brand != null && discovered.none { it.ipAddress == senderIp }) {
                            discovered.add(DiscoveredTv("$brand TV ($senderIp)", senderIp, brand))
                        }
                    } catch (_: Exception) {
                        break
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            if (multicastLock?.isHeld == true) {
                multicastLock.release()
            }
        }
        discovered
    }
}
