package com.bintianqi.owndroid.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bintianqi.owndroid.R
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
object RandomPasswordScreen

@Composable
fun RandomPasswordScreen(onSucceed: () -> Unit) {
    val focusMgr = LocalFocusManager.current
    var randomString by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    // Tạo chuỗi ngẫu nhiên khi màn hình được tạo
    DisposableEffect(Unit) {
        randomString = generateRandomString(3)
        onDispose { }
    }

    fun checkPassword() {
        if (input == randomString) {
            focusMgr.clearFocus()
            onSucceed()
        } else {
            isError = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = randomString,
            modifier = Modifier.padding(top = 50.dp, bottom = 24.dp)
        )

        OutlinedTextField(
            value = input,
            onValueChange = { input = it; isError = false },
             label = { Text("Enter random string") },
            isError = isError,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { checkPassword() })
        )

        Spacer(modifier = Modifier.padding(vertical = 16.dp))

        Button(
            onClick = { checkPassword() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Xác Minh")
        }
    }
}

/**
 * Tạo chuỗi ngẫu nhiên với độ dài cho trước
 * Chuỗi bao gồm chữ cái viết hoa và chữ số
 * Cứ mỗi 20 ký tự sẽ có một dấu gạch dưới (_)
 */
private fun generateRandomString(length: Int): String {
    val allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    val random = Random

    return buildString {
        for (i in 1..length) {
            // Thêm dấu gạch dưới sau mỗi 20 ký tự (trừ ký tự cuối cùng)
            if (i % 20 == 0 && i != length) {
                append('_')
            } else {
                append(allowedChars[random.nextInt(allowedChars.length)])
            }
        }
    }
}