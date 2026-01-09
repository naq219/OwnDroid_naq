package com.bintianqi.owndroid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bintianqi.owndroid.AppConfig
import com.bintianqi.owndroid.R
import com.bintianqi.owndroid.SP
import com.bintianqi.owndroid.TempUnlockManager
import com.bintianqi.owndroid.popToast
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Random Password Screen V2 - Refactored version with tier-based unlock.
 * 
 * Features:
 * - Tier-based unlock (Day/Night mode)
 * - Block time after unlock expires
 * - Separated UI components
 */
@Serializable
object RandomPasswordScreenV2

private const val TOTAL_ATTEMPTS = 50 // For settings access

@Composable
fun RandomPasswordScreenV2(onSucceed: () -> Unit) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    
    // Password state
    var randomString by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var successfulAttempts by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    
    // Unlock state
    var isUnlockActive by remember { mutableStateOf(TempUnlockManager.isUnlockActive()) }
    var remainingUnlockMillis by remember { mutableLongStateOf(TempUnlockManager.getRemainingTimeMillis()) }
    
    // Block state - check on init and reset attempts if blocked
    val initialBlocked = TempUnlockManager.isBlocked()
    var isBlocked by remember { mutableStateOf(initialBlocked) }
    var remainingBlockMillis by remember { mutableLongStateOf(TempUnlockManager.getRemainingBlockTimeMillis()) }
    
    // IMPORTANT: If blocked on init, reset attempts to 0
    // This prevents unlock immediately after block period was set by RelockWorker
    LaunchedEffect(initialBlocked) {
        if (initialBlocked) {
            successfulAttempts = 0
        }
    }
    
    // Timer effects
    LaunchedEffect(isUnlockActive) {
        while (isUnlockActive && TempUnlockManager.isUnlockActive()) {
            remainingUnlockMillis = TempUnlockManager.getRemainingTimeMillis()
            delay(1000L)
        }
        if (isUnlockActive && !TempUnlockManager.isUnlockActive()) {
            isUnlockActive = false
            remainingUnlockMillis = 0L
            
            // IMPORTANT: Set block period immediately when unlock expires in foreground
            // Don't wait for RelockWorker (which runs in background)
            val blockMinutes = SP.lastUsedBlockMinutes
            if (blockMinutes > 0) {
                TempUnlockManager.setBlockPeriod(blockMinutes)
                isBlocked = true
                remainingBlockMillis = TempUnlockManager.getRemainingBlockTimeMillis()
                // Reset attempts so user must enter password again
                successfulAttempts = 0
            }
        }
    }
    
    LaunchedEffect(isBlocked) {
        while (isBlocked && TempUnlockManager.isBlocked()) {
            remainingBlockMillis = TempUnlockManager.getRemainingBlockTimeMillis()
            delay(1000L)
        }
        if (isBlocked && !TempUnlockManager.isBlocked()) {
            isBlocked = false
            remainingBlockMillis = 0L
        }
    }
    
    // Generate new challenge
    fun generateNewChallenge() {
        randomString = (1..10).map { 
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789".random() 
        }.joinToString("")
        input = ""
        isError = false
        message = "Còn ${TOTAL_ATTEMPTS - successfulAttempts} lần để vào Settings"
    }
    
    // Check password
    fun checkPassword() {
        if (isBlocked) {
            context.popToast("Đang trong thời gian block!")
            return
        }
        
        if (input == randomString) {
            successfulAttempts++
            if (successfulAttempts >= TOTAL_ATTEMPTS) {
                focusManager.clearFocus()
                SP.lastAuthTime = System.currentTimeMillis()
                onSucceed()
            } else {
                generateNewChallenge()
            }
        } else {
            isError = true
            message = "Sai! Thử lại"
        }
    }
    
    // Initialize
    DisposableEffect(Unit) {
        generateNewChallenge()
        onDispose { }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()  // Scroll up when keyboard shows
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        
        // Version
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        Text(
            text = "v${packageInfo.versionName} • V2",
            fontSize = 12.sp,
            color = Color.Gray
        )
        
        Spacer(Modifier.height(16.dp))
        
        // ========== UNLOCK STATUS CARD ==========
        UnlockStatusCard(
            successfulAttempts = successfulAttempts,
            isUnlockActive = isUnlockActive,
            remainingTimeMillis = remainingUnlockMillis,
            isBlocked = isBlocked,
            blockRemainingMillis = remainingBlockMillis,
            onUnlock = { tier ->
                TempUnlockManager.activateWithTier(context, tier)
                isUnlockActive = true
                remainingUnlockMillis = TempUnlockManager.getRemainingTimeMillis()
                successfulAttempts = 0
                generateNewChallenge()
                context.popToast("Đã mở khóa ${tier.label}!")
            },
            onLockNow = { blockMinutes ->
                TempUnlockManager.deactivateWithBlock(context, blockMinutes)
                isUnlockActive = false
                remainingUnlockMillis = 0L
                if (blockMinutes > 0) {
                    isBlocked = true
                    remainingBlockMillis = TempUnlockManager.getRemainingBlockTimeMillis()
                    context.popToast("Đã khóa! Block $blockMinutes phút")
                } else {
                    context.popToast("Đã khóa!")
                }
            }
        )
        
        Spacer(Modifier.height(24.dp))
        
        // ========== PASSWORD INPUT SECTION ==========
        if (!isBlocked) {
            PasswordInputSection(
                randomString = randomString,
                input = input,
                onInputChange = { 
                    input = it
                    isError = false
                },
                isError = isError,
                message = message,
                onSubmit = { checkPassword() }
            )
        }
        
        Spacer(Modifier.height(24.dp))
        
        // ========== APP LIST SECTION ==========
        // Always show
        // Add: Always allowed
        // Remove: Only allowed after 50 attempts
        val canRemoveApps = successfulAttempts >= TOTAL_ATTEMPTS
        AppListSection(
            actionsEnabled = true,
            canRemove = canRemoveApps
        )
    }
}

/**
 * Password input section with random string display
 */
@Composable
private fun PasswordInputSection(
    randomString: String,
    input: String,
    onInputChange: (String) -> Unit,
    isError: Boolean,
    message: String,
    onSubmit: () -> Unit
) {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            
            // Random string to type
            Text(
                text = randomString,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Input field
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                label = { Text(context.getString(R.string.random_password_input_label)) },
                isError = isError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(Modifier.height(8.dp))
            
            // Message
            Text(
                text = message,
                fontSize = 12.sp,
                color = if (isError) MaterialTheme.colorScheme.error 
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(Modifier.height(12.dp))
            
            // Submit button
            Button(
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Xác nhận")
            }
        }
    }
}
