package com.dit.fgv.service

import android.content.Intent
import android.net.VpnService
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy as JavaProxy
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FgVpnService : VpnService() {
    private val proxy by lazy { Proxy.getInstance() }
    private var vpnThread: Thread? = null
    private var monitorThread: Thread? = null
    private var tun: ParcelFileDescriptor? = null

    override fun onCreate() {
        super.onCreate()
        val storedClientId = getSharedPreferences(PREFS, MODE_PRIVATE).getString(CLIENT_ID, null)
        val clientId = if (storedClientId.isNullOrEmpty() || storedClientId.length <= 36) {
            ORIGINAL_CLIENT_ID.also {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(CLIENT_ID, it).apply()
            }
        } else {
            storedClientId
        }
        proxy.NativeSetClientInfo(clientId.take(36), "1.7p")
        Log.i(TAG, "Service created; clientId=${clientId.take(8)}..., fdCount=${fdCount()}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> connect()
            ACTION_DISCONNECT -> disconnect()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)

    override fun onRevoke() {
        disconnect()
        stopSelf()
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    @Synchronized
    private fun connect() {
        if (mutableState.value != ConnectionState.DISCONNECTED && mutableState.value != ConnectionState.FAILED) return
        mutableState.value = ConnectionState.CONNECTING
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val cache = prefs.getString(CF_CACHE, null).orEmpty().ifEmpty {
            ORIGINAL_CF_CACHE.also { prefs.edit().putString(CF_CACHE, it).apply() }
        }
        Proxy.NativeSetCfIpCache(cache)
        proxy.PublicProxyOff()
        Log.i(TAG, "Starting native proxy; fdCount=${fdCount()}")
        proxy.start()
        startFdWatchdog()
        monitorThread = Thread({ monitorProxy() }, "ProxyMonitor").also { it.start() }
    }

    private fun monitorProxy() {
        var homepageAttempts = 0
        while (!Thread.currentThread().isInterrupted) {
            try {
                Thread.sleep(1_000)
            } catch (_: InterruptedException) {
                return
            }
            when (proxy.getStatus()) {
                Proxy.Status.READY -> {
                    Log.i(TAG, "Native proxy ready; port=${proxy.NativeGetLocalPort()}, fdCount=${fdCount()}")
                    if (testHomepage()) {
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putString(CF_CACHE, Proxy.NativeGetCfIpCache()).apply()
                        startVpnTunnel()
                        return
                    }
                    homepageAttempts++
                    if (homepageAttempts < 2) {
                        proxy.stop()
                        proxy.start()
                    } else {
                        failConnection()
                        return
                    }
                }
                Proxy.Status.ERROR, Proxy.Status.STOPPED -> {
                    Log.e(TAG, "Native proxy failed; status=${proxy.getStatus()}, fdCount=${fdCount()}")
                    failConnection()
                    return
                }
                Proxy.Status.SEARCHING -> Unit
            }
        }
    }

    private fun testHomepage(): Boolean = runCatching {
        val localProxy = JavaProxy(
            JavaProxy.Type.HTTP,
            InetSocketAddress("127.0.0.1", proxy.NativeGetLocalPort().toInt()),
        )
        (URL("http://dongtaiwang.com/loc/mobile/").openConnection(localProxy) as HttpURLConnection).run {
            connectTimeout = 10_000
            readTimeout = 20_000
            requestMethod = "GET"
            responseCode == HttpURLConnection.HTTP_OK
        }
    }.getOrDefault(false)

    @Synchronized
    private fun startVpnTunnel() {
        if (vpnThread?.isAlive == true) return
        vpnThread = Thread({ runVpnTunnel() }, "FgVpnThread").also { it.start() }
    }

    private fun runVpnTunnel() {
        try {
            val descriptor = Builder()
                .setMtu(MTU)
                .addAddress(VPN_ADDRESS, 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                // 排除本应用自身流量,否则原生代理连节点的流量会被 TUN 再次路由回来,
                // 形成回环导致文件描述符暴涨,触发 libproxy.so 的 select()/FD_SET(fd>=1024) 崩溃。
                .apply { runCatching { addDisallowedApplication(packageName) } }
                .setSession("FGV Session")
                .establish() ?: error("VPN permission was not granted")
            tun = descriptor
            mutableState.value = ConnectionState.CONNECTED
            Tun2Socks.runTun2Socks(
                descriptor.detachFd(),
                MTU,
                VPN_ADDRESS,
                "255.255.255.255",
                "127.0.0.1:${proxy.NativeGetLocalPort()}",
                "127.0.0.1:34567",
                1,
            )
        } catch (_: Exception) {
            if (mutableState.value != ConnectionState.DISCONNECTED) mutableState.value = ConnectionState.FAILED
        } finally {
            tun?.close()
            tun = null
            if (mutableState.value == ConnectionState.CONNECTED) mutableState.value = ConnectionState.DISCONNECTED
        }
    }

    @Synchronized
    private fun disconnect() {
        mutableState.value = ConnectionState.DISCONNECTING
        monitorThread?.interrupt()
        monitorThread = null
        runCatching { Tun2Socks.terminateTun2Socks() }
        vpnThread?.interrupt()
        runCatching { vpnThread?.join(3_000) }
        vpnThread = null
        runCatching { tun?.close() }
        tun = null
        proxy.stop()
        mutableState.value = ConnectionState.DISCONNECTED
    }

    private fun failConnection() {
        proxy.stop()
        mutableState.value = ConnectionState.FAILED
    }

    private fun startFdWatchdog() {
        Thread({
            while (mutableState.value == ConnectionState.CONNECTING) {
                val count = fdCount()
                if (count >= FD_ABORT_THRESHOLD) {
                    Log.e(TAG, "Stopping native proxy before FD overflow; fdCount=$count")
                    proxy.stop()
                    mutableState.value = ConnectionState.FAILED
                    return@Thread
                }
                try {
                    Thread.sleep(10)
                } catch (_: InterruptedException) {
                    return@Thread
                }
            }
        }, "ProxyFdWatchdog").start()
    }

    private fun fdCount(): Int = File("/proc/self/fd").list()?.size ?: -1

    enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING, FAILED }

    companion object {
        const val ACTION_CONNECT = "com.slimenull.wcxjvpn.CONNECT"
        const val ACTION_DISCONNECT = "com.slimenull.wcxjvpn.DISCONNECT"
        private const val PREFS = "vpn"
        private const val CLIENT_ID = "C_UUID"
        private const val CF_CACHE = "CF_CACHE"
        private const val MTU = 1300
        private const val VPN_ADDRESS = "10.10.0.1"
        private const val TAG = "WCXJ-VPN"
        private const val FD_ABORT_THRESHOLD = 800
        private const val ORIGINAL_CLIENT_ID = "916f3c04-5412-4164-b765-5c1df42fcc626a528175[-19, -87, -121, 7, -5, 24, -88, -16, 70, -38, 121, -3, -58, 76, 30, -111]"
        private const val ORIGINAL_CF_CACHE = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

        private val mutableState = MutableStateFlow(ConnectionState.DISCONNECTED)
        val state: StateFlow<ConnectionState> = mutableState
    }
}
