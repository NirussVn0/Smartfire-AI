package com.example.domain.pathfinding

import com.example.domain.model.Point2D
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

enum class TurnDirection {
    STRAIGHT,
    SLIGHT_LEFT,
    TURN_LEFT,
    SHARP_LEFT,
    SLIGHT_RIGHT,
    TURN_RIGHT,
    SHARP_RIGHT,
    U_TURN,
    ARRIVED
}

data class TurnInstruction(
    val direction: TurnDirection,
    val distanceMeters: Float,
    val primaryText: String,
    val shortAction: String
)

data class TurnByTurnGuidance(
    val targetName: String,
    val targetType: String,
    val totalRemainingMeters: Float,
    val etaSeconds: Int,
    val currentStep: TurnInstruction,
    val nextStep: TurnInstruction?,
    val isOutsideHouse: Boolean = false
)

/**
 * Google Maps-style Turn-by-Turn Navigation engine for indoor/outdoor fire evacuation & response.
 * Produces clear primary instructions (e.g., "50m phía trước rẽ trái", "Vị trí cửa thoát hiểm: 500m phía trước")
 * and an upcoming "lượt chờ / tiếp theo" preview step (e.g., "Tiếp theo: Đi thẳng 12m").
 */
object TurnByTurnNavigator {

    fun formatDistanceVietnamese(meters: Float): String {
        val safe = meters.coerceAtLeast(0f)
        return when {
            safe >= 1000f -> String.format(Locale.US, "%.1f km", safe / 1000f)
            safe >= 20f -> "${safe.roundToInt()}m"
            safe >= 1.0f -> String.format(Locale.US, "%.1fm", safe)
            else -> "${(safe * 100).roundToInt()}cm"
        }
    }

    /**
     * Bearing in degrees [0..360) where 0° = North (-Y), 90° = East (+X), 180° = South (+Y), 270° = West (-X).
     */
    fun segmentBearingDegrees(from: Point2D, to: Point2D, widthMeters: Float, lengthMeters: Float): Float {
        val dx = (to.x - from.x) * widthMeters.coerceAtLeast(4f)
        val dy = (to.y - from.y) * lengthMeters.coerceAtLeast(4f)
        // North is -dy, East is +dx
        val rad = atan2(dx.toDouble(), (-dy).toDouble())
        val deg = Math.toDegrees(rad).toFloat()
        return ((deg % 360f) + 360f) % 360f
    }

    /**
     * Signed turn angle in [-180..+180] from [currentHeadingDeg] to [targetBearingDeg].
     * Negative = Left turn, Positive = Right turn.
     */
    fun signedTurnDeltaDegrees(currentHeadingDeg: Float, targetBearingDeg: Float): Float {
        var delta = (targetBearingDeg - currentHeadingDeg) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta
    }

    fun classifyTurn(deltaDeg: Float): TurnDirection {
        val absDelta = abs(deltaDeg)
        return when {
            absDelta <= 22f -> TurnDirection.STRAIGHT
            absDelta >= 150f -> TurnDirection.U_TURN
            deltaDeg < -110f -> TurnDirection.SHARP_LEFT
            deltaDeg < -45f -> TurnDirection.TURN_LEFT
            deltaDeg < -22f -> TurnDirection.SLIGHT_LEFT
            deltaDeg > 110f -> TurnDirection.SHARP_RIGHT
            deltaDeg > 45f -> TurnDirection.TURN_RIGHT
            else -> TurnDirection.SLIGHT_RIGHT
        }
    }

    fun turnPhraseVietnamese(direction: TurnDirection): String = when (direction) {
        TurnDirection.STRAIGHT -> "đi thẳng"
        TurnDirection.SLIGHT_LEFT -> "chếch sang trái"
        TurnDirection.TURN_LEFT -> "rẽ trái"
        TurnDirection.SHARP_LEFT -> "ngoặt trái gấp"
        TurnDirection.SLIGHT_RIGHT -> "chếch sang phải"
        TurnDirection.TURN_RIGHT -> "rẽ phải"
        TurnDirection.SHARP_RIGHT -> "ngoặt phải gấp"
        TurnDirection.U_TURN -> "quay đầu lại"
        TurnDirection.ARRIVED -> "đã tới nơi"
    }

