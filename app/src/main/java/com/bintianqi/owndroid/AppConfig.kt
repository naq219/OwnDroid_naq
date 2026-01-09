package com.bintianqi.owndroid

/**
 * Centralized configuration for the app.
 * 
 * UNLOCK TIERS:
 * - Day Mode (07:00 - 22:00): 1→10p, 6→15p, 35→1day
 * - Night Mode (22:00 - 07:00): 1→5p (block 15p), 25→bypass
 */
object AppConfig {
    
    // ============= UNLOCK TIER =============
    
    /**
     * Unlock tier configuration
     * @param requiredAttempts Number of correct passwords needed
     * @param unlockMinutes Duration of unlock (-1 = until morning)
     * @param blockMinutes Block duration after unlock expires
     * @param label Display text for button
     */
    data class UnlockTier(
        val requiredAttempts: Int,
        val unlockMinutes: Long,
        val blockMinutes: Long,
        val label: String
    )
    
    // Day mode tiers (07:00 - 22:00)
    val DAY_TIERS = listOf(
        UnlockTier(1 , 10,   5,  "10 PHÚT"),
        UnlockTier(10,  15,   1,  "15 PHÚT"),
        UnlockTier(25, 1440, 5,  "1 NGÀY")
    )
    
    // Night mode tiers (22:00 - 07:00)
    val NIGHT_TIERS = listOf(
        UnlockTier(1,  5,  15, "5 PHÚT"),
        UnlockTier(25, -1, 0,  "BỎ QUA ĐÊM NAY")
    )
    
    // ============= NIGHT MODE =============
    
    const val NIGHT_MODE_START_HOUR = 22
    const val NIGHT_MODE_END_HOUR = 7
    
    // ============= VPN =============
    
    const val VPN_PACKAGE = "naq.dns"
    
    val VPN_ALLOWLIST: Set<String?> = setOf(
        "com.facebook.adsmanager",
        "com.facebook.orca",
        "com.facebook.pages.app"
    )

    /**
     * Get whitelist of apps allowed during Night Mode
     */
    fun getNightModeWhitelist(): Set<String> {
        // Start with VPN whitelist
        val whitelist = VPN_ALLOWLIST.filterNotNull().toMutableSet()
        // Add additional critical apps if needed
        whitelist.add("com.android.vending") // Google Play Store
         whitelist.add("com.vng.inputmethod.labankey")
          whitelist.add("naq.dns")
           whitelist.add("vn.com.techcombank.bb.app")
            whitelist.add("com.vnid")

              whitelist.add("com.vnid")
                whitelist.add("com.vnpay.vpbankonline")
         whitelist.add("com.twofasapp")       


        return whitelist
    }
    
    // ============= SETTINGS =============
    
    const val ATTEMPTS_FOR_SETTINGS = 3
    
    // Legacy constants for backward compatibility
    const val ATTEMPTS_FOR_TEMP_UNLOCK = 6
    const val ATTEMPTS_FOR_DAY_UNLOCK = 25
    const val TEMP_UNLOCK_DURATION_MINUTES = 10L
    const val DAY_UNLOCK_DURATION_MINUTES = 1440L
    
    // ============= HELPERS =============
    
    /**
     * Get tiers based on current mode
     */
    fun getCurrentTiers(isNightMode: Boolean) = 
        if (isNightMode) NIGHT_TIERS else DAY_TIERS
    
    /**
     * Find best tier user qualifies for
     */
    fun getBestTier(attempts: Int, isNightMode: Boolean): UnlockTier? =
        getCurrentTiers(isNightMode)
            .filter { it.requiredAttempts <= attempts }
            .maxByOrNull { it.requiredAttempts }
    
    /**
     * Get next tier to work towards
     */
    fun getNextTier(attempts: Int, isNightMode: Boolean): UnlockTier? =
        getCurrentTiers(isNightMode)
            .filter { it.requiredAttempts > attempts }
            .minByOrNull { it.requiredAttempts }
    
    /**
     * Get first tier (minimum requirement)
     */
    fun getFirstTier(isNightMode: Boolean): UnlockTier =
        getCurrentTiers(isNightMode).first()
}
