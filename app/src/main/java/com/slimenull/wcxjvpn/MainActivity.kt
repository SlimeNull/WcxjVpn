package com.slimenull.wcxjvpn

import android.content.Intent
import android.net.VpnService
import android.net.TrafficStats
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dit.fgv.service.FgVpnService
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(color = Color(0xFFF7F8FA)) {
                    VpnScreen(
                        connect = ::requestConnection,
                        disconnect = { sendServiceAction(FgVpnService.ACTION_DISCONNECT) },
                    )
                }
            }
        }
    }

    private var pendingConnect: (() -> Unit)? = null

    private fun requestConnection() {
        val permission = VpnService.prepare(this)
        if (permission == null) {
            sendServiceAction(FgVpnService.ACTION_CONNECT)
        } else {
            pendingConnect?.invoke()
        }
    }

    private fun sendServiceAction(action: String) {
        startService(Intent(this, FgVpnService::class.java).setAction(action))
    }

    @Composable
    private fun VpnScreen(connect: () -> Unit, disconnect: () -> Unit) {
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == RESULT_OK) sendServiceAction(FgVpnService.ACTION_CONNECT)
        }
        pendingConnect = {
            VpnService.prepare(this)?.let(permissionLauncher::launch)
        }

        val state by FgVpnService.state.collectAsStateWithLifecycle()
        val active = state == FgVpnService.ConnectionState.CONNECTED ||
            state == FgVpnService.ConnectionState.CONNECTING
        val busy = state == FgVpnService.ConnectionState.CONNECTING ||
            state == FgVpnService.ConnectionState.DISCONNECTING
        var traffic by remember { mutableStateOf(0L to 0L) }

        LaunchedEffect(state) {
            if (state == FgVpnService.ConnectionState.CONNECTING) {
                traffic = 0L to 0L
            }
            if (state == FgVpnService.ConnectionState.CONNECTING ||
                state == FgVpnService.ConnectionState.CONNECTED
            ) {
                val uid = Process.myUid()
                val initialRx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0L)
                val initialTx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0L)
                while (true) {
                    val rx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(initialRx) - initialRx
                    val tx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(initialTx) - initialTx
                    traffic = rx to tx
                    delay(1_000.milliseconds)
                }
            }
        }

        val connected = state == FgVpnService.ConnectionState.CONNECTED
        val accent = when {
            connected -> Color(0xFF16815D)
            active -> Color(0xFFE09A22)
            else -> Color(0xFF3568D4)
        }

        val buttonText = when (state) {
            FgVpnService.ConnectionState.DISCONNECTED -> "连接"
            FgVpnService.ConnectionState.CONNECTING -> "取消"
            FgVpnService.ConnectionState.CONNECTED -> "断开"
            FgVpnService.ConnectionState.DISCONNECTING -> "..."
            FgVpnService.ConnectionState.FAILED -> "连接"
        }

        val statusText = when (state) {
            FgVpnService.ConnectionState.DISCONNECTED -> "未连接"
            FgVpnService.ConnectionState.CONNECTING -> "正在连接"
            FgVpnService.ConnectionState.CONNECTED -> "已连接"
            FgVpnService.ConnectionState.DISCONNECTING -> "正在断开"
            FgVpnService.ConnectionState.FAILED -> "连接失败"
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier.weight(1f)
            ){
                Text(
                    text = "WCXJ",
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF171A21),
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "卧槽, 居然不要钱",
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF737984),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.weight(.5f))

            Box(
                modifier = Modifier
                    .size(256.dp)
                    .background(accent.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .size(218.dp)
                        .clip(RoundedCornerShape(109.dp))
                        .clickable(enabled = !busy, onClick = {
                            if (active) disconnect() else connect()
                        }),
                    shape = CircleShape,
                    color = accent,
                    shadowElevation = 14.dp,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = buttonText,
                            color = Color.White,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = statusText,
                            color = Color.White.copy(alpha = 0.82f),
                            fontSize = 15.sp,
                        )
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            HorizontalDivider(color = Color(0xFFE1E4E9))
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
                TrafficValue(
                    label = "下行流量",
                    value = formatBytes(traffic.first),
                    color = Color(0xFF3568D4),
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.size(width = 1.dp, height = 48.dp).background(Color(0xFFE1E4E9)))
                TrafficValue(
                    label = "上行流量",
                    value = formatBytes(traffic.second),
                    color = Color(0xFF16815D),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    @Composable
    private fun TrafficValue(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
        Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(label, color = Color(0xFF737984), fontSize = 13.sp)
        }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024 * 1024 -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024L * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024))
        bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
