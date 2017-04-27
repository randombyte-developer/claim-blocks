package de.randombyte.claimblocks.config

import de.randombyte.claimblocks.config.GeneralConfig.Messages
import de.randombyte.kosp.extensions.aqua
import de.randombyte.kosp.extensions.toArg
import de.randombyte.kosp.fixedTextTemplateOf
import ninja.leaping.configurate.objectmapping.Setting
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable
import org.spongepowered.api.block.BlockState
import org.spongepowered.api.block.BlockTypes
import org.spongepowered.api.text.TextTemplate

@ConfigSerializable
class GeneralConfig(
        @Setting val messages: Messages = Messages(),
        @Setting val ranges: Map<BlockState, Int> = emptyMap()
) {
    @ConfigSerializable
    class Messages(
            @Setting val enterClaim: TextTemplate = fixedTextTemplateOf("You entered the claim of '",
                    "claimOwner".toArg().aqua(),"'!"),
            @Setting val exitClaim: TextTemplate = fixedTextTemplateOf("You left the claim of '",
                    "claimOwner".toArg().aqua(),"'!")
    )

    constructor() : this(ranges = mapOf(
            BlockTypes.COAL_BLOCK.defaultState to 1,
            BlockTypes.IRON_BLOCK.defaultState to 5,
            BlockTypes.GOLD_ORE.defaultState to 45
    ))
}