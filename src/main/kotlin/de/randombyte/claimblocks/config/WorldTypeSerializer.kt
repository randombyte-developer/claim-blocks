package de.randombyte.claimblocks.config

import com.google.common.reflect.TypeToken
import de.randombyte.kosp.extensions.toUUID
import de.randombyte.kosp.extensions.typeToken
import ninja.leaping.configurate.ConfigurationNode
import ninja.leaping.configurate.objectmapping.ObjectMappingException
import ninja.leaping.configurate.objectmapping.serialize.TypeSerializer
import org.spongepowered.api.Sponge
import org.spongepowered.api.world.World
import java.util.*

object WorldTypeSerializer : TypeSerializer<World> {
    override fun deserialize(type: TypeToken<*>, node: ConfigurationNode): World {
        return Sponge.getServer().getWorld(node.getValue(UUID::class.typeToken)).orElseThrow {
            ObjectMappingException("World '${node.string.toUUID()}' is not available")
        }
    }

    override fun serialize(type: TypeToken<*>, world: World, node: ConfigurationNode) {
        node.setValue(UUID::class.typeToken, world.uniqueId)
    }
}