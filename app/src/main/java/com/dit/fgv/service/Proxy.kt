package com.dit.fgv.service

class Proxy private constructor() {
    enum class Status { STOPPED, SEARCHING, READY, ERROR }

    @Volatile
    private var status = Status.STOPPED

    external fun NativeGetLocalPort(): String
    external fun NativeSetClientInfo(clientId: String, version: String)
    external fun PublicProxyOff()
    external fun PublicProxyOnPort(port: Int)
    private external fun NativeGetProxyStatus(): String
    private external fun NativeStart()
    private external fun NativeStop()

    fun getStatus(): Status {
        if (status == Status.STOPPED) return status
        return when (NativeGetProxyStatus()) {
            "ready" -> Status.READY
            "searching" -> Status.SEARCHING
            else -> Status.ERROR
        }
    }

    @Synchronized
    fun start() {
        if (thread?.isAlive == true) return
        status = Status.SEARCHING
        thread = Thread({ NativeStart() }, "ProxyThread").also { it.start() }
    }

    @Synchronized
    fun stop() {
        val running = thread ?: return
        runCatching { NativeStop() }
        running.interrupt()
        status = Status.STOPPED
        thread = null
    }

    companion object {
        @Volatile
        private var instance: Proxy? = null
        private var thread: Thread? = null

        init {
            System.loadLibrary("proxy")
        }

        @JvmStatic external fun NativeGetCfIpCache(): String
        @JvmStatic external fun NativeSetCfIpCache(value: String)
        @JvmStatic external fun NativeMakeCrash()
        @JvmStatic external fun PostLog(value: String): Boolean
        @JvmStatic private external fun NativeSetContext(proxy: Proxy)

        @JvmStatic
        @Synchronized
        fun getInstance(): Proxy = instance ?: Proxy().also {
            instance = it
            NativeSetContext(it)
        }
    }
}
