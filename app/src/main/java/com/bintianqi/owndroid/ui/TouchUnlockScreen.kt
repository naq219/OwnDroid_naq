package com.bintianqi.owndroid.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bintianqi.owndroid.R
import com.bintianqi.owndroid.TempUnlockManager
import com.bintianqi.owndroid.popToast
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

@Serializable
object TouchUnlock

enum class TouchChallengeState {
    RUNNING,
    FAILED,
    UNLOCKED
}

@Composable
fun TouchUnlockScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Giữ màn hình luôn sáng trong lúc đang ở màn hình này
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // State của thử thách
    var state by remember { mutableStateOf(TouchChallengeState.RUNNING) }
    var challengeStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var lastTouchTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    var inactiveRemainingMillis by remember { mutableLongStateOf(30_000L) }
    var touchCount by remember { mutableIntStateOf(0) }
    var failureReason by remember { mutableStateOf("") }
    var unlockRemainingMillis by remember { mutableLongStateOf(0L) }
    var unlockedMinutes by remember { mutableLongStateOf(0L) }

    // Kiểm tra nếu hệ thống đã sẵn có unlock đang active
    LaunchedEffect(Unit) {
        if (TempUnlockManager.isUnlockActive()) {
            state = TouchChallengeState.UNLOCKED
            unlockRemainingMillis = TempUnlockManager.getRemainingTimeMillis()
        }
    }

    // Lắng nghe Lifecycle: nếu đang RUNNING mà Activity bị PAUSE -> THUA NGAY LẬP TỨC
    DisposableEffect(lifecycleOwner, state) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                if (state == TouchChallengeState.RUNNING) {
                    state = TouchChallengeState.FAILED
                    failureReason = "Bạn đã rời khỏi màn hình hoặc ứng dụng bị tạm dừng (Paused)!"
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Xử lý chạm màn hình (ghi nhận chạm từ bất kỳ đâu)
    fun registerTouch() {
        if (state == TouchChallengeState.RUNNING) {
            lastTouchTime = System.currentTimeMillis()
            inactiveRemainingMillis = 30_000L
            touchCount++
        }
    }

    // Vòng lặp timer
    LaunchedEffect(state) {
        if (state == TouchChallengeState.RUNNING) {
            while (state == TouchChallengeState.RUNNING) {
                val now = System.currentTimeMillis()
                elapsedMillis = now - challengeStartTime
                val inactive = now - lastTouchTime
                val rem = 30_000L - inactive

                if (rem <= 0) {
                    inactiveRemainingMillis = 0L
                    failureReason = "Bạn đã không chạm vào màn hình trong 30 giây!"
                    state = TouchChallengeState.FAILED
                    break
                } else {
                    inactiveRemainingMillis = rem
                }
                delay(50L)
            }
        } else if (state == TouchChallengeState.UNLOCKED) {
            while (state == TouchChallengeState.UNLOCKED) {
                unlockRemainingMillis = TempUnlockManager.getRemainingTimeMillis()
                if (unlockRemainingMillis <= 0L || !TempUnlockManager.isUnlockActive()) {
                    state = TouchChallengeState.RUNNING
                    challengeStartTime = System.currentTimeMillis()
                    lastTouchTime = System.currentTimeMillis()
                    touchCount = 0
                    break
                }
                delay(1000L)
            }
        }
    }

    // Mốc thời gian
    val reached5Min = elapsedMillis >= 5 * 60 * 1000L
    val reached10Min = elapsedMillis >= 10 * 60 * 1000L

    // Định dạng thời gian
    fun formatMMSS(millis: Long): String {
        val totalSec = millis / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%02d:%02d".format(min, sec)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = when (state) {
                        TouchChallengeState.RUNNING -> listOf(
                            Color(0xFF0F172A),
                            Color(0xFF1E293B)
                        )
                        TouchChallengeState.FAILED -> listOf(
                            Color(0xFF450A0A),
                            Color(0xFF1C1917)
                        )
                        TouchChallengeState.UNLOCKED -> listOf(
                            Color(0xFF064E3B),
                            Color(0xFF0F172A)
                        )
                    }
                )
            )
            // Lắng nghe mọi tương tác chạm trên toàn màn hình mà không chặn click của button
            .pointerInput(state) {
                if (state == TouchChallengeState.RUNNING) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press) {
                                registerTouch()
                            }
                        }
                    }
                }
            }
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ========== TOP BAR ==========
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        painter = painterResource(R.drawable.close_fill0),
                        contentDescription = "Đóng",
                        tint = Color.White
                    )
                }

                Text(
                    text = "TOUCH UNLOCK",
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )

                // Cân đối layout
                Spacer(Modifier.size(48.dp))
            }

            Spacer(Modifier.height(16.dp))

            // ========== NỘI DUNG THEO TRẠNG THÁI ==========
            when (state) {
                // ------------------ 1. TRẠNG THÁI RUNNING ------------------
                TouchChallengeState.RUNNING -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Thẻ đếm ngược 30s không chạm
                        val inactiveSeconds = (inactiveRemainingMillis / 1000f)
                        val isDanger = inactiveRemainingMillis <= 5000L

                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDanger) Color(0xFFEF4444).copy(alpha = 0.25f)
                                else Color(0xFF3B82F6).copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 18.dp, horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (isDanger) "⚠️ CHẠM NGAY! SẮP HẾT GIỜ" else "HÃY CHẠM ĐỂ RESET 30 GIÂY",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDanger) Color(0xFFFCA5A5) else Color(0xFF93C5FD)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "%.1fs".format(inactiveSeconds),
                                    fontSize = 42.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isDanger) Color(0xFFEF4444) else Color(0xFF60A5FA)
                                )
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { (inactiveRemainingMillis / 30000f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth(0.8f)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = if (isDanger) Color(0xFFEF4444) else Color(0xFF3B82F6),
                                    trackColor = Color.White.copy(alpha = 0.1f)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Số lần đã chạm: $touchCount",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }

                        Spacer(Modifier.height(28.dp))

                        // Thẻ thời gian tổng của thử thách
                        Text(
                            text = "THỜI GIAN ĐÃ DUY TRÌ",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.5f),
                            letterSpacing = 1.5.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = formatMMSS(elapsedMillis),
                            fontSize = 54.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White
                        )

                        Spacer(Modifier.height(20.dp))

                        // Tiến trình tới các mốc
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                // Mốc 1: 5 phút -> mở khoá 10 phút
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Mốc 1 (5 phút): Mở khoá 10 phút",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (reached5Min) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text = if (reached5Min) "✓ ĐẠT" else "%s / 05:00".format(formatMMSS(elapsedMillis.coerceAtMost(300_000L))),
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = if (reached5Min) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.6f)
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { (elapsedMillis.toFloat() / 300_000L).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = Color(0xFF4ADE80),
                                    trackColor = Color.White.copy(alpha = 0.1f)
                                )

                                Spacer(Modifier.height(14.dp))

                                // Mốc 2: 10 phút -> mở khoá 2 giờ
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Mốc 2 (10 phút): Mở khoá 2 giờ",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (reached10Min) Color(0xFF60A5FA) else Color.White.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text = if (reached10Min) "✓ ĐẠT" else "%s / 10:00".format(formatMMSS(elapsedMillis.coerceAtMost(600_000L))),
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = if (reached10Min) Color(0xFF60A5FA) else Color.White.copy(alpha = 0.6f)
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { (elapsedMillis.toFloat() / 600_000L).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = Color(0xFF60A5FA),
                                    trackColor = Color.White.copy(alpha = 0.1f)
                                )
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        // Gợi ý quy tắc
                        Text(
                            text = "💡 Lưu ý: Phải chạm ít nhất 1 lần trong 30s. Nếu rời màn hình hoặc thoát app là THUA ngay.",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }

                // ------------------ 2. TRẠNG THÁI FAILED ------------------
                TouchChallengeState.FAILED -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Spacer(Modifier.height(24.dp))
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.close_fill0),
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "THỬ THÁCH THẤT BẠI",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = Color(0xFFEF4444)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = failureReason,
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Thời gian đã giữ được: ${formatMMSS(elapsedMillis)} (${touchCount} lần chạm)",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }

                // ------------------ 3. TRẠNG THÁI UNLOCKED ------------------
                TouchChallengeState.UNLOCKED -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Spacer(Modifier.height(24.dp))
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.lock_open_fill0),
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "ĐÃ MỞ KHOÁ THÀNH CÔNG!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color(0xFF34D399)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Ứng dụng và hệ thống đã được mở khoá tạm thời.",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))

                        // Đồng hồ đếm lùi thời gian mở khoá
                        Text(
                            text = "THỜI GIAN MỞ KHOÁ CÒN LẠI",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.5f),
                            letterSpacing = 1.2.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = formatMMSS(unlockRemainingMillis),
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ========== BOTTOM ACTION BUTTONS ==========
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (state) {
                    TouchChallengeState.RUNNING -> {
                        // Nút Mở khoá 2 giờ (nếu đạt mốc 10 phút)
                        AnimatedVisibility(visible = reached10Min) {
                            Button(
                                onClick = {
                                    TempUnlockManager.activateCustomUnlock(context, 120L)
                                    unlockedMinutes = 120L
                                    unlockRemainingMillis = 120 * 60 * 1000L
                                    state = TouchChallengeState.UNLOCKED
                                    context.popToast("Đã mở khoá 2 giờ thành công!")
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                            ) {
                                Icon(painterResource(R.drawable.lock_open_fill0), null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("MỞ KHOÁ 2 GIỜ", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }

                        // Nút Mở khoá 10 phút (nếu đạt mốc 5 phút)
                        AnimatedVisibility(visible = reached5Min) {
                            Button(
                                onClick = {
                                    TempUnlockManager.activateCustomUnlock(context, 10L)
                                    unlockedMinutes = 10L
                                    unlockRemainingMillis = 10 * 60 * 1000L
                                    state = TouchChallengeState.UNLOCKED
                                    context.popToast("Đã mở khoá 10 phút thành công!")
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (reached10Min) Color(0xFF3B82F6) else Color(0xFF10B981)
                                )
                            ) {
                                Icon(painterResource(R.drawable.lock_open_fill0), null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Mở khoá 10 phút", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }

                        // Nút huỷ thử thách
                        OutlinedButton(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Dừng lại & Quay về", color = Color.White.copy(alpha = 0.7f))
                        }
                    }

                    TouchChallengeState.FAILED -> {
                        Button(
                            onClick = {
                                challengeStartTime = System.currentTimeMillis()
                                lastTouchTime = System.currentTimeMillis()
                                elapsedMillis = 0L
                                inactiveRemainingMillis = 30_000L
                                touchCount = 0
                                failureReason = ""
                                state = TouchChallengeState.RUNNING
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(painterResource(R.drawable.refresh_fill0), null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Thử lại từ đầu", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        OutlinedButton(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Quay về màn hình khoá", color = Color.White.copy(alpha = 0.8f))
                        }
                    }

                    TouchChallengeState.UNLOCKED -> {
                        // Nút Khoá trở lại
                        Button(
                            onClick = {
                                TempUnlockManager.deactivateTempUnlock(context)
                                context.popToast("Đã khoá trở lại!")
                                onNavigateBack()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(painterResource(R.drawable.lock_fill0), null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Khoá trở lại ngay", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        OutlinedButton(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Trở về màn hình chính", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
