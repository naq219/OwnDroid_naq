package com.bintianqi.owndroid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bintianqi.owndroid.AppConfig
import com.bintianqi.owndroid.R
import com.bintianqi.owndroid.TempUnlockManager

/**
 * Status card displaying unlock state with tier-based buttons.
 * 
 * States:
 * 1. UNLOCKED: Shows countdown + "Lock Now" button
 * 2. BLOCKED: Shows block countdown (cannot enter password)
 * 3. NIGHT MODE: Shows night mode info + available tiers
 * 4. DAY MODE: Shows progress + available tier buttons
 */
@Composable
fun UnlockStatusCard(
    successfulAttempts: Int,
    isUnlockActive: Boolean,
    remainingTimeMillis: Long,
    isBlocked: Boolean,
    blockRemainingMillis: Long,
    isStrictLock: Boolean = false,
    strictRemainingDays: Int = 0,
    strictTodayUsed: Int = 0,
    onUnlock: (AppConfig.UnlockTier) -> Unit,
    onLockNow: (Long) -> Unit,  // blockMinutes
    onExtendBlock: (Long) -> Unit = {}  // extraMinutes: tự nguyện khoá thêm
) {
    // Strict lock behaves like night mode for card coloring
    val isNightMode = isStrictLock || TempUnlockManager.isNightMode()
    val bestTier = AppConfig.getBestTier(successfulAttempts, isNightMode)
    val nextTier = AppConfig.getNextTier(successfulAttempts, isNightMode)
    val firstTier = AppConfig.getFirstTier(isNightMode)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = getCardColor(isUnlockActive, isBlocked, isNightMode, bestTier != null)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                isUnlockActive -> {
                    // During strict lock the current tier is always the strict tier
                    val currentTier = if (isStrictLock) AppConfig.getStrictTier() else bestTier
                    UnlockedState(remainingTimeMillis, currentTier, onLockNow)
                }
                isBlocked -> BlockedState(blockRemainingMillis, onExtendBlock)
                isStrictLock -> StrictLockState(strictRemainingDays, strictTodayUsed, onUnlock)
                isNightMode -> NightModeState(successfulAttempts, bestTier, nextTier, firstTier, onUnlock)
                else -> DayModeState(successfulAttempts, bestTier, nextTier, firstTier, onUnlock)
            }
        }
    }
}

