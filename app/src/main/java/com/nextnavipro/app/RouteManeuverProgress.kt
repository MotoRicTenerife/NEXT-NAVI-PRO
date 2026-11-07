package com.nextnavipro.app

/**
 * Resolves the next maneuver from continuous route distance rather than sparse vertex indices.
 * A maneuver remains active until the rider has passed its shape point by the small tolerance.
 */
internal data class NextRouteManeuver(val index: Int, val distanceMeters: Double)

internal object RouteManeuverProgress {
    fun next(
        routePoints: List<NavPoint>,
        maneuvers: List<RouteManeuver>,
        currentAlongRouteMeters: Double,
        progressTracker: RouteProgressTracker,
        passedToleranceMeters: Double = 8.0
    ): NextRouteManeuver? {
        if (routePoints.size < 2 || maneuvers.isEmpty() ||
            !currentAlongRouteMeters.isFinite() || !passedToleranceMeters.isFinite() ||
            passedToleranceMeters < 0.0) return null

        for ((index, maneuver) in maneuvers.withIndex()) {
            val maneuverAlong = progressTracker.distanceAtPoint(routePoints, maneuver.pointIndex) ?: continue
            val distance = maneuverAlong - currentAlongRouteMeters
            if (distance >= -passedToleranceMeters) {
                return NextRouteManeuver(index, distance.coerceAtLeast(0.0))
            }
        }
        return null
    }
}
