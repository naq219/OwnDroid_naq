package com.bintianqi.owndroid.ui

import android.content.pm.PackageManager.NameNotFoundException
import android.os.UserManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bintianqi.owndroid.Privilege
import com.bintianqi.owndroid.R
import com.bintianqi.owndroid.SP
import com.bintianqi.owndroid.TempUnlockManager
import com.bintianqi.owndroid.AppConfig
import com.bintianqi.owndroid.dpm.PackageNameTextField
import com.bintianqi.owndroid.dpm.isValidPackageName
import com.bintianqi.owndroid.popToast
import com.bintianqi.owndroid.showOperationResultToast
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlin.random.Random


@Serializable
object RandomPasswordScreen

val MAX_UNLOCK_APK= 1
private val TOTAL_ATTEMPTS = AppConfig.ATTEMPTS_FOR_SETTINGS
private val TOTAL_ATTEMPTS_TEMP = AppConfig.ATTEMPTS_FOR_TEMP_UNLOCK

@Composable
fun RandomPasswordScreen(onSucceed: () -> Unit) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var randomString by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var successfulAttempts by remember { mutableIntStateOf(0) }
    var attemptsMessage by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    
    // Temp unlock state
    var isTempUnlockActive by remember { mutableStateOf(TempUnlockManager.isUnlockActive()) }
    var remainingTimeMillis by remember { mutableLongStateOf(TempUnlockManager.getRemainingTimeMillis()) }
    
    // Countdown timer effect
    LaunchedEffect(isTempUnlockActive) {
        while (isTempUnlockActive && TempUnlockManager.isUnlockActive()) {
            remainingTimeMillis = TempUnlockManager.getRemainingTimeMillis()
            delay(1000L)
        }
        if (isTempUnlockActive && !TempUnlockManager.isUnlockActive()) {
            isTempUnlockActive = false
            remainingTimeMillis = 0L
        }
    }

    fun generateNewChallenge() {
        randomString = generateRandomString(10)
        input = ""
        isError = false
        if (successfulAttempts < TOTAL_ATTEMPTS) {
            attemptsMessage = context.getString(R.string.random_password_attempts_remaining, TOTAL_ATTEMPTS - successfulAttempts)
        }
    }

    DisposableEffect(Unit) {
        generateNewChallenge()
        onDispose { }
    }

    fun checkPassword() {
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
            attemptsMessage = context.getString(R.string.random_password_incorrect_try_again)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        // Version display
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        Text(
            text = "v${packageInfo.versionName} (${packageInfo.longVersionCode})",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // ============= TEMP UNLOCK STATUS CARD =============
        TempUnlockStatusCard(
            successfulAttempts = successfulAttempts,
            isTempUnlockActive = isTempUnlockActive,
            remainingTimeMillis = remainingTimeMillis,
            onActivateUnlock = {
                TempUnlockManager.activateTempUnlock(context)
                isTempUnlockActive = true
                remainingTimeMillis = TempUnlockManager.getRemainingTimeMillis()
                // Reset attempts so user must re-enter passwords for next unlock
                successfulAttempts = 0
                context.popToast("Đã mở khóa tạm thời 10 phút!")
            }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // ============= RANDOM PASSWORD SECTION =============
        Text(
            text = context.getString(R.string.random_password_title),
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Text(
            text = randomString,
            fontSize = 24.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
                isError = false
                if (attemptsMessage == context.getString(R.string.random_password_incorrect_try_again)) {
                    attemptsMessage = context.getString(R.string.random_password_attempts_remaining, TOTAL_ATTEMPTS - successfulAttempts)
                }
            },
            label = { Text(context.getString(R.string.random_password_input_label)) },
            isError = isError,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { checkPassword() })
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = attemptsMessage,
            modifier = Modifier.padding(bottom = 16.dp),
            fontSize = 14.sp
        )

        Button(
            onClick = { checkPassword() },
            modifier = Modifier.fillMaxWidth(),
            enabled = input.isNotBlank()
        ) {
            Text(context.getString(R.string.random_password_verify_button))
        }

        // Extra controls on login: input package name and block app (Suspend + Hide)
        Spacer(modifier = Modifier.height(24.dp))
        PackageNameTextField(
            value = packageName,
            modifier = Modifier.padding(bottom = 8.dp),
            onValueChange = { packageName = it }
        )
        Button(
            onClick = {
                // Add to softlock list AND suspend immediately
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    // 1. Add to softlock list
                    TempUnlockManager.addToSoftlock(packageName)
                    
                    // 2. Suspend the app immediately
                    val suspendOk = TempUnlockManager.suspendApp(packageName)
                    
                    context.showOperationResultToast(suspendOk)
                    if (suspendOk) {
                        context.popToast("Đã thêm vào Softlock: $packageName")
                        packageName = ""
                        focusManager.clearFocus()
                    }
                } else {
                    context.popToast(R.string.unsupported)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = packageName.isValidPackageName
        ) {
            Text("Block app (Softlock)")
        }
        
        // Hardlock button - permanently lock app
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    // 1. Add to hardlock list
                    TempUnlockManager.addToHardlock(packageName)
                    
                    // 2. Suspend the app immediately
                    val suspendOk = TempUnlockManager.suspendApp(packageName)
                    
                    context.showOperationResultToast(suspendOk)
                    if (suspendOk) {
                        context.popToast("Đã thêm vào Hardlock (vĩnh viễn): $packageName")
                        packageName = ""
                        focusManager.clearFocus()
                    }
                } else {
                    context.popToast(R.string.unsupported)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFB71C1C) // Dark red for hardlock
            ),
            enabled = packageName.isValidPackageName
        ) {
            Text("🔒 Hardlock (vĩnh viễn)")
        }
        
        // ============= APP LIST MANAGEMENT SECTION =============
        // Always show, but actions only enabled after passing TOTAL_ATTEMPTS_TEMP
        Spacer(modifier = Modifier.height(24.dp))
        AppListManagementSection(actionsEnabled = successfulAttempts >= TOTAL_ATTEMPTS_TEMP)
        
        // Add Set VPN button
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        // Set the package as always-on VPN
                        val vpnPackage = AppConfig.VPN_PACKAGE
                        val allowlist: MutableSet<String?> = HashSet(AppConfig.VPN_ALLOWLIST)

                        Privilege.DPM.setAlwaysOnVpnPackage(Privilege.DAR, vpnPackage, false, allowlist)

                        // Block uninstall for VPN app
                        Privilege.DPM.setUninstallBlocked(Privilege.DAR, vpnPackage, true)
                        
                        // Disable user control
                        val current = Privilege.DPM.getUserControlDisabledPackages(Privilege.DAR)
                        if (!current.contains(vpnPackage)) {
                            Privilege.DPM.setUserControlDisabledPackages(
                                Privilege.DAR,
                                current.plus(vpnPackage)
                            )
                        }

                        Privilege.DPM.addUserRestriction(Privilege.DAR, UserManager.DISALLOW_CONFIG_VPN);
                        Privilege.DPM.addUserRestriction(Privilege.DAR, UserManager.DISALLOW_CONFIG_PRIVATE_DNS);
                        // Chặn xóa dữ liệu ứng dụng bằng cách đặt hạn chế
                        

                        //val restrictions = Bundle()
                        // restrictions.putBoolean("block_uninstall", true)
                        // restrictions.putBoolean("block_clear_data", true)
                        // Privilege.DPM.setApplicationRestrictions(Privilege.DAR, packageName, restrictions)
                        
                        context.showOperationResultToast(true)
                        packageName = ""
                        focusManager.clearFocus()
                    } else {
                        context.popToast(R.string.unsupported)
                    }
                } catch(e: UnsupportedOperationException) {
                    e.printStackTrace()
                    context.popToast(R.string.unsupported)
                } catch(e: NameNotFoundException) {
                    e.printStackTrace()
                    context.popToast(R.string.not_installed)
                } catch(e: Exception) {
                    e.printStackTrace()
                    context.popToast("Error: ${e.message}")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = true
        ) {
            Text("Set VPN")
        }

         Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                       
                          Privilege.DPM.addUserRestriction(Privilege.DAR, UserManager.DISALLOW_INSTALL_APPS);
                    } else {
                        context.popToast(R.string.unsupported)
                    }
                } catch(e: UnsupportedOperationException) {
                    e.printStackTrace()
                    context.popToast(R.string.unsupported)
                } catch(e: NameNotFoundException) {
                    e.printStackTrace()
                    context.popToast(R.string.not_installed)
                } catch(e: Exception) {
                    e.printStackTrace()
                    context.popToast("Error: ${e.message}")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = true
        ) {
            Text("Block install apps")
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {

                        if(successfulAttempts<MAX_UNLOCK_APK){
                            context.popToast("Chưa đủ, "+(MAX_UNLOCK_APK-successfulAttempts)+" lần thử")
                            return@Button;
                        }
                      
                        Privilege.DPM.clearUserRestriction(Privilege.DAR, UserManager.DISALLOW_INSTALL_APPS);
                    
                        context.showOperationResultToast(true)
                        packageName = ""
                        focusManager.clearFocus()
                    } else {
                        context.popToast(R.string.unsupported)
                    }
                } catch(e: UnsupportedOperationException) {
                    e.printStackTrace()
                    context.popToast(R.string.unsupported)
                } catch(e: NameNotFoundException) {
                    e.printStackTrace()
                    context.popToast(R.string.not_installed)
                } catch(e: Exception) {
                    e.printStackTrace()
                    context.popToast("Error: ${e.message}")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = true 

        ) {
            Text("UNBLOCK install apps")
        }


    }
}

