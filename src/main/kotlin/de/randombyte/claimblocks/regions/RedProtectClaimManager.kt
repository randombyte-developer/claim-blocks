package de.randombyte.claimblocks.regions

import br.net.fabiozumbi12.redprotect.API.RedProtectAPI
import br.net.fabiozumbi12.redprotect.RPUtil
import br.net.fabiozumbi12.redprotect.Region
import com.flowpowered.math.vector.Vector3i
import de.randombyte.kosp.extensions.getUser
import de.randombyte.kosp.extensions.orNull
import de.randombyte.kosp.getServiceOrFail
import org.spongepowered.api.entity.living.player.User
import org.spongepowered.api.service.user.UserStorageService
import org.spongepowered.api.world.Location
import org.spongepowered.api.world.World
import java.util.*

class RedProtectClaimManager : ClaimManager {
    override fun getClaimOwners(location: Location<World>): List<User> {
        return RedProtectAPI.getRegion(location)?.owners?.mapNotNull { userName ->
            getServiceOrFail(UserStorageService::class).get(userName).orNull()
        } ?: emptyList()
    }

    override fun createClaim(world: World, positionA: Vector3i, positionB: Vector3i, owner: UUID): Boolean {
        val claimName = "ClaimBlocks region ${UUID.randomUUID()}"
        val ownerName = owner.getUser()!!.name
        val locationA = world.getLocation(positionB) // switched because RP...
        val locationB = world.getLocation(positionA)
        val region = Region(
                claimName,
                LinkedList<String>().apply { add(ownerName) },
                LinkedList<String>(),
                ownerName,
                locationA,
                locationB,
                hashMapOf<String, Any>(),
                "",
                0,
                world.name,
                RPUtil.DateNow(),
                0,
                null
        )

        RedProtectAPI.addRegion(region, world)

        return true
    }

    override fun removeClaim(location: Location<World>): Boolean {
        RedProtectAPI.removeRegion(RedProtectAPI.getRegion(location))
        return true
    }
}