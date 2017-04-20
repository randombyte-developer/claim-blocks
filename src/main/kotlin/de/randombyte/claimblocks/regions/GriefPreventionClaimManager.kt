package de.randombyte.claimblocks.regions

import com.flowpowered.math.vector.Vector3i
import de.randombyte.kosp.extensions.getUser
import de.randombyte.kosp.extensions.orNull
import me.ryanhamshire.griefprevention.api.GriefPreventionApi
import me.ryanhamshire.griefprevention.api.claim.Claim
import org.spongepowered.api.entity.living.player.User
import org.spongepowered.api.event.cause.Cause
import org.spongepowered.api.plugin.PluginContainer
import org.spongepowered.api.world.Location
import org.spongepowered.api.world.World
import java.util.*

internal class GriefPreventionClaimManager(
        pluginContainer: PluginContainer,
        private val griefPreventionApi: GriefPreventionApi
) : ClaimManager {

    private val cause = Cause.source(pluginContainer).build()

    private fun getClaimManager(world: World) = griefPreventionApi.getClaimManager(world)

    override fun getClaimOwners(location: Location<World>): List<User> {
        val claim = getClaimManager(location.extent).getClaimAt(location, false) // ignoreHeight: false
        return if (claim.isWilderness || claim.ownerUniqueId == UUID(0, 0)) emptyList()
        else listOf(claim.ownerUniqueId.getUser()!!)
    }

    override fun createClaim(world: World, positionA: Vector3i, positionB: Vector3i, owner: UUID): Boolean {
        val claim = Claim.builder()
                .cuboid(true)
                .world(world)
                .bounds(positionA, positionB)
                .owner(owner)
                .cause(cause)
                .build().claim.orNull() ?: return false
        griefPreventionApi.getClaimManager(world).addClaim(claim, cause)
        return true
    }

    override fun removeClaim(location: Location<World>): Boolean {
        val claimManager = getClaimManager(location.extent)
        val claim = claimManager.getClaimAt(location, false) // ignoreHeight: false
        if (claim.isWilderness) return false
        claimManager.deleteClaim(claim, cause)
        return true
    }
}