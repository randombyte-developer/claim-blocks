package de.randombyte.claimblocks

import com.flowpowered.math.vector.Vector3i
import com.google.inject.Inject
import de.randombyte.claimblocks.ClaimBlocks.Companion.FOX_GUARD_ID
import de.randombyte.claimblocks.ClaimBlocks.Companion.GRIEF_PREVENTION_ID
import de.randombyte.claimblocks.config.DatabaseConfig
import de.randombyte.claimblocks.config.GeneralConfig
import de.randombyte.claimblocks.config.WorldTypeSerializer
import de.randombyte.claimblocks.regions.ClaimManager
import de.randombyte.claimblocks.regions.GriefPreventionClaimManager
import de.randombyte.claimblocks.regions.crossborderevent.GriefPreventionCrossBorderClaimListener
import de.randombyte.kosp.bstats.BStats
import de.randombyte.kosp.config.ConfigManager
import de.randombyte.kosp.extensions.*
import de.randombyte.kosp.getServiceOrFail
import me.ryanhamshire.griefprevention.api.GriefPreventionApi
import ninja.leaping.configurate.hocon.HoconConfigurationLoader
import org.slf4j.Logger
import org.spongepowered.api.Sponge
import org.spongepowered.api.block.BlockType
import org.spongepowered.api.block.BlockTypes.*
import org.spongepowered.api.block.tileentity.carrier.Beacon
import org.spongepowered.api.config.ConfigDir
import org.spongepowered.api.data.key.Keys
import org.spongepowered.api.entity.EntityTypes
import org.spongepowered.api.entity.living.player.Player
import org.spongepowered.api.entity.living.player.User
import org.spongepowered.api.event.Listener
import org.spongepowered.api.event.block.ChangeBlockEvent
import org.spongepowered.api.event.cause.Cause
import org.spongepowered.api.event.filter.cause.Root
import org.spongepowered.api.event.game.GameReloadEvent
import org.spongepowered.api.event.game.state.GameInitializationEvent
import org.spongepowered.api.item.inventory.ItemStack
import org.spongepowered.api.plugin.Dependency
import org.spongepowered.api.plugin.Plugin
import org.spongepowered.api.plugin.PluginContainer
import org.spongepowered.api.scheduler.Task
import org.spongepowered.api.world.Location
import org.spongepowered.api.world.World
import java.nio.file.Files
import java.nio.file.Path

@Plugin(id = ClaimBlocks.ID,
        name = ClaimBlocks.NAME,
        version = ClaimBlocks.VERSION,
        authors = arrayOf(ClaimBlocks.AUTHOR),
        dependencies = arrayOf(
                Dependency(id = GRIEF_PREVENTION_ID, optional = true),
                Dependency(id = FOX_GUARD_ID, optional = true)))
