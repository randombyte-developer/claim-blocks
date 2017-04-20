package de.randombyte.claimblocks.regions

import com.flowpowered.math.vector.Vector3i
import de.randombyte.claimblocks.rangeTo
import org.spongepowered.api.entity.living.player.User
import org.spongepowered.api.world.Location
import org.spongepowered.api.world.World
import java.util.*

/**
 * Implemented for the various region plugins out there.
 * This would help: https://github.com/SpongePowered/SpongeAPI/issues/1175
 */
interface ClaimManager {

    fun getClaimOwners(location: Location<World>): List<User>

    fun getClaimOwners(world: World, positionA: Vector3i, positionB: Vector3i): List<User> {
        val positionMin = positionA.min(positionB)
        val positionMax = positionA.max(positionB)

        val allRegionOwners = (positionMin..positionMax).mapNotNull { position ->
            val regionOwners = getClaimOwners(Location(world, position))
            return@mapNotNull if (regionOwners.isEmpty()) null else regionOwners
        }.flatten()



        return allRegionOwners
    }

    fun createClaim(world: World, positionA: Vector3i, positionB: Vector3i, owner: UUID): Boolean

    fun removeClaim(location: Location<World>): Boolean
}