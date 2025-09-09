package com.bintianqi.owndroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.window.Dialog
import com.bintianqi.owndroid.R

private const val TOTAL_ATTEMPTS_DIALOG = 30

@Composable
fun RandomPasswordDialog(onSucceed: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismiss) {
        Card(Modifier.padding(16.dp)) {
            RandomPasswordDialogBody(onSucceed)
        }
    }
}

@Composable
private fun RandomPasswordDialogBody(onSucceed: () -> Unit) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var randomString by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var successfulAttempts by remember { mutableIntStateOf(0) }
    var attemptsMessage by remember { mutableStateOf("") }

    fun generateNewChallenge() {
        randomString = generateRandomString(10)
        input = ""
        isError = false
        attemptsMessage = context.getString(R.string.random_password_attempts_remaining, TOTAL_ATTEMPTS_DIALOG - successfulAttempts)
    }

    DisposableEffect(Unit) {
        generateNewChallenge()
        onDispose { }
    }

    fun checkPassword() {
        if (input == randomString) {
            successfulAttempts++
            if (successfulAttempts >= TOTAL_ATTEMPTS_DIALOG) {
                focusManager.clearFocus()
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
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = context.getString(R.string.random_password_title),
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            text = randomString,
            fontSize = 22.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
                isError = false
                if (attemptsMessage == context.getString(R.string.random_password_incorrect_try_again)) {
                    attemptsMessage = context.getString(R.string.random_password_attempts_remaining, TOTAL_ATTEMPTS_DIALOG - successfulAttempts)
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
            modifier = Modifier.padding(bottom = 12.dp),
            fontSize = 14.sp
        )
        Button(
            onClick = { checkPassword() },
            modifier = Modifier.fillMaxWidth(),
            enabled = input.isNotBlank()
        ) {
            Text(context.getString(R.string.random_password_verify_button))
        }
    }
}

@Composable
fun RandomChallengeIconButton(onAuthenticatedClick: () -> Unit, content: @Composable () -> Unit) {
    var show by remember { mutableStateOf(false) }
    IconButton({ show = true }) {
        content()
    }
    if (show) {
        RandomPasswordDialog(onSucceed = { show = false; onAuthenticatedClick() }, onDismiss = { show = false })
    }
}

@Composable
fun rememberRandomChallengeLauncher(): ((action: () -> Unit) -> Unit) {
    var show by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    if (show) {
        RandomPasswordDialog(
            onSucceed = { val p = pending; show = false; pending = null; p?.invoke() },
            onDismiss = { show = false; pending = null }
        )
    }
    return { action -> pending = action; show = true }
}

private fun generateRandomString(length: Int): String {
    val allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    return buildString {
        repeat(length) { append(allowedChars.random()) }
    }
}