class ClaimBlocks @Inject constructor(
        val logger: Logger,
        @ConfigDir(sharedRoot = false) configPath: Path,
        val bStats: BStats,
        val pluginContainer: PluginContainer
) {
    internal companion object {
        const val ID = "claim-blocks"
        const val NAME = "ClaimBlocks"
        const val VERSION = "0.2"
        const val AUTHOR = "RandomByte"

        const val GRIEF_PREVENTION_ID = "griefprevention"
        const val FOX_GUARD_ID = "foxguard"

        const val ROOT_PERMISSION = ID

        val BEACON_BASE_BLOCKS = listOf(IRON_BLOCK, GOLD_BLOCK, DIAMOND_BLOCK, EMERALD_BLOCK)
        private fun BlockType.isBeaconBaseBlock() = BEACON_BASE_BLOCKS.contains(this)
    }

    init {
        if (Files.notExists(configPath)) Files.createDirectory(configPath)
    }

    private val generalConfigManager = ConfigManager(
            configLoader = HoconConfigurationLoader.builder().setPath(configPath.resolve("general.conf")).build(),
            clazz = GeneralConfig::class.java,
            hyphenSeparatedKeys = true,
            simpleTextSerialization = true,
            simpleTextTemplateSerialization = true)

    private val claimBlocksConfigManager = ConfigManager(
            configLoader = HoconConfigurationLoader.builder().setPath(configPath.resolve("database.conf")).build(),
            clazz = DatabaseConfig::class.java,
            hyphenSeparatedKeys = true,
            additionalSerializers = {
                registerType(World::class.typeToken, WorldTypeSerializer)
            })

    // The config is needed frequently -> cache it
    private lateinit var config: GeneralConfig

    private lateinit var claimManager: ClaimManager

    @Listener
    fun onInit(event: GameInitializationEvent) {
        loadConfig()

        if (!loadClaimManager()) {
            Sponge.getEventManager().unregisterPluginListeners(this)
            throw RuntimeException("No supported region plugin(GriefPrevention) is available! ClaimBlocks won't be usable!")
        }

        logger.info("$NAME loaded: $VERSION")
    }

    @Listener
    fun onReload(event: GameReloadEvent) {
        loadConfig()

        logger.info("Reloaded!")
    }

    private fun loadConfig() {
        config = generalConfigManager.get()
        generalConfigManager.save(config) // regenerate config
    }

    /**
     * Takes action when placing claim blocks, beacon and beacon base blocks.
     */
    @Listener
    fun onPlaceBlock(event: ChangeBlockEvent.Place, @Root player: Player) {
        if (!player.hasPermission("$ROOT_PERMISSION.use")) return
        event.transactions.forEach { transaction ->
            val final = transaction.final
            val location = final.location.get()

            if (isRegisteredClaimBlock(final.state.type)) {
                val range = getRange(final.state.type)!!
                if (range > 0 && !createClaim(location, range, listOf(player))) event.isCancelled = true
            } else if (config.beaconsEnabled && final.state.type == BEACON) {
                executeAfterBeaconBlockUpdate {
                    if (location.tileEntity.isPresent) {
                        val beacon = location.tileEntity.get() as Beacon
                        registerBeaconBlock(beacon, listOf(player))
                    }
                }
            } else if (final.state.type.isBeaconBaseBlock()) {
                executeAfterBeaconBlockUpdate {
                    getBeaconsInRange(location, 10).forEach { beacon -> checkBeacon(beacon, listOf(player)) }
                }
            }
        }
    }

    private fun registerBeaconBlock(beacon: Beacon, players: List<Player>) {
        val range = beacon.getRange()
        if (range > 0) {
            if (!createClaim(beacon.location, range, players)) {
                // didn't work -> remove beacon and drop item
                destroyAndDropBlock(beacon.location)
            }
        } else {
            // no beacon pyramid underneath
            destroyAndDropBlock(beacon.location)
            players.forEach { it.sendMessage("The base of the beacon block has to be completed at first!".yellow()) }
        }
    }

    private fun createClaim(location: Location<World>, range: Int, players: List<Player>): Boolean {
        val (cornerA, cornerB) = getClaimCorners(location.blockPosition, range)

        if (checkOverlaps(location.extent, cornerA, cornerB, players)) return false

        val success = claimManager.createClaim(location.extent, cornerA, cornerB, players.first().uniqueId)
        if (success) {
            val newDatabaseConfig = claimBlocksConfigManager.get().addPosition(location, range)
            claimBlocksConfigManager.save(newDatabaseConfig)
            players.forEach { it.sendMessage("Created claim!".green()) }
            return true
        } else {
            players.forEach { it.sendMessage("Failed to create claim!".red()) }
            return false
        }
    }

    /**
     * Sends the [players] a message when the given region overlaps with a registered claim.
     *
     * @return true if overlaps with other region(s), false if not
     */
    private fun checkOverlaps(world: World, cornerA: Vector3i, cornerB: Vector3i, players: List<Player>): Boolean {
        val allRegionOwnersInRange = claimManager.getClaimOwners(world, cornerA, cornerB).toSet()
        if (allRegionOwnersInRange.isNotEmpty()) {
            players.forEach { it.sendMessage(("You can't create a claim here, it overlaps with claims by other players: " +
                    allRegionOwnersInRange.joinToString(transform = User::getName)).red()) }
            return true
        }

        return false
    }

    /**
     * Takes action when a claim block, a beacon or a beacon base block is broken.
     */
    @Listener
    fun onBreakBlock(event: ChangeBlockEvent.Break) {
        event.transactions
                .forEach { transaction ->
                    val original = transaction.original
                    val location = original.location.get()
                    val player = event.cause.first(Player::class.java).orNull()
                    val players = if (player == null) emptyList() else listOf(player)

                    val isRegistered = claimBlocksConfigManager.get().getRange(location) != null

                    if (isRegistered && (isRegisteredClaimBlock(original.state.type) || original.state.type == BEACON)) {
                        removeClaim(location, players)
                        return
                    } else if (original.state.type.isBeaconBaseBlock()) {
                        executeAfterBeaconBlockUpdate {
                            getBeaconsInRange(location, 10).forEach { beacon -> checkBeacon(beacon, players) }
                        }
                    }
                }
    }

    /**
     * Just removes the claim from the region plugin and the internal config file. Sends a message
     * to the [players].
     */
    private fun removeClaim(location: Location<World>, players: List<Player>) {
        val successful = claimManager.removeClaim(location)
        val newDatabaseConfig = claimBlocksConfigManager.get().removePosition(location)
        claimBlocksConfigManager.save(newDatabaseConfig)
        if (successful) players.forEach { it.sendMessage("Removed claim!".yellow()) }
    }

    /**
     * Checks if the range of the beacon is the same as the range it was registered with; if they
     * don't equal the Beacon gets dropped to the ground. This method never registers beacons.
     *
     * @return true if everything is okay, false if it was destroyed because it was invalid
     */
    private fun checkBeacon(beacon: Beacon, players: List<Player>): Boolean {
        val location = beacon.location
        val storedRange = claimBlocksConfigManager.get().getRange(location)
        if (storedRange == null) {
            // invalid beacon
            destroyAndDropBlock(location)
            players.forEach { it.sendMessage(("Beacon was destroyed because it was invalid!").yellow()) }
            return false
        } else {
            val realRange = beacon.getRange()
            if (realRange != storedRange) {
                // range changed
                destroyAndDropBlock(location)
                removeClaim(location, players)
                players.forEach {
                    it.sendMessage(("Beacon was destroyed because its completeness level changed! " +
                            "Re-place the beacon to re-register it.").yellow()) }
                return false
            }
        }

        return true
    }

    private fun destroyAndDropBlock(location: Location<World>) {
        val blockType = location.block.type.item.orElseThrow { IllegalArgumentException("${location.block.type} doesn't have an item type!") }
        // break
        location.setBlock(AIR.defaultState, Cause.source(pluginContainer).build())
        //drop
        val itemEntity = location.extent.createEntity(EntityTypes.ITEM, location.blockPosition)
        itemEntity.offer(Keys.REPRESENTED_ITEM, ItemStack.of(blockType, 1).createSnapshot())
        location.extent.spawnEntity(itemEntity, Cause.source(this@ClaimBlocks).build())
    }

    private fun loadClaimManager(): Boolean {
        /*if (Sponge.getPluginManager().getPlugin(FOX_GUARD_ID).isPresent) {
            claimManager = FoxGuardClaimManager(FGManager.getInstance(), FGFactoryManager.getInstance())
            return true
        }*/

        if (Sponge.getPluginManager().getPlugin(GRIEF_PREVENTION_ID).isPresent) {
            claimManager = GriefPreventionClaimManager(pluginContainer, getServiceOrFail(GriefPreventionApi::class))
            Sponge.getEventManager().registerListeners(this, GriefPreventionCrossBorderClaimListener(
                    getEnterTextTemplate = config.messages::enterClaim,
                    getExitTextTemplate = config.messages::exitClaim
            ))
            return true
        }

        return false
    }

    private fun getRange(blockType: BlockType): Int? = config.ranges.firstOrNull { it.block == blockType }?.range
    private fun isRegisteredClaimBlock(blockType: BlockType) = getRange(blockType) != null

    private fun getClaimCorners(center: Vector3i, range: Int) = center.add(range, range, range) to center.sub(range, range, range)

    private fun getBeaconsInRange(location: Location<*>, range: Int): List<Beacon> {
        val centerPosition = location.blockPosition
        val cornerA = centerPosition.sub(range, range, range)
        val cornerB = centerPosition.add(range, range, range)

        val beaconBlocks = (cornerA..cornerB).mapNotNull { position ->
            location.extent.getTileEntity(position).orNull() as? Beacon
        }

        return beaconBlocks
    }

    private fun Beacon.getRange() = completedLevels.let { lvl -> if (lvl > 0) lvl * 10 + 10 else 0 }

    /**
     * This is needed to ensure that the [Beacon] has updated its [Beacon.getCompletedLevels] before
     * accessing it. The update occurs each ticke where `worldTime % 80 == 0` is true.
     */
    private fun executeAfterBeaconBlockUpdate(action: () -> Unit) = Task.builder()
            .delayTicks(81) // the update time of beacon blocks is roughly 80 ticks
            .execute(action)
            .submit(this)
}