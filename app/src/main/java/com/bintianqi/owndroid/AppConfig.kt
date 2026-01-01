package com.bintianqi.owndroid

/**
 * Centralized configuration for the app.
 * Edit values here for quick adjustments.
 */
object AppConfig {
    
    // ============= PASSWORD ATTEMPTS =============
    
    /**
     * Number of password attempts required to access main settings
     * and configure softlock/hardlock lists
     */
    const val ATTEMPTS_FOR_SETTINGS = 50
    
    /**
     * Number of password attempts required to enable temporary unlock (10 minutes)
     */
    const val ATTEMPTS_FOR_TEMP_UNLOCK = 6
    
    /**
     * Number of password attempts required to enable day unlock (24 hours)
     */
    const val ATTEMPTS_FOR_DAY_UNLOCK = 25
    
    // ============= TEMP UNLOCK =============
    
    /**
     * Duration of temporary unlock in minutes (10 minutes)
     */
    const val TEMP_UNLOCK_DURATION_MINUTES = 10L
    
    /**
     * Duration of day unlock in minutes (24 hours)
     */
    const val DAY_UNLOCK_DURATION_MINUTES = 24 * 60L
    
    // ============= NIGHT MODE =============
    
    /**
     * Night mode start hour (24h format)
     */
    const val NIGHT_MODE_START_HOUR = 22
    
    /**
     * Night mode end hour (24h format)
     */
    const val NIGHT_MODE_END_HOUR = 7
    
    // ============= VPN =============
    
    /**
     * Package name of the VPN app
     */
    const val VPN_PACKAGE = "naq.dns"
    
    /**
     * Packages allowed to bypass VPN
     */
    val VPN_ALLOWLIST: Set<String?> = setOf(
        "com.facebook.adsmanager",
        "com.facebook.orca",
        "com.facebook.pages.app"
    )
}
