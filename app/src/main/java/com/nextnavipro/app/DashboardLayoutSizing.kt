package com.nextnavipro.app

/**
 * Scales visual-only dimensions when the app has unusually little vertical space.
 * Callers must preserve minimum touch-target sizes when applying this scale.
 */
internal object DashboardLayoutSizing {
    fun visualScale(availableHeightDp: Int, isPortrait: Boolean): Float {
        if (availableHeightDp <= 0) return 1f
        val referenceHeightDp = if (isPortrait) 760f else 420f
        return (availableHeightDp / referenceHeightDp).coerceIn(0.82f, 1f)
    }

    fun visualDp(valueDp: Int, availableHeightDp: Int, isPortrait: Boolean): Int {
        if (valueDp <= 0) return valueDp
        return (valueDp * visualScale(availableHeightDp, isPortrait)).toInt().coerceAtLeast(1)
    }
}
