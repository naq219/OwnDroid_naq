package com.bintianqi.owndroid.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bintianqi.owndroid.R
import com.bintianqi.owndroid.TwoFactorManager
import com.bintianqi.owndroid.popToast
import kotlinx.serialization.Serializable

@Serializable
object TwoFactorManagement

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwoFactorManagementScreen(
    onOpenHome: () -> Unit,
    onRelocked: () -> Unit
) {
    val context = LocalContext.current
    var secretKey by remember { mutableStateOf(TwoFactorManager.getSecretKey()) }
    var backupCodes by remember { mutableStateOf(TwoFactorManager.getBackupCodes()) }

    var showRelockConfirmDialog by remember { mutableStateOf(false) }
    var showNewCodesConfirmDialog by remember { mutableStateOf(false) }
    var showEditSecretDialog by remember { mutableStateOf(false) }
    var showRandomSecretConfirmDialog by remember { mutableStateOf(false) }
    var customSecretInput by remember { mutableStateOf("") }
    var customSecretError by remember { mutableStateOf<String?>(null) }

    fun copyToClipboard(text: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        context.popToast("Đã sao chép $label!")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quản lý mở khoá 2FA", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ========== CARD TRẠNG THÁI MỞ KHOÁ VĨNH VIỄN ==========
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2E7D32).copy(alpha = 0.15f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = painterResource(R.drawable.lock_open_fill0),
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "ĐÃ MỞ KHOÁ VĨNH VIỄN BẰNG 2FA",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF2E7D32)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Toàn bộ ứng dụng đã được mở khoá. Các giới hạn DPM và worker tự động khoá lại đã bị huỷ.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ========== ACTION BUTTONS ==========
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Button Khoá lại
                Button(
                    onClick = { showRelockConfirmDialog = true },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(painterResource(R.drawable.lock_fill0), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Khoá trở lại", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                // Button Vào cấu hình
                Button(
                    onClick = onOpenHome,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(painterResource(R.drawable.settings_fill0), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Vào cấu hình", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(20.dp))

            // ========== SECRET KEY SECTION ==========
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "🔑 2FA Secret Key",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Thêm khoá này vào Google Authenticator hoặc 2FAS (Time-based, 6 chữ số, 30 giây):",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))

                    // Textbox Secret Key
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = secretKey,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            letterSpacing = 2.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { copyToClipboard(secretKey, "Secret Key") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(painterResource(R.drawable.content_copy_fill0), null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy Key", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val uri = TwoFactorManager.getOtpAuthUri()
                                copyToClipboard(uri, "OTPAuth URL")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(painterResource(R.drawable.open_in_new), null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy OTPAuth", fontSize = 12.sp)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                customSecretInput = secretKey
                                customSecretError = null
                                showEditSecretDialog = true
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(painterResource(R.drawable.edit_fill0), null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Nhập Key mới", fontSize = 12.sp)
                        }

                        Button(
                            onClick = { showRandomSecretConfirmDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Icon(painterResource(R.drawable.refresh_fill0), null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Tạo Key mới", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ========== BACKUP CODES SECTION ==========
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🛡️ Mã dự phòng (${backupCodes.size} mã còn lại)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Mỗi mã dự phòng dùng được 1 lần để mở khoá khi không có Authenticator:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))

                    // Lưới hiển thị 2 cột mã dự phòng
                    val chunked = backupCodes.chunked(2)
                    chunked.forEach { rowCodes ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowCodes.forEach { code ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = code,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                            if (rowCodes.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val text = backupCodes.joinToString("\n")
                                copyToClipboard(text, "Danh sách mã dự phòng")
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(painterResource(R.drawable.content_copy_fill0), null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy toàn bộ", fontSize = 12.sp)
                        }

                        Button(
                            onClick = { showNewCodesConfirmDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Icon(painterResource(R.drawable.refresh_fill0), null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Tạo mới mã", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // ========== CONFIRM RELOCK DIALOG ==========
    if (showRelockConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRelockConfirmDialog = false },
            title = { Text("Xác nhận khoá lại", fontWeight = FontWeight.Bold) },
            text = { Text("Thiết bị sẽ được áp dụng lại toàn bộ hạn chế và trở về màn hình khoá. Bạn có chắc không?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRelockConfirmDialog = false
                        TwoFactorManager.relock(context)
                        context.popToast("Đã khoá trở lại!")
                        onRelocked()
                    }
                ) {
                    Text("Khoá ngay", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRelockConfirmDialog = false }) {
                    Text("Huỷ")
                }
            }
        )
    }

    // ========== CONFIRM NEW BACKUP CODES DIALOG ==========
    if (showNewCodesConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showNewCodesConfirmDialog = false },
            title = { Text("Tạo mới mã dự phòng", fontWeight = FontWeight.Bold) },
            text = { Text("Toàn bộ các mã dự phòng cũ sẽ bị vô hiệu hoá và thay bằng 16 mã mới. Bạn có chắc không?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNewCodesConfirmDialog = false
                        backupCodes = TwoFactorManager.generateNewBackupCodes()
                        context.popToast("Đã tạo mới 16 mã dự phòng!")
                    }
                ) {
                    Text("Tạo mới", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewCodesConfirmDialog = false }) {
                    Text("Huỷ")
                }
            }
        )
    }

    // ========== CONFIRM RANDOM SECRET KEY DIALOG ==========
    if (showRandomSecretConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRandomSecretConfirmDialog = false },
            title = { Text("Tạo mới Secret Key", fontWeight = FontWeight.Bold) },
            text = {
                Text("Khoá 2FA hiện tại sẽ bị thay bằng một khoá mới ngẫu nhiên. Bạn sẽ cần cấu hình lại mã này vào ứng dụng Authenticator. Bạn có chắc không?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRandomSecretConfirmDialog = false
                        secretKey = TwoFactorManager.generateNewSecretKey()
                        context.popToast("Đã tạo mới Secret Key thành công!")
                    }
                ) {
                    Text("Tạo mới", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRandomSecretConfirmDialog = false }) {
                    Text("Huỷ")
                }
            }
        )
    }

    // ========== EDIT CUSTOM SECRET KEY DIALOG ==========
    if (showEditSecretDialog) {
        AlertDialog(
            onDismissRequest = { showEditSecretDialog = false },
            title = { Text("Đổi Secret Key 2FA", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Nhập khoá bí mật Base32 tuỳ chỉnh của bạn (chỉ gồm các chữ cái A-Z và chữ số 2-7, tối thiểu 16 ký tự):",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customSecretInput,
                        onValueChange = {
                            customSecretInput = it.filter { char -> !char.isWhitespace() }.uppercase()
                            customSecretError = null
                        },
                        label = { Text("Secret Key (Base32)") },
                        isError = customSecretError != null,
                        supportingText = customSecretError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cleaned = customSecretInput.trim().uppercase()
                        if (TwoFactorManager.isValidBase32(cleaned)) {
                            TwoFactorManager.setSecretKey(cleaned)
                            secretKey = TwoFactorManager.getSecretKey()
                            showEditSecretDialog = false
                            context.popToast("Đã lưu Secret Key mới!")
                        } else {
                            customSecretError = "Khoá không hợp lệ! Chỉ dùng A-Z, 2-7 và tối thiểu 16 ký tự."
                        }
                    }
                ) {
                    Text("Lưu", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditSecretDialog = false }) {
                    Text("Huỷ")
                }
            }
        )
    }
}
