package de.randombyte.claimblocks.regions.crossborderevent

import me.ryanhamshire.griefprevention.api.event.BorderClaimEvent
import org.spongepowered.api.event.Listener
import org.spongepowered.api.text.TextTemplate

internal class GriefPreventionCrossBorderClaimListener(
        private val getEnterTextTemplate: () -> TextTemplate,
        private val getExitTextTemplate: () -> TextTemplate
) {

    @Listener
    fun onCrossBorderClaimEvent(event: BorderClaimEvent) {
        val exitClaim = event.exitClaim
        val enterClaim = event.enterClaim

        if (enterClaim.isWilderness) {
            val text = getExitTextTemplate().apply(mapOf("claimOwner" to exitClaim.ownerName)).build()
            event.setExitMessage(text)
        } else {
            val text = getEnterTextTemplate().apply(mapOf("claimOwner" to enterClaim.ownerName)).build()
            event.setEnterMessage(text)
        }
    }
}