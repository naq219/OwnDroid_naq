package com.bintianqi.owndroid.ui

import android.util.Log
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
        
        if (input.equals(randomString, ignoreCase = true)) {
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

    // First run bypass + Default config
    LaunchedEffect(Unit) {
        if (SP.lastAuthTime == 0L) {
            // Maxwell: Apply default config on first run
            
            // 1. Soft Block Defaults
            if (TempUnlockManager.getSoftlockApps().isEmpty()) {
                val defaults = AppConfig.DEFAULT_SOFT_BLOCK_APPS
                Log.d("RandomPasswordScreenV2", "First Run: Applying default Soft Block list: $defaults")
                // Save to SP
                defaults.forEach { TempUnlockManager.addToSoftlock(it) }
                // Apply Suspend immediately
                 if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    com.bintianqi.owndroid.Privilege.DPM.setPackagesSuspended(
                        com.bintianqi.owndroid.Privilege.DAR, 
                        defaults.toTypedArray(), 
                        true
                    )
                 }
            }
            
            // 2. Hard Block Defaults (Empty by default but safe to handle)
            if (TempUnlockManager.getHardlockApps().isEmpty()) {
                val defaults = AppConfig.DEFAULT_HARD_BLOCK_APPS
                Log.d("RandomPasswordScreenV2", "First Run: Applying default Hard Block list: $defaults")
                // Save to SP
                defaults.forEach { TempUnlockManager.addToHardlock(it) }
                // Apply Suspend + Hide immediately
                 if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    com.bintianqi.owndroid.Privilege.DPM.setPackagesSuspended(
                        com.bintianqi.owndroid.Privilege.DAR, 
                        defaults.toTypedArray(), 
                        true
                    )
                    defaults.forEach { pkg ->
                        com.bintianqi.owndroid.Privilege.DPM.setApplicationHidden(
                            com.bintianqi.owndroid.Privilege.DAR, 
                            pkg, 
                            true
                        )
                    }
                 }
            }
            
            // Bypass logic REMOVED as per user request (Must login even on first run)
            // successfulAttempts = TOTAL_ATTEMPTS
            // SP.lastAuthTime = System.currentTimeMillis()
            // onSucceed()
        }
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
            onLockNow = { _ ->
                // Use stored block minutes from the tier used to unlock
                // The UI argument is 0 because attempts are reset, so we rely on SP
                val actualBlockMinutes = SP.lastUsedBlockMinutes
                TempUnlockManager.deactivateWithBlock(context, actualBlockMinutes)
                isUnlockActive = false
                remainingUnlockMillis = 0L
                if (actualBlockMinutes > 0) {
                    isBlocked = true
                    remainingBlockMillis = TempUnlockManager.getRemainingBlockTimeMillis()
                    context.popToast("Đã khóa! Block $actualBlockMinutes phút")
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
        
        Spacer(Modifier.height(24.dp))
        
        // TEST BUTTON: Enable Install Apps
        Button(
            onClick = { 
                try {
                    com.bintianqi.owndroid.Privilege.DPM.clearUserRestriction(
                        com.bintianqi.owndroid.Privilege.DAR, 
                        android.os.UserManager.DISALLOW_INSTALL_APPS
                    )
                    context.popToast("Allowed Install Apps")
                } catch (e: Exception) {
                    context.popToast("Failed: ${e.message}")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
        ) {
            Text("TEST: Cho phép cài ứng dụng")
        }
        
        Spacer(Modifier.height(12.dp))
        
        // TEST BUTTON: Block Date/Time & Allow Install Apps & Protect Apps
        Button(
            onClick = { 
                try {
                    val dpm = com.bintianqi.owndroid.Privilege.DPM
                    val admin = com.bintianqi.owndroid.Privilege.DAR
                    val myPackage = context.packageName
                    val dnsPackage = "naq.dns"

                    // 1. Block Date/Time
                    dpm.addUserRestriction(admin, android.os.UserManager.DISALLOW_CONFIG_DATE_TIME)
                    
                    // 2. Allow Install Apps
                    dpm.clearUserRestriction(admin, android.os.UserManager.DISALLOW_INSTALL_APPS)
                    
                    // 3. Block Uninstall
                    dpm.setUninstallBlocked(admin, dnsPackage, true)
                   // dpm.setUninstallBlocked(admin, myPackage, true)

                   Log.i("RandomPasswordScreenV2", "setUninstallBlocked: "+myPackage)
                    
                    // 4. Block Clear Data (User Control Disabled)
                    // Get current list so we don't remove existing ones (optional but safer)
                    val currentProtected = dpm.getUserControlDisabledPackages(admin).toMutableSet()
                    currentProtected.add(dnsPackage)
                    currentProtected.add(myPackage)
                    dpm.setUserControlDisabledPackages(admin, currentProtected.toList())

                    context.popToast("Đã chặn sửa giờ, gỡ/clear data + Cho phép cài App")
                } catch (e: Exception) {
                    context.popToast("Failed: ${e.message}")
                    e.printStackTrace()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("TEST: Chặn sửa giờ + Bảo vệ App")
        }
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
