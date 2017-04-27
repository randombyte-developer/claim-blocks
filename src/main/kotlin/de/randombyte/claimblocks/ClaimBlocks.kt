package de.randombyte.claimblocks

import com.google.inject.Inject
import de.randombyte.claimblocks.ClaimBlocks.Companion.GRIEF_PREVENTION_ID
import de.randombyte.claimblocks.config.DatabaseConfig
import de.randombyte.claimblocks.config.GeneralConfig
import de.randombyte.claimblocks.config.WorldTypeSerializer
import de.randombyte.claimblocks.regions.ClaimManager
import de.randombyte.claimblocks.regions.GriefPreventionClaimManager
import de.randombyte.kosp.bstats.BStats
import de.randombyte.kosp.config.ConfigManager
import de.randombyte.kosp.extensions.green
import de.randombyte.kosp.extensions.red
import de.randombyte.kosp.extensions.typeToken
import de.randombyte.kosp.extensions.yellow
import de.randombyte.kosp.getServiceOrFail
import me.ryanhamshire.griefprevention.api.GriefPreventionApi
import me.ryanhamshire.griefprevention.api.event.BorderClaimEvent
import ninja.leaping.configurate.hocon.HoconConfigurationLoader
import org.slf4j.Logger
import org.spongepowered.api.Sponge
import org.spongepowered.api.block.BlockState
import org.spongepowered.api.config.ConfigDir
import org.spongepowered.api.entity.living.player.Player
import org.spongepowered.api.entity.living.player.User
import org.spongepowered.api.event.Listener
import org.spongepowered.api.event.block.ChangeBlockEvent
import org.spongepowered.api.event.filter.Getter
import org.spongepowered.api.event.filter.cause.Root
import org.spongepowered.api.event.game.GameReloadEvent
import org.spongepowered.api.event.game.state.GameInitializationEvent
import org.spongepowered.api.plugin.Dependency
import org.spongepowered.api.plugin.Plugin
import org.spongepowered.api.plugin.PluginContainer
import org.spongepowered.api.world.World
import java.nio.file.Files
import java.nio.file.Path

@Plugin(id = ClaimBlocks.ID,
        name = ClaimBlocks.NAME,
        version = ClaimBlocks.VERSION,
        authors = arrayOf(ClaimBlocks.AUTHOR),
        dependencies = arrayOf(
                Dependency(id = GRIEF_PREVENTION_ID, optional = true)))
class ClaimBlocks @Inject constructor(
        val logger: Logger,
        @ConfigDir(sharedRoot = false) configPath: Path,
        val bStats: BStats,
        val pluginContainer: PluginContainer
) {
    internal companion object {
        const val ID = "claim-blocks"
        const val NAME = "ClaimBlocks"
        const val VERSION = "0.1"
        const val AUTHOR = "RandomByte"

        const val GRIEF_PREVENTION_ID = "griefprevention"

        const val ROOT_PERMISSION = ID
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
        loadClaimManager()

        loadConfig()

        logger.info("$NAME loaded: $VERSION")
    }

    @Listener
    fun onReload(event: GameReloadEvent) {
        loadConfig()

        logger.info("Reloaded!")
    }

    @Listener
    fun onPlaceBlock(event: ChangeBlockEvent.Place, @Root player: Player) {
        if (!player.hasPermission("$ROOT_PERMISSION.use")) return
        event.transactions
                .filter { isClaimBlock(it.final.state) }
                .map { it.final.location.get() to getRange(it.final.state) }
                .forEach { (location, range) ->
                    val centerPosition = location.blockPosition
                    val cornerA = centerPosition.add(range, range, range)
                    val cornerB = centerPosition.sub(range, range, range)

                    val allRegionOwnersInRange = claimManager.getClaimOwners(location.extent, cornerA, cornerB).toSet()

                    val everythingUnclaimed = allRegionOwnersInRange.isEmpty()
                    if (everythingUnclaimed) {
                        val success = claimManager.createClaim(location.extent, cornerA, cornerB, player.uniqueId)
                        if (success) {
                            val newDatabaseConfig = claimBlocksConfigManager.get().addPosition(location)
                            claimBlocksConfigManager.save(newDatabaseConfig)
                            player.sendMessage("Created claim!".green())
                        } else {
                            player.sendMessage("Failed to create claim!".red())
                            event.isCancelled = true
                        }
                    } else {
                        player.sendMessage(("You can't create a claim here, it overlaps with claims by other players: " +
                                allRegionOwnersInRange.joinToString(transform = User::getName)).red())
                        event.isCancelled = true
                    }
                }
    }

    @Listener
    fun onBreakBlock(event: ChangeBlockEvent.Break) {
        event.transactions
                .filter { isClaimBlock(it.original.state) && claimBlocksConfigManager.get().contains(it.original.location.get())}
                .forEach { transaction ->
                    val location = transaction.original.location.get()
                    val successful = claimManager.removeClaim(location)
                    val newDatabaseConfig = claimBlocksConfigManager.get().removePosition(location)
                    claimBlocksConfigManager.save(newDatabaseConfig)
                    if (successful && event.cause.containsType(Player::class.java)) {
                        event.cause.first(Player::class.java).ifPresent { player ->
                            player.sendMessage("Removed claim!".yellow())
                        }
                    }
                }
    }

    @Listener
    fun onCrossBorderClaimEvent(event: BorderClaimEvent, @Getter("getTargetEntity") player: Player) {
        val exitClaim = event.exitClaim
        val enterClaim = event.enterClaim

        if (enterClaim.isWilderness) {
            val text = config.messages.exitClaim.apply(mapOf("claimOwner" to exitClaim.ownerName)).build()
            event.setExitMessage(text)
        } else {
            val text = config.messages.enterClaim.apply(mapOf("claimOwner" to enterClaim.ownerName)).build()
            event.setEnterMessage(text)
        }
    }

    private fun loadClaimManager() {
        if (Sponge.getPluginManager().getPlugin(GRIEF_PREVENTION_ID).isPresent) {
            claimManager = GriefPreventionClaimManager(pluginContainer, getServiceOrFail(GriefPreventionApi::class))
            return
        }

        throw RuntimeException("No supported region plugin(GriefPrevention) is available! ClaimBlocks won't be usable!")
    }

    private fun isClaimBlock(blockState: BlockState) = config.ranges.containsKey(blockState)
    private fun getRange(blockState: BlockState) = config.ranges.getValue(blockState)

    private fun loadConfig() {
        config = generalConfigManager.get()
        generalConfigManager.save(config) // regenerate config
    }
}