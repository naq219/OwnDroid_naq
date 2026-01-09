package com.bintianqi.owndroid.ui

import android.content.pm.PackageManager.NameNotFoundException
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bintianqi.owndroid.R
import com.bintianqi.owndroid.TempUnlockManager
import com.bintianqi.owndroid.popToast

/**
 * Section for managing Hardlock and Softlock app lists.
 * Compact design with expandable details.
 */
@Composable
fun AppListSection(actionsEnabled: Boolean = true, canRemove: Boolean = true) {
    val context = LocalContext.current
    var hardlockApps by remember { mutableStateOf(TempUnlockManager.getHardlockApps()) }
    var softlockApps by remember { mutableStateOf(TempUnlockManager.getSoftlockApps()) }
    var showPicker by remember { mutableStateOf(false) }
    var selectedApps by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    fun refresh() {
        hardlockApps = TempUnlockManager.getHardlockApps()
        softlockApps = TempUnlockManager.getSoftlockApps()
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📱 Quản lý App",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                
                if (actionsEnabled) {
                    TextButton(onClick = { showPicker = true }) {
                        Text("+ Thêm")
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Hardlock section
            AppListGroup(
                title = "🔒 Khóa cứng (${hardlockApps.size})",
                color = Color(0xFFB71C1C),
                apps = hardlockApps,
                enabled = actionsEnabled,
                canRemove = canRemove,
                onMove = { pkg ->
                    TempUnlockManager.moveToSoftlock(pkg)
                    TempUnlockManager.unsuspendApp(pkg)
                    refresh()
                    context.popToast("Đã chuyển sang Softlock")
                },
                onRemove = { pkg ->
                    TempUnlockManager.removeFromHardlock(pkg)
                    TempUnlockManager.unsuspendApp(pkg)
                    refresh()
                    context.popToast("Đã gỡ khỏi danh sách")
                },
                moveLabel = "→ Soft",
                moveColor = Color(0xFFE65100)
            )
            
            Spacer(Modifier.height(12.dp))
            
            // Softlock section
            AppListGroup(
                title = "🔓 Khóa mềm (${softlockApps.size})",
                color = Color(0xFFE65100),
                apps = softlockApps,
                enabled = actionsEnabled,
                canRemove = canRemove,
                onMove = { pkg ->
                    TempUnlockManager.moveToHardlock(pkg)
                    TempUnlockManager.suspendApp(pkg)
                    refresh()
                    context.popToast("Đã chuyển sang Hardlock")
                },
                onRemove = { pkg ->
                    TempUnlockManager.removeFromSoftlock(pkg)
                    TempUnlockManager.unsuspendApp(pkg)
                    refresh()
                    context.popToast("Đã gỡ khỏi danh sách")
                },
                moveLabel = "→ Hard",
                moveColor = Color(0xFFB71C1C)
            )
        }
    }
    
    // App picker dialog (omitted for brevity, same as before)
    if (showPicker) {
        AppPickerDialog(
            selected = selectedApps,
            onSelectionChange = { selectedApps = it },
            onDismiss = { 
                showPicker = false
                selectedApps = emptySet()
            },
            onBlockSoft = {
                selectedApps.forEach { pkg ->
                    TempUnlockManager.addToSoftlock(pkg)
                    TempUnlockManager.suspendApp(pkg)
                }
                refresh()
                showPicker = false
                selectedApps = emptySet()
                context.popToast("Đã thêm ${selectedApps.size} app vào Softlock")
            },
            onBlockHard = {
                selectedApps.forEach { pkg ->
                    TempUnlockManager.addToHardlock(pkg)
                    TempUnlockManager.suspendApp(pkg)
                }
                refresh()
                showPicker = false
                selectedApps = emptySet()
                context.popToast("Đã thêm ${selectedApps.size} app vào Hardlock")
            }
        )
    }
}

@Composable
private fun AppListGroup(
    title: String,
    color: Color,
    apps: List<String>,
    enabled: Boolean,
    canRemove: Boolean,
    onMove: (String) -> Unit,
    onRemove: (String) -> Unit,
    moveLabel: String,
    moveColor: Color
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    Column {
        // Header row (clickable to expand)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = color)
            Text(
                text = if (expanded) "▲" else "▼",
                fontSize = 12.sp,
                color = color
            )
        }
        
        // Expanded content
        if (expanded && apps.isNotEmpty()) {
            Column(modifier = Modifier.padding(start = 8.dp)) {
                apps.forEach { pkg ->
                    val appName = try {
                        context.packageManager.getApplicationLabel(
                            context.packageManager.getApplicationInfo(pkg, 0)
                        ).toString()
                    } catch (e: NameNotFoundException) { pkg }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = appName,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        
                        if (enabled) {
                            Row {
                                 if (canRemove) {

                                TextButton(
                                    onClick = { onMove(pkg) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text(moveLabel, fontSize = 11.sp, color = moveColor)
                                }
                                }
                                
                                
                                if (canRemove) {
                                    TextButton(
                                        onClick = { onRemove(pkg) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Text("Xóa", fontSize = 11.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (expanded) {
            Text(
                "(Trống)",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 8.dp, top = 4.dp)
            )
        }
    }
}