private fun generateRandomString(length: Int): String {
    val allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    val random = Random
    return buildString {
        for (i in 1..length) {
            append(allowedChars[random.nextInt(allowedChars.length)])
        }
    }
}

/**
 * Status card showing temp unlock state with countdown timer
 */
@Composable
private fun TempUnlockStatusCard(
    successfulAttempts: Int,
    isTempUnlockActive: Boolean,
    remainingTimeMillis: Long,
    onActivateUnlock: () -> Unit
) {
    val canUnlock = successfulAttempts >= TOTAL_ATTEMPTS_TEMP
    val isNightMode = TempUnlockManager.isNightMode()
    val nightModeMinutesRemaining = TempUnlockManager.getMinutesUntilNightModeEnds()
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isNightMode && !isTempUnlockActive -> Color(0xFF1A237E).copy(alpha = 0.15f) // Dark blue tint for night
                isTempUnlockActive -> Color(0xFF1B5E20).copy(alpha = 0.15f) // Green tint
                canUnlock -> Color(0xFFE65100).copy(alpha = 0.15f) // Orange tint
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                // UNLOCKED STATE - Show countdown (even during night mode, if already unlocked)
                isTempUnlockActive -> {
                    val minutes = (remainingTimeMillis / 1000 / 60).toInt()
                    val seconds = ((remainingTimeMillis / 1000) % 60).toInt()
                    val progress = remainingTimeMillis.toFloat() / (10 * 60 * 1000)
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.lock_open_fill0),
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "ĐÃ MỞ KHÓA TẠM THỜI",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                    
                    Text(
                        text = "⏱️ Còn lại: ${minutes}:${seconds.toString().padStart(2, '0')}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = Color(0xFF4CAF50),
                        trackColor = Color(0xFFE0E0E0)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Không chặn app • Không chặn cài đặt • VPN tắt",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                
                // NIGHT MODE - Block unlock
                isNightMode -> {
                    val hours = nightModeMinutesRemaining / 60
                    val mins = nightModeMinutesRemaining % 60
                    
                    Text(
                        text = "🌙",
                        fontSize = 32.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Text(
                        text = "CHẾ ĐỘ ĐÊM",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF3F51B5)
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "22:00 - 07:00",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Còn ${hours}h ${mins}m",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF3F51B5)
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Không thể mở khóa tạm thời",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // CAN UNLOCK - Show unlock button
                canUnlock -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.lock_fill0),
                            contentDescription = null,
                            tint = Color(0xFFE65100),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "✓ Đủ $TOTAL_ATTEMPTS_TEMP lần!",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100)
                        )
                    }
                    
                    Button(
                        onClick = onActivateUnlock,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF6D00)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.lock_open_fill0),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "TẠM MỞ KHÓA ${AppConfig.TEMP_UNLOCK_DURATION_MINUTES} PHÚT",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
                
                // LOCKED - Show progress
                else -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.lock_fill0),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Nhập đúng: $successfulAttempts / $TOTAL_ATTEMPTS_TEMP",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    LinearProgressIndicator(
                        progress = { successfulAttempts.toFloat() / TOTAL_ATTEMPTS_TEMP },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Nhập đúng $TOTAL_ATTEMPTS_TEMP lần để mở khóa tạm thời",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/**
 * Section for managing Hardlock and Softlock app lists
 * Shows current blocked apps and allows multi-select to add new ones
 * @param actionsEnabled If false, shows lists but disables action buttons
 */
