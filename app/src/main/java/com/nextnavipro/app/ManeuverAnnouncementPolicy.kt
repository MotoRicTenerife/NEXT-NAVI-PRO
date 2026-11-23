package com.nextnavipro.app

internal data class ManeuverAnnouncementThresholds(
    val advanceMeters: Float,
    val prepareMeters: Float,
    val imminentMeters: Float
)

internal object ManeuverAnnouncementPolicy {
    const val MAX_GUIDANCE_FIX_AGE_MS = 3_500L

    fun isFixFresh(ageMillis: Long): Boolean = ageMillis in 0L..MAX_GUIDANCE_FIX_AGE_MS

    fun thresholds(speedMetersPerSecond: Float, accuracyMeters: Float?, isRoundabout: Boolean): ManeuverAnnouncementThresholds {
        val speed = speedMetersPerSecond.takeIf { it.isFinite() && it >= 0f } ?: 0f
        val uncertainty = (accuracyMeters?.takeIf { it.isFinite() && it > 0f } ?: 10f).coerceIn(0f, 20f)
        val advance: Float
        val prepare: Float
        val imminent: Float
        if (speed < 2f) {
            advance = 75f
            prepare = 35f
            imminent = 15f
        } else {
            val advanceSeconds = if (isRoundabout) 24f else 20f
            val prepareSeconds = if (isRoundabout) 12f else 9f
            val imminentSeconds = if (isRoundabout) 6f else 4f
            val minAdvance = if (isRoundabout) 180f else 160f
            val minPrepare = if (isRoundabout) 100f else 80f
            val minImminent = if (isRoundabout) 50f else 40f
            val maxAdvance = if (isRoundabout) 650f else 600f
            val maxPrepare = if (isRoundabout) 300f else 280f
            val maxImminent = if (isRoundabout) 160f else 150f
            advance = (speed * advanceSeconds).coerceIn(minAdvance, maxAdvance)
            prepare = (speed * prepareSeconds).coerceIn(minPrepare, maxPrepare)
            imminent = (speed * imminentSeconds).coerceIn(minImminent, maxImminent)
        }
        return ManeuverAnnouncementThresholds(
            advanceMeters = advance + uncertainty * 1.25f,
            prepareMeters = prepare + uncertainty * 0.75f,
            imminentMeters = imminent + uncertainty * 0.5f
        )
    }

    fun stage(distanceMeters: Double, thresholds: ManeuverAnnouncementThresholds): Int {
        if (!distanceMeters.isFinite() || distanceMeters < 0.0) return 0
        return when {
            distanceMeters <= thresholds.imminentMeters -> 3
            distanceMeters <= thresholds.prepareMeters -> 2
            distanceMeters <= thresholds.advanceMeters -> 1
            else -> 0
        }
    }
}