@Composable
private fun UnlockedState(
    remainingMillis: Long,
    currentTier: AppConfig.UnlockTier?,
    onLockNow: (Long) -> Unit
) {
    val totalMinutes = (remainingMillis / 60000).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val seconds = ((remainingMillis / 1000) % 60).toInt()
    val blockMinutes = currentTier?.blockMinutes ?: 0L
    
    // Header + Countdown in one row
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.lock_open_fill0),
            contentDescription = null,
            tint = Color(0xFF2E7D32),
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "ĐÃ MỞ KHÓA",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2E7D32)
        )
    }
    
    // Countdown
    val timeText = if (hours > 0) {
        "⏱️ ${hours}h ${minutes}m ${seconds.toString().padStart(2, '0')}s"
    } else {
        "⏱️ ${minutes}:${seconds.toString().padStart(2, '0')}"
    }
    
    Text(
        text = timeText,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
    
    Spacer(Modifier.height(8.dp))
    
    // Lock Now button
    Button(
        onClick = { onLockNow(blockMinutes) },
        modifier = Modifier.fillMaxWidth().height(40.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
    ) {
        Icon(painterResource(R.drawable.lock_fill0), null, Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("KHÓA NGAY", fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun BlockedState(
    remainingMillis: Long,
    onExtendBlock: (Long) -> Unit = {}
) {
    val minutes = (remainingMillis / 60000).toInt()
    val seconds = ((remainingMillis / 1000) % 60).toInt()
    var showExtendDialog by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("⏳", fontSize = 24.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "ĐANG BLOCK",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFB71C1C)
        )
    }

    Spacer(Modifier.height(4.dp))

    Text(
        text = "Chờ ${minutes}:${seconds.toString().padStart(2, '0')}",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )

    Text(
        text = "Không thể nhập mật khẩu",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(Modifier.height(8.dp))

    OutlinedButton(
        onClick = { showExtendDialog = true },
        modifier = Modifier.fillMaxWidth().height(40.dp),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
    ) {
        Icon(painterResource(R.drawable.lock_fill0), null, Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("KHOÁ THÊM", fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }

    if (showExtendDialog) {
        ExtendBlockDialog(
            onDismiss = { showExtendDialog = false },
            onConfirm = { extraMinutes ->
                showExtendDialog = false
                onExtendBlock(extraMinutes)
            }
        )
    }
}

/**
 * Dialog nhập số + chọn đơn vị (phút/giờ/ngày) để tự nguyện khoá thêm.
 */
@Composable
private fun ExtendBlockDialog(
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit  // extraMinutes
) {
    var numberText by remember { mutableStateOf("") }
    var unitIndex by remember { mutableIntStateOf(0) }
    val unitLabels = listOf("Phút", "Giờ", "Ngày")
    val unitMultipliers = listOf(1L, 60L, 1440L)
    val number = numberText.toIntOrNull()
    val totalMinutes = if (number != null && number > 0) number * unitMultipliers[unitIndex] else 0L
    val isValid = totalMinutes in 1..43200 // tối đa 30 ngày

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Khoá thêm", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "Cộng thêm thời gian chờ vào block hiện tại.",
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = numberText,
                    onValueChange = { numberText = it.filter(Char::isDigit).take(5) },
                    label = { Text("Số lượng") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = numberText.isNotEmpty() && !isValid,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    unitLabels.forEachIndexed { i, label ->
                        FilterChip(
                            selected = unitIndex == i,
                            onClick = { unitIndex = i },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(totalMinutes) },
                enabled = isValid
            ) {
                Text("OK", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Huỷ")
            }
        }
    )
}

@Composable
private fun StrictLockState(
    remainingDays: Int,
    todayUsed: Int,
    onUnlock: (AppConfig.UnlockTier) -> Unit
) {
    val dailyQuota = AppConfig.getStrictDailyUnlocks()
    val remainingUses = dailyQuota - todayUsed

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("🔒", fontSize = 20.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "KHOÁ CHẶT CHẼ",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF6A1B9A)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Còn $remainingDays ngày",
            fontSize = 12.sp,
            color = Color(0xFF6A1B9A)
        )
    }

    Spacer(Modifier.height(8.dp))

    if (remainingUses > 0) {
        val strictTier = AppConfig.getStrictTier()
        Button(
            onClick = { onUnlock(strictTier) },
            modifier = Modifier.fillMaxWidth().height(40.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A)),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
        ) {
            Icon(painterResource(R.drawable.lock_open_fill0), null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "${strictTier.label} (còn $remainingUses/${dailyQuota} lượt hôm nay)",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    } else {
        Text(
            text = "Đã dùng hết ${dailyQuota} lượt mở khoá hôm nay",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFB71C1C),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Chờ qua 00:00 để có lượt mới",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NightModeState(
    attempts: Int,
    bestTier: AppConfig.UnlockTier?,
    nextTier: AppConfig.UnlockTier?,
    firstTier: AppConfig.UnlockTier,
    onUnlock: (AppConfig.UnlockTier) -> Unit
) {
    val minutesRemaining = TempUnlockManager.getMinutesUntilNightModeEnds()
    val hours = minutesRemaining / 60
    val mins = minutesRemaining % 60
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("🌙", fontSize = 20.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "CHẾ ĐỘ ĐÊM",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF3F51B5)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Còn ${hours}h ${mins}m",
            fontSize = 12.sp,
            color = Color(0xFF3F51B5)
        )
    }
    
    Spacer(Modifier.height(8.dp))
    
    // Show unlock button if qualified
    if (bestTier != null) {
        TierButton(bestTier, onUnlock)
        
        if (nextTier != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "💡 +${nextTier.requiredAttempts - attempts} lần → ${nextTier.label}",
                fontSize = 11.sp,
                color = Color(0xFF1565C0)
            )
        }
    } else {
        ProgressSection(attempts, firstTier)
    }
}

@Composable
private fun DayModeState(
    attempts: Int,
    bestTier: AppConfig.UnlockTier?,
    nextTier: AppConfig.UnlockTier?,
    firstTier: AppConfig.UnlockTier,
    onUnlock: (AppConfig.UnlockTier) -> Unit
) {
    if (bestTier != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(
                painterResource(R.drawable.lock_fill0),
                null,
                tint = Color(0xFFE65100),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "✓ Đủ ${bestTier.requiredAttempts} lần!",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE65100)
            )
        }
        
        TierButton(bestTier, onUnlock)
        
        if (nextTier != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "💡 +${nextTier.requiredAttempts - attempts} lần → ${nextTier.label}",
                fontSize = 11.sp,
                color = Color(0xFF1565C0)
            )
        }
    } else {
        ProgressSection(attempts, firstTier)
    }
}

@Composable
private fun TierButton(tier: AppConfig.UnlockTier, onUnlock: (AppConfig.UnlockTier) -> Unit) {
    val color = when {
        tier.unlockMinutes == -1L -> Color(0xFF1976D2)  // Bypass
        tier.unlockMinutes >= 1440 -> Color(0xFF1976D2) // 1 day+
        else -> Color(0xFFFF6D00)                        // Normal
    }
    
    Button(
        onClick = { onUnlock(tier) },
        modifier = Modifier.fillMaxWidth().height(40.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
    ) {
        Icon(painterResource(R.drawable.lock_open_fill0), null, Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(tier.label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun ProgressSection(attempts: Int, firstTier: AppConfig.UnlockTier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 4.dp)
    ) {
        Icon(
            painterResource(R.drawable.lock_fill0),
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Nhập đúng: $attempts / ${firstTier.requiredAttempts}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    
    LinearProgressIndicator(
        progress = { attempts.toFloat() / firstTier.requiredAttempts },
        modifier = Modifier.fillMaxWidth().height(4.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant
    )
}

private fun getCardColor(
    isUnlocked: Boolean,
    isBlocked: Boolean,
    isNightMode: Boolean,
    canUnlock: Boolean
): Color = when {
    isBlocked -> Color(0xFFB71C1C).copy(alpha = 0.15f)
    isUnlocked -> Color(0xFF1B5E20).copy(alpha = 0.15f)
    isNightMode -> Color(0xFF1A237E).copy(alpha = 0.15f)
    canUnlock -> Color(0xFFE65100).copy(alpha = 0.15f)
    else -> Color.Transparent
}
