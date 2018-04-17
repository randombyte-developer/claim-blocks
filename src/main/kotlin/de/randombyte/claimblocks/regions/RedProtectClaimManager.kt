package de.randombyte.claimblocks.regions

import br.net.fabiozumbi12.RedProtect.Sponge.API.RedProtectAPI
import br.net.fabiozumbi12.RedProtect.Sponge.RPUtil
import br.net.fabiozumbi12.RedProtect.Sponge.RedProtect
import br.net.fabiozumbi12.RedProtect.Sponge.Region
import com.flowpowered.math.vector.Vector3i
import de.randombyte.kosp.extensions.getUser
import de.randombyte.kosp.extensions.toUUID
import org.spongepowered.api.world.Location
import org.spongepowered.api.world.World
import java.util.*

class RedProtectClaimManager : ClaimManager {
    private fun getRedProtectAPI() : RedProtectAPI {
        return RedProtect.get().api
    }

    override fun getClaimOwners(location: Location<World>): List<String> {
        return getRedProtectAPI().getRegion(location)?.leaders?.mapNotNull { userUuid ->
            try {
                if (userUuid == "#server#") "Server" else userUuid.toUUID().getUser()?.name
            } catch (exception: IllegalArgumentException) {
                return@mapNotNull userUuid
            }
        } ?: emptyList()
    }

    override fun createClaim(world: World, positionA: Vector3i, positionB: Vector3i, owner: UUID): Boolean {
        val claimName = "ClaimBlocks region ${UUID.randomUUID()}"
        val ownerName = owner.getUser()!!.name.toLowerCase()
        val locationA = world.getLocation(positionB) // switched because RP...
        val locationB = world.getLocation(positionA)

        val maxMbrX = locationB.blockX
        val minMbrX = locationA.blockX
        val maxMbrZ = locationB.blockZ
        val minMbrZ = locationA.blockZ
        val maxY = locationB.blockY
        val minY = locationA.blockY

        val region = Region(
                claimName,
                LinkedList<String>(), // admins
                LinkedList<String>(), // members
                LinkedList<String>().apply { add(ownerName) }, // leaders
                maxMbrX,
                minMbrX,
                maxMbrZ,
                minMbrZ,
                minY,
                maxY,
                hashMapOf<String, Any>(), // flags
                "", // welcome message
                0, // priority
                world.name,
                RPUtil.DateNow(), // latest visit of member or leader
                0, // "latest value of the region" say the docs
                locationA, // teleport location
                true // "can delete" say the docs, don't know what it does exactly
        )

        getRedProtectAPI().addRegion(region, world)

        return true
    }

    override fun removeClaim(location: Location<World>): Boolean {
        getRedProtectAPI().removeRegion(getRedProtectAPI().getRegion(location))
        return true
    }
}