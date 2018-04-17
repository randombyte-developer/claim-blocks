package de.randombyte.claimblocks

import com.flowpowered.math.vector.Vector3i
import com.google.inject.Inject
import de.randombyte.claimblocks.ClaimBlocks.Companion.FOX_GUARD_ID
import de.randombyte.claimblocks.ClaimBlocks.Companion.GRIEF_PREVENTION_ID
import de.randombyte.claimblocks.config.DatabaseConfig
import de.randombyte.claimblocks.config.GeneralConfig
import de.randombyte.claimblocks.config.MessagesConfig
import de.randombyte.claimblocks.config.WorldTypeSerializer
import de.randombyte.claimblocks.regions.ClaimManager
import de.randombyte.claimblocks.regions.GriefPreventionClaimManager
import de.randombyte.claimblocks.regions.RedProtectClaimManager
import de.randombyte.claimblocks.regions.crossborderevent.GriefPreventionCrossBorderClaimListener
import de.randombyte.kosp.bstats.BStats
import de.randombyte.kosp.config.ConfigManager
import de.randombyte.kosp.extensions.orNull
import de.randombyte.kosp.extensions.rangeTo
import de.randombyte.kosp.extensions.typeToken
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
        const val VERSION = "1.3-7.0.0"
        const val AUTHOR = "RandomByte"

        const val GRIEF_PREVENTION_ID = "griefprevention"
        const val FOX_GUARD_ID = "foxguard"
        const val RED_PROTECT_ID = "redprotect"

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

    // Worlds must be loaded when this config is loaded due to WorldTypeSerializer
    private val claimBlocksConfigManager = ConfigManager(
            configLoader = HoconConfigurationLoader.builder().setPath(configPath.resolve("database.conf")).build(),
            clazz = DatabaseConfig::class.java,
            hyphenSeparatedKeys = true,
            additionalSerializers = {
                registerType(World::class.typeToken, WorldTypeSerializer)
            })

    private val messageConfigManager = ConfigManager(
            configLoader = HoconConfigurationLoader.builder().setPath(configPath.resolve("messages.conf")).build(),
            clazz = MessagesConfig::class.java,
            hyphenSeparatedKeys = true,
            simpleTextSerialization = true,
            simpleTextTemplateSerialization = true
    )

    // The config is needed frequently -> cache it
    private lateinit var config: GeneralConfig

    private lateinit var messages: MessagesConfig

    private lateinit var claimManager: ClaimManager

    @Listener
    fun onInit(event: GameInitializationEvent) {
        loadConfig()

        if (!loadClaimManager()) {
            Sponge.getEventManager().unregisterPluginListeners(this)
            throw RuntimeException("No supported region plugin(GriefPrevention, RedProtect) is available! ClaimBlocks won't be usable!")
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
        config.validate()
        generalConfigManager.save(config) // regenerate config

        messages = messageConfigManager.get()
        messageConfigManager.save(messages) // regenerate config
    }

    /**
     * Takes action when placing claim blocks, beacon and beacon base blocks.
     */
    @Listener
    fun onPlaceBlock(event: ChangeBlockEvent.Place, @Root player: Player) {
        event.transactions.forEach { transaction ->
            val final = transaction.final
            val blockType = final.state.type
            val location = final.location.get()

            val rangeConfig = config.getRangeConfig(final.state.type)

            val configPresent = rangeConfig != null
            val handleBeacon = config.beacons.enabled && final.state.type == BEACON
            val isBeaconBaseBlock = final.state.type.isBeaconBaseBlock()

            if ((configPresent || handleBeacon || isBeaconBaseBlock) && !checkPermission(player, blockType)) return

            if (rangeConfig != null) {
                val horizontalRange = rangeConfig.horizontalRange
                val verticalRange = rangeConfig.verticalRange
                if (!createClaim(location, horizontalRange, verticalRange, rangeConfig.shifting, listOf(player))) {
                    event.isCancelled = true
                }
            } else if (handleBeacon) {
                executeAfterBeaconBlockUpdate {
                    if (location.tileEntity.isPresent) {
                        val beacon = location.tileEntity.get() as Beacon
                        registerBeaconBlock(beacon, listOf(player))
                    }
                }
            } else if (isBeaconBaseBlock) {
                executeAfterBeaconBlockUpdate {
                    getBeaconsInRange(location, 10).forEach { beacon -> checkBeacon(beacon, listOf(player)) }
                }
            }
        }
    }

    /**
     * @return true if permissions are okay, false if not
     */
    private fun checkPermission(player: Player, blockType: BlockType) =
            player.hasPermission("$ROOT_PERMISSION.use.${blockType.id}")

    private fun registerBeaconBlock(beacon: Beacon, players: List<Player>) {
        val horizontalRange = beacon.getHorizontalRange()
        if (horizontalRange > 0) {
            val verticalRange = if (config.beacons.verticalRange == 0) horizontalRange else config.beacons.verticalRange
            if (!createClaim(beacon.location, horizontalRange, verticalRange, players = players)) {
                // didn't work -> remove beacon and drop item
                destroyAndDropBlock(beacon.location)
            }
        } else {
            // no beacon pyramid underneath
            destroyAndDropBlock(beacon.location)
            players.forEach { it.sendMessage(messages.beaconLevelZero) }
        }
    }

    private fun createClaim(location: Location<World>, horizontalRange: Int, verticalRange: Int, shifting: Vector3i = Vector3i.ZERO, players: List<Player>): Boolean {
        val shiftedCenter = location.blockPosition.add(shifting)
        val (cornerA, cornerB) = getClaimCorners(shiftedCenter, horizontalRange, verticalRange)

        if (checkOverlaps(location.extent, cornerA, cornerB, players)) return false

        val success = claimManager.createClaim(location.extent, cornerA, cornerB, players.first().uniqueId)
        if (success) {
            // use the real location of the claimblock here to later check if a claim is registered when breaking the block
            val newDatabaseConfig = claimBlocksConfigManager.get().addPosition(location, horizontalRange)
            claimBlocksConfigManager.save(newDatabaseConfig)
            players.forEach { it.sendMessage(messages.createdClaim) }
            return true
        } else {
            players.forEach { it.sendMessage(messages.claimCreationFailed) }
            return false
        }
    }

    /**
     * Sends the [players] a message when the given region overlaps with a registered claim.
     *
     * @return true if overlaps with other region(s), false if not
     */
    private fun checkOverlaps(world: World, cornerA: Vector3i, cornerB: Vector3i, players: List<Player>): Boolean {
        val allClaimOwnersInRange = claimManager.getClaimOwners(world, cornerA, cornerB).toSet()
        if (allClaimOwnersInRange.isNotEmpty()) {
            val claimOwnersNames = allClaimOwnersInRange.joinToString()
            val message = messages.claimsOverlap.apply(mapOf("overlapsClaimOwnersNames" to claimOwnersNames)).build()
            players.forEach { it.sendMessage(message) }
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

                    val isRegisteredClaimBlockType = config.getRangeConfig(original.state.type) != null
                    if (isRegistered && (isRegisteredClaimBlockType || original.state.type == BEACON)) {
                        removeClaim(location, players)
                        return
                    } else if (original.state.type.isBeaconBaseBlock()) {
                        executeAfterBeaconBlockUpdate {
                            getBeaconsInRange(location, 20).forEach { beacon -> checkBeacon(beacon, players) }
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
        if (successful) players.forEach { it.sendMessage(messages.removedClaim) }
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
            players.forEach { it.sendMessage(messages.invalidBeacon) }
            return false
        } else {
            val realRange = beacon.getHorizontalRange()
            if (realRange != storedRange) {
                // range changed
                destroyAndDropBlock(location)
                removeClaim(location, players)
                players.forEach { it.sendMessage(messages.beaconLevelChanged) }
                return false
            }
        }

        return true
    }

    private fun destroyAndDropBlock(location: Location<World>) {
        val blockType = location.block.type.item.orElseThrow { IllegalArgumentException("${location.block.type} doesn't have an item type!") }
        // break
        location.setBlock(AIR.defaultState)
        //drop
        val itemEntity = location.extent.createEntity(EntityTypes.ITEM, location.blockPosition)
        itemEntity.offer(Keys.REPRESENTED_ITEM, ItemStack.of(blockType, 1).createSnapshot())
        location.extent.spawnEntity(itemEntity)
    }

    private fun loadClaimManager(): Boolean {
        /*if (Sponge.getPluginManager().getPlugin(FOX_GUARD_ID).isPresent) {
            claimManager = FoxGuardClaimManager(FGManager.getInstance(), FGFactoryManager.getInstance())
            return true
        }*/

        if (Sponge.getPluginManager().getPlugin(GRIEF_PREVENTION_ID).isPresent) {
            claimManager = GriefPreventionClaimManager(
                    getServiceOrFail(GriefPreventionApi::class),
                    doesConsumeClaimBlocks = { config.consumeClaimBlocks },
                    getInsufficientGriefPreventionClaimBlocksText = { messages.insufficientGriefPreventionClaimBlocks },
                    debug = config.debug,
                    logger = logger
            )
            Sponge.getEventManager().registerListeners(this, GriefPreventionCrossBorderClaimListener(
                    getEnterTextTemplate = { messages.enterClaim },
                    getExitTextTemplate = { messages.exitClaim }
            ))
            return true
        }

        if (Sponge.getPluginManager().getPlugin(RED_PROTECT_ID).isPresent) {
            claimManager = RedProtectClaimManager()
            return true
        }

        return false
    }

    private fun getBeaconsInRange(location: Location<*>, range: Int): List<Beacon> {
        val centerPosition = location.blockPosition
        val cornerA = centerPosition.sub(range, range, range)
        val cornerB = centerPosition.add(range, range, range)

        val beaconBlocks = (cornerA..cornerB).mapNotNull { position ->
            location.extent.getTileEntity(position).orNull() as? Beacon
        }

        return beaconBlocks
    }

    private fun Beacon.getHorizontalRange() = completedLevels.let { lvl -> if (lvl > 0) lvl * 10 + 10 else 0 }

    /**
     * This is needed to ensure that the [Beacon] has updated its [Beacon.getCompletedLevels] before
     * accessing it. The update occurs each ticke where `worldTime % 80 == 0` is true.
     */
    private fun executeAfterBeaconBlockUpdate(action: () -> Unit) = Task.builder()
            .delayTicks(81) // the update time of beacon blocks is roughly 80 ticks
            .execute(action)
            .submit(this)
}