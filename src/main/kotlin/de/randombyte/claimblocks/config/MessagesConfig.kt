package de.randombyte.claimblocks.config

import de.randombyte.kosp.extensions.*
import de.randombyte.kosp.fixedTextTemplateOf
import ninja.leaping.configurate.objectmapping.Setting
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable
import org.spongepowered.api.text.Text
import org.spongepowered.api.text.TextTemplate

@ConfigSerializable
class MessagesConfig(
        @Setting val createdClaim: Text = "Created claim!".green(),
        @Setting val claimCreationFailed: Text = "Failed to create claim!".red(),
        @Setting val removedClaim: Text = "Removed claim!".yellow(),
        @Setting val claimsOverlap: TextTemplate = fixedTextTemplateOf(
                "You can't create a claim here, it overlaps with claims by other players: ".red(),
                "overlapsClaimOwnersNames".toArg().red()),

        @Setting val beaconLevelZero: Text = "The base of the beacon block has to be completed at first!".yellow(),
        @Setting val invalidBeacon: Text = "Beacon was destroyed because it was invalid!".yellow(),
        @Setting val beaconLevelChanged: Text = ("Beacon was destroyed because its completeness level changed! " +
                "Re-place the beacon to re-register it.").yellow(),

        @Setting val enterClaim: TextTemplate = fixedTextTemplateOf("You entered the claim of '",
                "claimOwner".toArg().aqua(),"'!"),
        @Setting val exitClaim: TextTemplate = fixedTextTemplateOf("You left the claim of '",
                "claimOwner".toArg().aqua(),"'!")
)