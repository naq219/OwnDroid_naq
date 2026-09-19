package com.bintianqi.owndroid

import kotlinx.serialization.Serializable

/**
 * Schema cấu hình online (khớp web block_site /api/owndroid-config, version 2).
 * Web là nơi sửa duy nhất (phải giữ màn hình 30p). App chỉ fetch + lưu local.
 */
@Serializable
data class RemoteTier(
    val req: Int = 1,
    val unlock: Long = 5,
    val block: Long = 15
)

@Serializable
data class RemoteStrict(
    val unlockMinutes: Long = 10,
    val blockMinutes: Long = 15,
    val dailyUnlocks: Int = 30
)

@Serializable
data class RemoteNightMode(
    val startHour: Int = 22,
    val endHour: Int = 7
)

@Serializable
data class RemoteLogin(
    val attemptsForSettings: Int = 20,
    val reloginMinutes: Long = 10,
    val challengeLength: Int = 10
)

@Serializable
data class RemoteVpn(
    val `package`: String = "naq.dns",
    val allowlist: List<String> = emptyList()
)

@Serializable
data class OwndroidRemoteConfig(
    val version: Int = 2,
    val updatedAt: Long = 0L,
    val strictLock: RemoteStrict = RemoteStrict(),
    val dayTiers: List<RemoteTier> = listOf(
        RemoteTier(1, 10, 7),
        RemoteTier(10, 15, 1),
        RemoteTier(25, 1440, 5)
    ),
    // unlock = -1 nghĩa là "tới sáng" (tier BỎ QUA ĐÊM NAY)
    val nightTiers: List<RemoteTier> = listOf(
        RemoteTier(1, 5, 15),
        RemoteTier(25, -1, 0)
    ),
    val nightMode: RemoteNightMode = RemoteNightMode(),
    val login: RemoteLogin = RemoteLogin(),
    // Mặc định = dữ liệu cũ hardcode trong app (AppConfig.DEFAULT_* + whitelist)
    val softlockApps: List<String> = listOf(
        "com.facebook.katana",
        "com.google.android.youtube",
        "com.ss.android.ugc.trill",
        "com.instagram.android",
        "com.zhiliaoapp.musically",
        "com.google.android.apps.youtube.kids",
        "com.netflix.mediaclient"
    ),
    val hardlockApps: List<String> = listOf("com.scaleup.dreame"),
    val nightWhitelistExtra: List<String> = listOf(
        "com.android.vending",
        "com.vng.inputmethod.labankey",
        "naq.dns",
        "vn.com.techcombank.bb.app",
        "com.vnid",
        "vn.com.vetc.app",
        "com.vnpay.vpbankonline",
        "com.twofasapp",
        "com.google.android.apps.maps",
        "nhacnho.ghichu.reminder2",
        "com.zing.zalo",
        "com.lux.luxcloud",
        "com.pqsoft.phapquang",
        "app.revanced.android.youtube",
        "com.google.android.apps.bard",
        "naq.a1",
        "naq.a2",
        "naq.a3",
        "co.median.android.zpdprql",
        "in.snapcore.screen_alive",
        "com.android.chrome",
        "com.openai.chatgpt",
        "org.videolan.vlc",
        "com.automattic.simplenote"
    ),
    val vpn: RemoteVpn = RemoteVpn(
        `package` = "naq.dns",
        allowlist = listOf("com.facebook.adsmanager", "com.facebook.pages.app")
    )
)

@Serializable
data class RemoteConfigWrapper(
    val config: OwndroidRemoteConfig = OwndroidRemoteConfig(),
    val stored: Boolean = false
)
