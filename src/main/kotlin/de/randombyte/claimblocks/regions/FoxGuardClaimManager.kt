package de.randombyte.claimblocks.regions

import com.flowpowered.math.vector.Vector3i
import de.randombyte.kosp.extensions.getUser
import de.randombyte.kosp.extensions.orNull
import net.foxdenstudio.sponge.foxguard.plugin.FGManager
import net.foxdenstudio.sponge.foxguard.plugin.`object`.IFGObject
import net.foxdenstudio.sponge.foxguard.plugin.`object`.factory.FGFactoryManager
import net.foxdenstudio.sponge.foxguard.plugin.handler.BasicHandler
import org.spongepowered.api.entity.living.player.User
import org.spongepowered.api.world.Location
import org.spongepowered.api.world.World
import java.util.*

class FoxGuardClaimManager(
        private val foxGuardManager: FGManager,
        private val foxGuardFactoryManager: FGFactoryManager
) : ClaimManager {
    override fun getClaimOwners(location: Location<World>): List<User> {
        val regions = foxGuardManager.getRegionsAtPos(location.extent, location.blockPosition)
        val claimOwners = regions.map { region ->
            region.handlers.map { handler ->
                (handler as? BasicHandler)?.getGroup("owners")?.orNull()?.users?.mapNotNull(UUID::getUser) ?: emptyList()
            }
        }.flatten().flatten()

        return claimOwners
    }

    override fun createClaim(world: World, positionA: Vector3i, positionB: Vector3i, owner: UUID): Boolean {
        // used to generate unique region and handler names
        val randomString = UUID.randomUUID().toString()
        // create region and handler
        val region = foxGuardFactoryManager.createRegion("ClaimBlocks region $randomString", "cuboid", "", null)
        val basicHandler = foxGuardFactoryManager.createHandler("ClaimBlocks owners handler $randomString", "basic", 0, "bare", null) as BasicHandler
        // add owner
        val ownersGroup = basicHandler.createGroup("owners").orNull() ?: return false
        if (!basicHandler.addUser(ownersGroup, owner)) return false
        // register handler
        if (!foxGuardManager.addHandler(basicHandler)) return false
        // link handler to region
        if (!region.addHandler(basicHandler)) return false

        return true
    }

    // Trying to tidy everything up
    override fun removeClaim(location: Location<World>): Boolean {
        val regions = foxGuardManager.getRegionsAtPos(location.extent, location.blockPosition)
        regions.filter { it.isByClaimBlocksPlugin() }.forEach { region ->
            region.handlers.filter { it.isByClaimBlocksPlugin() }.forEach {
                // unlink handler and region
                if (!region.removeHandler(it)) return false
                // unregister handler
                if (!foxGuardManager.removeHandler(it)) return false
            }
            // remove region
            if (!foxGuardManager.removeRegion(region)) return false
        }

        return true
    }

    private fun IFGObject.isByClaimBlocksPlugin() = name.startsWith("ClaimBlocks")
}