package com.bintianqi.owndroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bintianqi.owndroid.AppConfig
import com.bintianqi.owndroid.OwndroidRemoteConfig
import com.bintianqi.owndroid.RemoteConfigManager
import com.bintianqi.owndroid.popToast
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
object RemoteConfigViewer

/**
 * Màn hình chỉ-xem cấu hình hiện tại + nút Sync.
 * Mở được ngay từ màn Login (chưa login vẫn dùng được). Không cho sửa gì ở đây.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteConfigViewerScreen(onNavigateUp: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cfg by remember { mutableStateOf<OwndroidRemoteConfig?>(null) }
    var syncing by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var lastSync by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        cfg = RemoteConfigManager.effective()
        lastSync = RemoteConfigManager.lastSyncMillis()
        if (cfg == null) msg = "Chưa từng Sync. Bấm Sync để tải cấu hình từ web."
    }

    fun doSync() {
        scope.launch {
            syncing = true
            msg = "Đang sync từ ${RemoteConfigManager.baseUrl()} ..."
            val r = RemoteConfigManager.sync(context)
            syncing = false
            if (r.isSuccess) {
                cfg = r.getOrNull()
                lastSync = RemoteConfigManager.lastSyncMillis()
                msg = "Sync thành công."
                context.popToast("Đã sync cấu hình")
            } else {
                msg = r.exceptionOrNull()?.message ?: "Sync thất bại"
                context.popToast(msg)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cấu hình hiện tại") },
                navigationIcon = { NavIcon(onNavigateUp) }
            )
        }
    ) { pv ->
        Column(
            Modifier.fillMaxSize().padding(pv).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Chỉ xem — muốn sửa thì sửa trên web (phải giữ màn hình 30 phút), xong bấm Sync ở đây.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = ::doSync,
                enabled = !syncing,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (syncing) "Đang sync…" else "🔄 Sync ngay") }
            if (msg.isNotEmpty()) {
                Text(msg, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (lastSync > 0L) {
                val t = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault()).format(Date(lastSync))
                Text("Lần sync cuối: $t", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            val c = cfg
            if (c == null) {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "Đang dùng cấu hình mặc định trong máy. Sync để lấy cấu hình từ web.",
                        modifier = Modifier.padding(12.dp), fontSize = 13.sp
                    )
                }
            } else {
                Section("🔒 Khoá chặt chẽ") {
                    Row2("Unlock mỗi lần", "${c.strictLock.unlockMinutes} phút")
                    Row2("Block sau unlock", "${c.strictLock.blockMinutes} phút")
                    Row2("Lượt mở/ngày", "${c.strictLock.dailyUnlocks}")
                }
                Section("🌙 Giờ đêm") {
                    Row2("Bắt đầu", "${c.nightMode.startHour}h")
                    Row2("Kết thúc", "${c.nightMode.endHour}h")
                    Row2("Day tiers", c.dayTiers.joinToString(", ") { "${it.req}:${it.unlock}:${it.block}" })
                    Row2("Night tiers", c.nightTiers.joinToString(", ") { "${it.req}:${it.unlock}:${it.block}" })
                }
                Section("🔑 Màn login") {
                    Row2("Số lần nhập → Settings", "${c.login.attemptsForSettings}")
                    Row2("Login lại sau", "${c.login.reloginMinutes} phút")
                    Row2("Độ dài mã", "${c.login.challengeLength}")
                }
                Section("📱 Danh sách app") {
                    Row2("Softlock (khoá mềm)", "${c.softlockApps.size}")
                    PkgList(c.softlockApps)
                    Row2("Hardlock (khoá cứng)", "${c.hardlockApps.size}")
                    PkgList(c.hardlockApps)
                    Row2("Whitelist đêm thêm", "${c.nightWhitelistExtra.size}")
                    PkgList(c.nightWhitelistExtra)
                    Row2("VPN", c.vpn.`package`)
                }
            }

            // Giá trị đang có hiệu lực (remote hoặc fallback) — minh bạch để khỏi nhầm
            Section("⚙️ Đang có hiệu lực") {
                val s = AppConfig.getStrictTier()
                Row2("Strict", "${s.unlockMinutes}p / block ${s.blockMinutes}p / ${AppConfig.getStrictDailyUnlocks()} lượt/ngày")
                Row2("Vào Settings", "${AppConfig.getAttemptsForSettings()} lần nhập")
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onNavigateUp, modifier = Modifier.fillMaxWidth()) {
                Text("← Quay lại Login")
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            content()
        }
    }
}

@Composable
private fun Row2(k: String, v: String) {
    Text("$k: $v", fontSize = 13.sp)
}

@Composable
private fun PkgList(pkgs: List<String>) {
    if (pkgs.isEmpty()) {
        Text("(Trống)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Text(pkgs.joinToString("\n"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