@Composable
private fun AppListManagementSection(actionsEnabled: Boolean = true) {
    val context = LocalContext.current
    var softlockApps by remember { mutableStateOf(TempUnlockManager.getSoftlockApps()) }
    var hardlockApps by remember { mutableStateOf(TempUnlockManager.getHardlockApps()) }
    var showAppPicker by remember { mutableStateOf(false) }
    var selectedApps by remember { mutableStateOf(setOf<String>()) }
    
    fun refreshLists() {
        softlockApps = TempUnlockManager.getSoftlockApps()
        hardlockApps = TempUnlockManager.getHardlockApps()
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "⚙️ Quản lý danh sách khóa",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            // Locked message when actions disabled
            if (!actionsEnabled) {
                Text(
                    text = "🔒 Nhập mật khẩu để thao tác",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            // ========== ADD MULTIPLE APPS BUTTON ==========
            Button(
                onClick = { showAppPicker = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                Text("📱 Chọn nhiều app để block", fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // ========== SOFTLOCK LIST ==========
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE3F2FD).copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "🔓 Softlock: ${softlockApps.size} apps",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    if (softlockApps.isEmpty()) {
                        Text("(Chưa có app nào)", fontSize = 12.sp, color = Color.Gray)
                    } else {
                        softlockApps.forEach { pkg ->
                            BlockedAppRow(
                                packageName = pkg,
                                onMoveClick = {
                                    TempUnlockManager.moveToHardlock(pkg)
                                    refreshLists()
                                    context.popToast("→ Hardlock: $pkg")
                                },
                                onRemoveClick = {
                                    TempUnlockManager.removeFromSoftlock(pkg)
                                    TempUnlockManager.unsuspendApp(pkg)
                                    refreshLists()
                                    context.popToast("Đã gỡ: $pkg")
                                },
                                moveButtonText = "→🔒",
                                moveButtonColor = Color(0xFFB71C1C),
                                enabled = actionsEnabled
                            )
                        }
                    }
                }
            }
            
            // ========== HARDLOCK LIST ==========
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFEBEE).copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "🔒 Hardlock: ${hardlockApps.size} apps",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    if (hardlockApps.isEmpty()) {
                        Text("(Chưa có app nào)", fontSize = 12.sp, color = Color.Gray)
                    } else {
                        hardlockApps.forEach { pkg ->
                            BlockedAppRow(
                                packageName = pkg,
                                onMoveClick = {
                                    TempUnlockManager.moveToSoftlock(pkg)
                                    refreshLists()
                                    context.popToast("→ Softlock: $pkg")
                                },
                                onRemoveClick = {
                                    TempUnlockManager.removeFromHardlock(pkg)
                                    TempUnlockManager.unsuspendApp(pkg)
                                    refreshLists()
                                    context.popToast("Đã gỡ: $pkg")
                                },
                                moveButtonText = "→🔓",
                                moveButtonColor = Color(0xFF1976D2),
                                enabled = actionsEnabled
                            )
                        }
                    }
                }
            }
        }
    }
    
    // ========== MULTI-SELECT APP PICKER DIALOG ==========
    if (showAppPicker) {
        MultiSelectAppPickerDialog(
            selectedApps = selectedApps,
            onSelectionChange = { selectedApps = it },
            onDismiss = { 
                showAppPicker = false 
                selectedApps = emptySet()
            },
            onBlockAsSoftlock = {
                selectedApps.forEach { pkg ->
                    TempUnlockManager.addToSoftlock(pkg)
                    TempUnlockManager.suspendApp(pkg)
                }
                context.popToast("Đã thêm ${selectedApps.size} apps vào Softlock")
                refreshLists()
                showAppPicker = false
                selectedApps = emptySet()
            },
            onBlockAsHardlock = {
                selectedApps.forEach { pkg ->
                    TempUnlockManager.addToHardlock(pkg)
                    TempUnlockManager.suspendApp(pkg)
                }
                context.popToast("Đã thêm ${selectedApps.size} apps vào Hardlock")
                refreshLists()
                showAppPicker = false
                selectedApps = emptySet()
            }
        )
    }
}

