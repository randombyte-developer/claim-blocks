package de.randombyte.claimblocks.config

import de.randombyte.claimblocks.config.GeneralConfig.Messages
import de.randombyte.kosp.extensions.aqua
import de.randombyte.kosp.extensions.toArg
import de.randombyte.kosp.fixedTextTemplateOf
import ninja.leaping.configurate.objectmapping.Setting
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable
import org.spongepowered.api.block.BlockType
import org.spongepowered.api.block.BlockTypes.*
import org.spongepowered.api.text.TextTemplate

@ConfigSerializable
internal class GeneralConfig(
        @Setting val messages: Messages = Messages(),
        @Setting val ranges: List<Range> = emptyList(),
        @Setting val beacons: Beacons = GeneralConfig.Beacons()
) {
    @ConfigSerializable
    class Messages(
            @Setting val enterClaim: TextTemplate = fixedTextTemplateOf("You entered the claim of '",
                    "claimOwner".toArg().aqua(),"'!"),
            @Setting val exitClaim: TextTemplate = fixedTextTemplateOf("You left the claim of '",
                    "claimOwner".toArg().aqua(),"'!")
    )

    @ConfigSerializable
    class Range(
            @Setting val block: BlockType = AIR,
            @Setting val horizontalRange: Int = -1,
            @Setting(comment = "-1 for max. height") val verticalRange: Int = -1
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
}