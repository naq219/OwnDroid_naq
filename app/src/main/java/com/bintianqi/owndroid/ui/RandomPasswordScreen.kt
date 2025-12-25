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
private const val TOTAL_ATTEMPTS = 50
private const val TOTAL_ATTEMPTS_TEMP = 3

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
                // Perform Suspend only (don't hide)
                val suspendOk = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    Privilege.DPM.setPackagesSuspended(Privilege.DAR, arrayOf(packageName), true).isEmpty()
                } else {
                    false
                }
                context.showOperationResultToast(suspendOk)
                if (suspendOk) {
                    packageName = ""
                    focusManager.clearFocus()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = packageName.isValidPackageName
        ) {
            Text("Block app")
        }
        
        // Add Set VPN button
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        // Set the package as always-on VPN
                        var naqdns="naq.dns"
                        val allowlist: MutableSet<String?> = HashSet<String?>()
                        allowlist.add("com.facebook.adsmanager")
                        allowlist.add("com.facebook.orca")

                        allowlist.add("com.facebook.pages.app")
//                        allowlist.add("com.facebook.orca")
//                        allowlist.add("com.facebook.orca")

                        Privilege.DPM.setAlwaysOnVpnPackage(Privilege.DAR, naqdns, false,allowlist)


                        // Chặn gỡ cài đặt ứng dụng
                        
                       // val bun1 = android.os.Bundle()
                        //bun1.putBoolean("block_uninstall", true)
                        //bun1.putBoolean("block_clear_data", true)
                        //Privilege.DPM.setApplicationRestrictions(Privilege.DAR, packageName, bun1)
                        Privilege.DPM.setUninstallBlocked(Privilege.DAR, naqdns, true)
                       // Privilege.DPM.addUserRestriction(Privilege.DAR, UserManager.DISALLOW_INSTALL_APPS);
                       //Privilege.DPM.clearUserRestriction(Privilege.DAR, UserManager.DISALLOW_INSTALL_APPS);
                        val current = Privilege.DPM.getUserControlDisabledPackages(Privilege.DAR)
                        if (!current.contains(naqdns)) {
                            Privilege.DPM.setUserControlDisabledPackages(
                                Privilege.DAR,
                                current.plus(naqdns)
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
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
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
                // UNLOCKED STATE - Show countdown
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
                            text = "TẠM MỞ KHÓA 10 PHÚT",
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
