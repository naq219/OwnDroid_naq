package com.bintianqi.owndroid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
    onUnlock: (AppConfig.UnlockTier) -> Unit,
    onLockNow: (Long) -> Unit  // blockMinutes
) {
    val isNightMode = TempUnlockManager.isNightMode()
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
                isUnlockActive -> UnlockedState(remainingTimeMillis, bestTier, onLockNow)
                isBlocked -> BlockedState(blockRemainingMillis)
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
private fun BlockedState(remainingMillis: Long) {
    val minutes = (remainingMillis / 60000).toInt()
    val seconds = ((remainingMillis / 1000) % 60).toInt()
    
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