/**
 * Row displaying a blocked app with move and remove buttons
 */
@Composable
private fun BlockedAppRow(
    packageName: String,
    onMoveClick: () -> Unit,
    onRemoveClick: () -> Unit,
    moveButtonText: String,
    moveButtonColor: Color,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = packageName,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f),
            color = if (enabled) Color.Unspecified else Color.Gray
        )
        Row {
            Button(
                onClick = onMoveClick,
                modifier = Modifier.height(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (enabled) moveButtonColor else Color.LightGray
                ),
                enabled = enabled
            ) {
                Text(moveButtonText, fontSize = 10.sp)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Button(
                onClick = onRemoveClick,
                modifier = Modifier.height(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (enabled) Color.Gray else Color.LightGray
                ),
                enabled = enabled
            ) {
                Text("✕", fontSize = 10.sp)
            }
        }
    }
}

/**
 * Dialog for multi-selecting apps to block
 */
@Composable
private fun MultiSelectAppPickerDialog(
    selectedApps: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
    onBlockAsSoftlock: () -> Unit,
    onBlockAsHardlock: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    var searchQuery by remember { mutableStateOf("") }
    var installedApps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Load installed apps
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val apps = pm.getInstalledApplications(
                android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS or 
                android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
            ).map { appInfo ->
                val label = try { appInfo.loadLabel(pm).toString() } catch (e: Exception) { appInfo.packageName }
                appInfo.packageName to label
            }.sortedBy { it.second.lowercase() }
            installedApps = apps
            isLoading = false
        }
    }
    
    val filteredApps = installedApps.filter { (pkg, label) ->
        searchQuery.isEmpty() || 
        pkg.contains(searchQuery, ignoreCase = true) || 
        label.contains(searchQuery, ignoreCase = true)
    }
    
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Text(
                    text = "Chọn apps để block (${selectedApps.size} đã chọn)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Search
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Tìm kiếm...") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true
                )
                
                // App list with checkboxes
                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.weight(1f)
                    ) {
                        items(filteredApps.size) { index ->
                            val (pkg, label) = filteredApps[index]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val newSelection = if (selectedApps.contains(pkg)) {
                                            selectedApps - pkg
                                        } else {
                                            selectedApps + pkg
                                        }
                                        onSelectionChange(newSelection)
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.Checkbox(
                                    checked = selectedApps.contains(pkg),
                                    onCheckedChange = { checked ->
                                        val newSelection = if (checked) {
                                            selectedApps + pkg
                                        } else {
                                            selectedApps - pkg
                                        }
                                        onSelectionChange(newSelection)
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text(text = pkg, fontSize = 10.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Action buttons
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onBlockAsSoftlock,
                        modifier = Modifier.weight(1f),
                        enabled = selectedApps.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                    ) {
                        Text("Softlock", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onBlockAsHardlock,
                        modifier = Modifier.weight(1f),
                        enabled = selectedApps.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
                    ) {
                        Text("Hardlock", fontSize = 12.sp)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                ) {
                    Text("Đóng")
                }
            }
        }
    }
}

