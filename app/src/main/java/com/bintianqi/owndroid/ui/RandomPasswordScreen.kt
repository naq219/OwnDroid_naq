package com.bintianqi.owndroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bintianqi.owndroid.R
import com.bintianqi.owndroid.Privilege
import com.bintianqi.owndroid.SP
import com.bintianqi.owndroid.showOperationResultToast
import com.bintianqi.owndroid.dpm.PackageNameTextField
import com.bintianqi.owndroid.dpm.isValidPackageName
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
object RandomPasswordScreen

private const val TOTAL_ATTEMPTS = 80

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
                // Mark last successful authentication time
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
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
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
                // Perform Suspend and Hide on the specified package
                val suspendOk = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    Privilege.DPM.setPackagesSuspended(Privilege.DAR, arrayOf(packageName), true).isEmpty()
                } else {
                    true
                }
                val hideOk = Privilege.DPM.setApplicationHidden(Privilege.DAR, packageName, true)
                val ok = suspendOk && hideOk
                context.showOperationResultToast(ok)
                if (ok) {
                    packageName = ""
                    focusManager.clearFocus()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = packageName.isValidPackageName
        ) {
            Text("Block app")
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
