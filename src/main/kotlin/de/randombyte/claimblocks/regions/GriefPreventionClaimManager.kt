package de.randombyte.claimblocks.regions

import com.flowpowered.math.vector.Vector3i
import de.randombyte.kosp.extensions.getPlayer
import de.randombyte.kosp.extensions.getUser
import me.ryanhamshire.griefprevention.api.GriefPreventionApi
import me.ryanhamshire.griefprevention.api.claim.Claim
import me.ryanhamshire.griefprevention.api.claim.ClaimResultType
import org.slf4j.Logger
import org.spongepowered.api.event.cause.Cause
import org.spongepowered.api.plugin.PluginContainer
import org.spongepowered.api.text.Text
import org.spongepowered.api.world.Location
import org.spongepowered.api.world.World
import java.util.*

internal class GriefPreventionClaimManager(
        pluginContainer: PluginContainer,
        private val griefPreventionApi: GriefPreventionApi,
        private val doesConsumeClaimBlocks: () -> Boolean,
        private val getInsufficientGriefPreventionClaimBlocksText: () -> Text,
        private val debug: Boolean = false,
        private val logger: Logger
) : ClaimManager {

    private val cause = Cause.source(pluginContainer).build()

    private fun getClaimManager(world: World) = griefPreventionApi.getClaimManager(world)

    override fun getClaimOwners(location: Location<World>): List<String> {
        val claim = getClaimManager(location.extent).getClaimAt(location)
        return if (claim.isWilderness || claim.ownerUniqueId == UUID(0, 0)) emptyList()
        else listOf(claim.ownerUniqueId.getUser()!!.name)
    }

    override fun createClaim(world: World, positionA: Vector3i, positionB: Vector3i, owner: UUID): Boolean {
        val claimResult = Claim.builder()
                .world(world)
                .bounds(positionA, positionB)
                .cuboid(true)
                .owner(owner)
                .cause(cause)
                .requireClaimBlocks(doesConsumeClaimBlocks())
                .build()

        if (debug) {
            logger.info("Tried creating claim with GriefPrevention: Result = '${claimResult.resultType.name}'; Message = '${claimResult.message.orElse(Text.EMPTY).toPlain()}'")
        }
        if (claimResult.resultType == ClaimResultType.INSUFFICIENT_CLAIM_BLOCKS) {
            owner.getPlayer()?.sendMessage(getInsufficientGriefPreventionClaimBlocksText())
        }
        return claimResult.claim.isPresent
    }

    override fun removeClaim(location: Location<World>): Boolean {
        val claimManager = getClaimManager(location.extent)
        val claim = claimManager.getClaimAt(location)
        if (claim.isWilderness) return false
        claimManager.deleteClaim(claim, cause)
        return true
    }
}