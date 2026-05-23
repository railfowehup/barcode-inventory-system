package com.barcodescanner.utils

import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * 网络工具类 - 获取本机IP地址等
 */
object NetworkUtils {

    /**
     * 获取本机局域网 IP 地址
     */
    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress ?: ""
                    }
                }
            }
        } catch (_: Exception) {}
        return ""
    }

    /**
     * 获取设备名称
     */
    fun getDeviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}"
}
