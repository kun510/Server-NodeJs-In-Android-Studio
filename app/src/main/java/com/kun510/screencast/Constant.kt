package com.kun510.screencast

import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

object Constant {
    val SERVER_CONNECT get() = "http://${getLocalIpAddress()}:5555"


     private fun getLocalIpAddress(): String? {
        return try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            findIpAddress(interfaces)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun findIpAddress(interfaces: List<NetworkInterface>): String? {
        for (into in interfaces) {
            val ipAddress = checkAddresses(Collections.list(into.inetAddresses))
            if (ipAddress != null) {
                return ipAddress
            }
        }
        return null
    }

    private fun checkAddresses(addressList: List<InetAddress>): String? {
        for (adder in addressList) {
            if (!adder.isLoopbackAddress) {
                val sAdder = adder.hostAddress
                if (sAdder.indexOf(':') < 0) {
                    return sAdder
                }
            }
        }
        return null
    }

}