package com.dit.fgv.service

object Tun2Socks {
    init {
        System.loadLibrary("tun2socks")
    }

    @JvmStatic external fun runTun2Socks(
        tunFd: Int,
        mtu: Int,
        ipAddress: String,
        netmask: String,
        socksServerAddress: String,
        udpGatewayAddress: String,
        enableDns: Int,
    ): Int

    @JvmStatic external fun terminateTun2Socks(): Int
    @JvmStatic external fun enableUdpGwKeepAlive(): Int
    @JvmStatic external fun disableUdpGwKeepAlive(): Int

    @JvmStatic
    fun logTun2Socks(level: String, tag: String, message: String) = Unit
}
