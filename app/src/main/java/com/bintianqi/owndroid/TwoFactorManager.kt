package com.bintianqi.owndroid

import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log
import org.json.JSONArray
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

/**
 * Quản lý xác thực 2 lớp (2FA - TOTP RFC 6238) và Mã dự phòng (Backup Codes).
 * Cung cấp chức năng mở khoá vĩnh viễn và khoá trở lại cho OwnDroid.
 */
object TwoFactorManager {
    private const val TAG = "TwoFactorManager"
    private const val TOTP_TIME_STEP_SECONDS = 30L
    private const val TOTP_DIGITS = 6
    private const val BACKUP_CODES_COUNT = 8

    // Bảng ký tự Base32 (RFC 4648)
    private const val BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /**
     * Lấy Secret Key hiện tại (lưu trong SP hoặc fallback về AppConfig.DEFAULT_2FA_SECRET).
     */
    fun getSecretKey(): String {
        val saved = SP.twoFactorSecret?.trim()
        if (!saved.isNullOrEmpty()) {
            return saved
        }
        val defaultSecret = AppConfig.DEFAULT_2FA_SECRET
        SP.twoFactorSecret = defaultSecret
        return defaultSecret
    }

    /**
     * Lấy danh sách mã dự phòng khả dụng.
     * Nếu chưa có thì tự động sinh 8 mã mới.
     */
    fun getBackupCodes(): List<String> {
        val json = SP.twoFactorBackupCodesJson
        if (!json.isNullOrBlank()) {
            try {
                val array = JSONArray(json)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    list.add(array.getString(i))
                }
                if (list.isNotEmpty()) return list
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi đọc backup codes từ SP", e)
            }
        }
        return generateNewBackupCodes()
    }

    /**
     * Tạo mới danh sách 8 mã dự phòng và lưu vào SP.
     * Định dạng: 4 ký tự - 4 ký tự (ví dụ: A3F8-9K2C).
     */
    fun generateNewBackupCodes(): List<String> {
        val random = SecureRandom()
        val chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        val newCodes = (1..BACKUP_CODES_COUNT).map {
            val part1 = (1..4).map { chars[random.nextInt(chars.length)] }.joinToString("")
            val part2 = (1..4).map { chars[random.nextInt(chars.length)] }.joinToString("")
            "$part1-$part2"
        }
        saveBackupCodes(newCodes)
        return newCodes
    }

    private fun saveBackupCodes(codes: List<String>) {
        val array = JSONArray()
        codes.forEach { array.put(it) }
        SP.twoFactorBackupCodesJson = array.toString()
    }

    /**
     * Kiểm tra mã nhập vào:
     * 1. Mã TOTP 6 số (chấp nhận lệch ±1 step = ±30s).
     * 2. Hoặc mã dự phòng (nếu đúng thì tiêu thụ mã đó).
     */
    fun verifyCode(rawInput: String): Boolean {
        val cleaned = rawInput.trim().replace(" ", "").replace("-", "").uppercase()
        if (cleaned.isEmpty()) return false

        // 1. Kiểm tra nếu là mã TOTP 6 số
        if (cleaned.length == TOTP_DIGITS && cleaned.all { it.isDigit() }) {
            if (verifyTotp(getSecretKey(), cleaned)) {
                Log.d(TAG, "Xác thực TOTP 2FA thành công!")
                return true
            }
        }

        // 2. Kiểm tra nếu là mã dự phòng
        val currentBackupCodes = getBackupCodes().toMutableList()
        val matchedIndex = currentBackupCodes.indexOfFirst {
            it.replace("-", "").equals(cleaned, ignoreCase = true)
        }
        if (matchedIndex != -1) {
            val consumedCode = currentBackupCodes.removeAt(matchedIndex)
            saveBackupCodes(currentBackupCodes)
            Log.d(TAG, "Xác thực bằng mã dự phòng thành công! Đã tiêu thụ mã: $consumedCode")
            return true
        }

        return false
    }

    /**
     * Xác thực TOTP với dung sai ±1 bước (30s trước, hiện tại, 30s sau).
     */
    private fun verifyTotp(secretBase32: String, code: String): Boolean {
        try {
            val key = base32Decode(secretBase32)
            val currentSeconds = System.currentTimeMillis() / 1000L
            val currentStep = currentSeconds / TOTP_TIME_STEP_SECONDS

            for (stepOffset in -1..1) {
                val candidateCode = computeTotp(key, currentStep + stepOffset, TOTP_DIGITS)
                if (candidateCode == code) {
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi kiểm tra TOTP", e)
        }
        return false
    }

    /**
     * Tính mã TOTP theo RFC 6238 / RFC 4226.
     */
    private fun computeTotp(key: ByteArray, counter: Long, digits: Int): String {
        val data = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "RAW"))
        val hash = mac.doFinal(data)

        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)

        val otp = binary % (10.0.pow(digits.toDouble()).toInt())
        return otp.toString().padStart(digits, '0')
    }

    /**
     * Giải mã chuỗi Base32 chuẩn RFC 4648.
     */
    private fun base32Decode(base32: String): ByteArray {
        val clean = base32.trim().replace("=", "").replace(" ", "").uppercase()
        var buffer = 0
        var bitsLeft = 0
        val output = mutableListOf<Byte>()

        for (ch in clean) {
            val index = BASE32_CHARS.indexOf(ch)
            if (index == -1) continue // Bỏ qua ký tự không hợp lệ
            buffer = (buffer shl 5) or index
            bitsLeft += 5
            if (bitsLeft >= 8) {
                output.add((buffer shr (bitsLeft - 8) and 0xFF).toByte())
                bitsLeft -= 8
            }
        }
        return output.toByteArray()
    }

    /**
     * Tạo chuỗi otpauth URI để import vào Google Authenticator / 2FAS.
     */
    fun getOtpAuthUri(label: String = "OwnDroid"): String {
        val secret = getSecretKey()
        return "otpauth://totp/$label?secret=$secret&issuer=OwnDroid"
    }

    /**
     * MỞ KHOÁ VĨNH VIỄN BẰNG 2FA:
     * - Tương đương các tính năng mở khoá khi mở khoá chặt chẽ:
     *   + Bỏ suspend toàn bộ app (trừ hardlock hoặc mở toàn bộ).
     *   + Unhide các app.
     *   + Gỡ bỏ install apps restriction (DISALLOW_INSTALL_APPS).
     *   + Gỡ bỏ always-on VPN (setAlwaysOnVpnPackage = null).
     *   + Gỡ bỏ VPN config restriction (DISALLOW_CONFIG_VPN).
     *   + Gỡ bỏ Private DNS restriction (DISALLOW_CONFIG_PRIVATE_DNS).
     *   + Xoá block time (clearBlockPeriod).
     *   + Huỷ auto-relock worker (WORK_NAME) để KHÔNG TỰ ĐỘNG KHOÁ LẠI.
     * - Lưu cờ isTwoFactorUnlocked = true để mở khoá vĩnh viễn cho đến khi người dùng bấm Khoá lại.
     */
    fun unlockPermanently(context: Context) {
        try {
            val appCtx = context.applicationContext

            // 1. Huỷ relock worker để không tự động khoá lại sau 10p hay 24h
            TempUnlockManager.cancelRelock(appCtx)

            // 2. Mở khoá các ứng dụng và gỡ bỏ hạn chế hệ thống (tương đương button mở khoá chặt chẽ)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val hardlockApps = TempUnlockManager.getHardlockApps()
                val allUserApps = TempUnlockManager.getAllUserInstalledApps(appCtx)
                val appsToUnlock = allUserApps.filter { !hardlockApps.contains(it) }

                if (appsToUnlock.isNotEmpty()) {
                    Privilege.DPM.setPackagesSuspended(Privilege.DAR, appsToUnlock.toTypedArray(), false)
                }

                for (pkg in appsToUnlock) {
                    TempUnlockManager.unhideApp(pkg)
                }

                // Gỡ các hạn chế DPM
                Privilege.DPM.clearUserRestriction(Privilege.DAR, UserManager.DISALLOW_INSTALL_APPS)
                Privilege.DPM.setAlwaysOnVpnPackage(Privilege.DAR, null, false)
                Privilege.DPM.clearUserRestriction(Privilege.DAR, UserManager.DISALLOW_CONFIG_VPN)
                Privilege.DPM.clearUserRestriction(Privilege.DAR, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
            }

            // 3. Xoá trạng thái block thời gian
            TempUnlockManager.clearBlockPeriod()

            // 4. Lưu trạng thái mở khoá vĩnh viễn
            SP.isTwoFactorUnlocked = true
            SP.isTempUnlockActive = true
            SP.tempUnlockEndTime = 0L // 0L nghĩa là không có đếm ngược hết hạn
            SP.lastAuthTime = System.currentTimeMillis()

            Log.d(TAG, "Đã mở khoá vĩnh viễn bằng 2FA!")
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi thực hiện unlockPermanently", e)
        }
    }

    /**
     * KHOÁ TRỞ LẠI:
     * - Đặt isTwoFactorUnlocked = false.
     * - Gọi deactivateTempUnlock để tái thiết lập toàn bộ quy chế khoá (Day/Night hoặc Strict lock).
     */
    fun relock(context: Context) {
        try {
            val appCtx = context.applicationContext
            SP.isTwoFactorUnlocked = false
            SP.isTempUnlockActive = false
            SP.tempUnlockEndTime = 0L

            TempUnlockManager.deactivateTempUnlock(appCtx)
            Log.d(TAG, "Đã khoá trở lại thiết bị!")
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi thực hiện relock", e)
        }
    }
}
