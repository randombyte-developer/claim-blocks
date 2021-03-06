package de.randombyte.claimblocks.config

import com.flowpowered.math.vector.Vector3i
import de.randombyte.claimblocks.getClaimCorners
import de.randombyte.kosp.extensions.rangeTo
import ninja.leaping.configurate.objectmapping.Setting
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable
import org.spongepowered.api.block.BlockType
import org.spongepowered.api.block.BlockTypes.*

@ConfigSerializable
internal class GeneralConfig(
        @Setting(comment = "If you are using GriefPrevention this determines if the created claims should consume the GP-claimblocks of the player")
        val consumeClaimBlocks: Boolean = false,
        @Setting val ranges: List<Range> = emptyList(),
        @Setting val beacons: Beacons = GeneralConfig.Beacons(),
        @Setting val debug: Boolean = false
) {
    @ConfigSerializable
    class Range(
            @Setting val block: BlockType = AIR,
            @Setting val horizontalRange: Int = -1,
            @Setting(comment = "-1 for max. height") val verticalRange: Int = -1,
            @Setting(comment = "Shifts the created claim instead of centering it around the claimblock") val shifting: Vector3i = Vector3i.ZERO
    )

    @ConfigSerializable
    class Beacons(
            @Setting val enabled: Boolean = true,
            @Setting(comment = "-1 for max. height; 0 for default beacon range") val verticalRange: Int = 0
    )

    constructor() : this(ranges = listOf(
            Range(COAL_BLOCK, horizontalRange = 1, verticalRange = 1),
            Range(IRON_BLOCK, horizontalRange = 5, verticalRange = 5),
            Range(GOLD_ORE, horizontalRange = 45, verticalRange = 45)
    ))

    fun getRangeConfig(blockType: BlockType): Range? = ranges.firstOrNull { it.block == blockType }

    /**
     * Checks if all ranges' shifting are valid.
     */
    fun validate() {
        ranges.forEach { range ->
            val (cornerA, cornerB) = getClaimCorners(Vector3i.ZERO, range.horizontalRange, range.verticalRange)
            val claimArea = cornerB..cornerA
            // the negate() can be omitted here because of symmetry but it is left here to avoid future bugs
            if (!claimArea.contains(range.shifting.negate())) {
                throw RuntimeException("[ClaimBlocks] Invalid config: '${range.block.id}'-range's shifting is bigger than the claim itself!")
            }
        }
    }
}