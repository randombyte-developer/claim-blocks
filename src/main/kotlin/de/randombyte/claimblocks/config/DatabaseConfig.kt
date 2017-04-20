package de.randombyte.claimblocks.config

import com.flowpowered.math.vector.Vector3i
import ninja.leaping.configurate.objectmapping.Setting
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable
import org.spongepowered.api.world.Location
import org.spongepowered.api.world.World

@ConfigSerializable
internal data class DatabaseConfig(
        @Setting val locations: Map<World, List<Vector3i>> = emptyMap()
) {

    fun addPosition(location: Location<World>): DatabaseConfig =
            copy(locations = locations.plus(location.extent to ((this.locations[location.extent] ?: emptyList<Vector3i>()).plus(location.blockPosition)).uniqueElements()))

    fun removePosition(location: Location<World>): DatabaseConfig =
            copy(locations = locations.plus(location.extent to ((this.locations[location.extent] ?: emptyList<Vector3i>()).minus(location.blockPosition)).uniqueElements()))

    fun contains(location: Location<World>) = locations[location.extent]?.contains(location.blockPosition) ?: false

    private fun <T> List<T>.uniqueElements() = toSet().toList()
}