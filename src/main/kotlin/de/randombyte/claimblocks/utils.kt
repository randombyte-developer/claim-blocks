package de.randombyte.claimblocks

import com.flowpowered.math.vector.Vector3i
import de.randombyte.kosp.extensions.copy

/**
 * If [verticalRange] is less than 0 the corners are set to cover all the area from bedrock to the max build height.
 */
internal fun getClaimCorners(center: Vector3i, horizontalRange: Int, verticalRange: Int): Pair<Vector3i, Vector3i> {
    if (verticalRange < 0) {
        val (cornerA, cornerB) = getClaimCorners(center, horizontalRange, 0)
        return cornerA.copy(newY = 0) to cornerB.copy(newY = 256)
    }
    return center.add(horizontalRange, verticalRange, horizontalRange) to center.sub(horizontalRange, verticalRange, horizontalRange)
}