    fun buildGuidance(
        route: NavigationRoute,
        userHeadingDeg: Float,
        houseWidthMeters: Float,
        houseLengthMeters: Float,
        isOutsideHouse: Boolean = false,
        outdoorOverrideDistanceMeters: Float? = null
    ): TurnByTurnGuidance {
        val safeW = houseWidthMeters.coerceAtLeast(4f)
        val safeL = houseLengthMeters.coerceAtLeast(4f)
        val pts = route.pathPoints

        // If user is far outside the house and GPS gives a macro distance, show clean Google-Maps-style distance banner
        if (isOutsideHouse && outdoorOverrideDistanceMeters != null && outdoorOverrideDistanceMeters > 25f) {
            val distStr = formatDistanceVietnamese(outdoorOverrideDistanceMeters)
            val labelPrefix = when (route.targetType) {
                "EXIT" -> "Vị trí cửa thoát hiểm"
                "EXTINGUISHER" -> "Vị trí bình chữa cháy"
                "FIRE_SENSOR" -> "Vị trí báo cháy"
                "TWO_STAGE" -> "Lộ trình Bình CC ➔ Đám cháy"
                else -> route.targetName
            }
            val bearing = if (pts.size >= 2) segmentBearingDegrees(pts.first(), pts.last(), safeW, safeL) else userHeadingDeg
            val dir = classifyTurn(signedTurnDeltaDegrees(userHeadingDeg, bearing))
            val action = if (dir == TurnDirection.STRAIGHT) "phía trước" else "phía trước · ${turnPhraseVietnamese(dir)}"
            return TurnByTurnGuidance(
                targetName = route.targetName,
                targetType = route.targetType,
                totalRemainingMeters = outdoorOverrideDistanceMeters,
                etaSeconds = (outdoorOverrideDistanceMeters / 1.35f).roundToInt().coerceAtLeast(1),
                currentStep = TurnInstruction(
                    direction = dir,
                    distanceMeters = outdoorOverrideDistanceMeters,
                    primaryText = "$labelPrefix: $distStr $action",
                    shortAction = turnPhraseVietnamese(dir).replaceFirstChar { it.uppercase() }
                ),
                nextStep = TurnInstruction(
                    direction = TurnDirection.STRAIGHT,
                    distanceMeters = outdoorOverrideDistanceMeters,
                    primaryText = "Lượt chờ: Tiếp tục đi thẳng tới ${route.targetName}",
                    shortAction = "Đi thẳng"
                ),
                isOutsideHouse = true
            )
        }

        if (pts.size < 2) {
            return TurnByTurnGuidance(
                targetName = route.targetName,
                targetType = route.targetType,
                totalRemainingMeters = 0f,
                etaSeconds = 0,
                currentStep = TurnInstruction(
                    direction = TurnDirection.ARRIVED,
                    distanceMeters = 0f,
                    primaryText = "Đã tới ${route.targetName}",
                    shortAction = "Đã tới nơi"
                ),
                nextStep = null,
                isOutsideHouse = isOutsideHouse
            )
        }

        // Compute segment lengths and bearings
        data class Seg(val distM: Float, val bearingDeg: Float)
        val segments = mutableListOf<Seg>()
        var totalMeters = 0f
        for (i in 0 until pts.size - 1) {
            val dx = (pts[i + 1].x - pts[i].x) * safeW
            val dy = (pts[i + 1].y - pts[i].y) * safeL
            val d = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (d >= 0.15f) {
                segments.add(Seg(d, segmentBearingDegrees(pts[i], pts[i + 1], safeW, safeL)))
                totalMeters += d
            }
        }

        if (segments.isEmpty() || totalMeters <= 0.9f) {
            return TurnByTurnGuidance(
                targetName = route.targetName,
                targetType = route.targetType,
                totalRemainingMeters = totalMeters,
                etaSeconds = 0,
                currentStep = TurnInstruction(
                    direction = TurnDirection.ARRIVED,
                    distanceMeters = totalMeters,
                    primaryText = "Đã tới ${route.targetName}",
                    shortAction = "Đã tới nơi"
                ),
                nextStep = null,
                isOutsideHouse = isOutsideHouse
            )
        }

        val firstSeg = segments[0]
        val firstTurnDir = classifyTurn(signedTurnDeltaDegrees(userHeadingDeg, firstSeg.bearingDeg))
        val firstDistStr = formatDistanceVietnamese(firstSeg.distM)

        val currentStep: TurnInstruction
        val nextStep: TurnInstruction?

        if (segments.size >= 2) {
            val secondSeg = segments[1]
            val upcomingTurnAtCorner = classifyTurn(signedTurnDeltaDegrees(firstSeg.bearingDeg, secondSeg.bearingDeg))
            val secondDistStr = formatDistanceVietnamese(secondSeg.distM)

            // Google Maps style: "50m phía trước rẽ trái" + "Lượt chờ: Đi thẳng 12m"
            val primaryPhrase = if (firstTurnDir == TurnDirection.STRAIGHT || firstTurnDir == TurnDirection.SLIGHT_LEFT || firstTurnDir == TurnDirection.SLIGHT_RIGHT) {
                if (upcomingTurnAtCorner == TurnDirection.STRAIGHT) {
                    "$firstDistStr phía trước đi thẳng"
                } else {
                    "$firstDistStr phía trước ${turnPhraseVietnamese(upcomingTurnAtCorner)}"
                }
            } else {
                "${turnPhraseVietnamese(firstTurnDir).replaceFirstChar { it.uppercase() }} · đi $firstDistStr"
            }

            val displayDir = if (firstTurnDir == TurnDirection.STRAIGHT && upcomingTurnAtCorner != TurnDirection.STRAIGHT) {
                upcomingTurnAtCorner
            } else {
                firstTurnDir
            }

            currentStep = TurnInstruction(
                direction = displayDir,
                distanceMeters = firstSeg.distM,
                primaryText = primaryPhrase,
                shortAction = turnPhraseVietnamese(displayDir).replaceFirstChar { it.uppercase() }
            )

            val afterCornerDir = if (segments.size >= 3) {
                classifyTurn(signedTurnDeltaDegrees(secondSeg.bearingDeg, segments[2].bearingDeg))
            } else {
                TurnDirection.STRAIGHT
            }
            nextStep = TurnInstruction(
                direction = afterCornerDir,
                distanceMeters = secondSeg.distM,
                primaryText = if (segments.size >= 3 && afterCornerDir != TurnDirection.STRAIGHT) {
                    "Lượt chờ: đi $secondDistStr rồi ${turnPhraseVietnamese(afterCornerDir)}"
                } else {
                    "Lượt chờ: đi thẳng $secondDistStr tới ${route.targetName}"
                },
                shortAction = if (afterCornerDir == TurnDirection.STRAIGHT) "Đi thẳng" else turnPhraseVietnamese(afterCornerDir).replaceFirstChar { it.uppercase() }
            )
        } else {
            val prefix = when (route.targetType) {
                "EXIT" -> "Cửa thoát hiểm"
                "EXTINGUISHER" -> "Bình chữa cháy"
                "FIRE_SENSOR" -> "Điểm báo cháy"
                else -> route.targetName
            }
            val primaryPhrase = if (firstTurnDir == TurnDirection.STRAIGHT) {
                "$prefix: $firstDistStr phía trước · đi thẳng"
            } else {
                "$prefix: ${turnPhraseVietnamese(firstTurnDir)} · $firstDistStr phía trước"
            }
            currentStep = TurnInstruction(
                direction = firstTurnDir,
                distanceMeters = firstSeg.distM,
                primaryText = primaryPhrase,
                shortAction = turnPhraseVietnamese(firstTurnDir).replaceFirstChar { it.uppercase() }
            )
            nextStep = TurnInstruction(
                direction = TurnDirection.ARRIVED,
                distanceMeters = 0f,
                primaryText = "Lượt chờ: Đi thẳng tới đích (${route.targetName})",
                shortAction = "Đi thẳng"
            )
        }

        return TurnByTurnGuidance(
            targetName = route.targetName,
            targetType = route.targetType,
            totalRemainingMeters = totalMeters,
            etaSeconds = (totalMeters / 1.25f).roundToInt().coerceAtLeast(1),
            currentStep = currentStep,
            nextStep = nextStep,
            isOutsideHouse = isOutsideHouse
        )
    }
}
