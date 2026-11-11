package com.nextnavipro.app

/** Screen-space sizing contract for the live GPS arrow. */
internal object GpsArrowVisualSpec {
    const val WIDTH_DP = 96f
    const val HEIGHT_DP = 112f
    const val ICON_SIZE = 1.0f

    fun pixels(dp: Float, density: Float): Int {
        val safeDp = dp.takeIf { it.isFinite() && it > 0f } ?: 1f
        val safeDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
        return (safeDp * safeDensity + 0.5f).toInt().coerceAtLeast(1)
    }
}
