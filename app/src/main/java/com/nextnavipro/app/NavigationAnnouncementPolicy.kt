package com.nextnavipro.app

/** Speed- and maneuver-aware distances for the two spoken navigation stages. */
internal data class NavigationAnnouncementThresholds(
    val advanceMeters: Float,
    val confirmationMeters: Float
)

internal object NavigationAnnouncementPolicy {
    fun thresholds(speedMetersPerSecond: Float, roundabout: Boolean): NavigationAnnouncementThresholds {
        val speed = speedMetersPerSecond.takeIf { it.isFinite() && it >= 0f } ?: 0f
        val advanceSeconds = if (roundabout) 26f else 22f
        val confirmationSeconds = if (roundabout) 7f else 5f
        return NavigationAnnouncementThresholds(
            advanceMeters = (speed * advanceSeconds).coerceIn(150f, 600f),
            confirmationMeters = (speed * confirmationSeconds).coerceIn(30f, 180f)
        )
    }
}
