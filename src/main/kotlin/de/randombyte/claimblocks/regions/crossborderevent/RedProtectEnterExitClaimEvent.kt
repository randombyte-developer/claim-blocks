package de.randombyte.claimblocks.regions.crossborderevent

import br.net.fabiozumbi12.RedProtect.Sponge.events.EnterExitRegionEvent
import org.spongepowered.api.text.TextTemplate

internal class RedProtectEnterExitClaimEvent(
        private val getEnterTextTemplate: () -> TextTemplate,
        private val getExitTextTemplate: () -> TextTemplate
) {

    fun onEnterExit(event: EnterExitRegionEvent) {

    }
}