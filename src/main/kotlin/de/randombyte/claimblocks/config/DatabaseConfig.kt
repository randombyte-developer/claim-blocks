package de.randombyte.claimblocks.config

import com.flowpowered.math.vector.Vector3i
import ninja.leaping.configurate.objectmapping.Setting
import ninja.leaping.configurate.objectmapping.serialize.ConfigSerializable
import org.spongepowered.api.world.Location
import org.spongepowered.api.world.World

@ConfigSerializable
internal data class DatabaseConfig(
        @Setting val locations: Map<World, List<Claim>> = emptyMap()
) {

    // we have to do it this way and not Map<Vector3i, Int> because of SpongeCommon/1346
    @ConfigSerializable
    class Claim(
            @Setting val position: Vector3i = Vector3i.ONE.negate(),
            @Setting val range: Int = -1
    )

    fun addPosition(location: Location<World>, range: Int): DatabaseConfig = copy(locations = locations
            .plus(location.extent to
                    (this.locations[location.extent] ?: emptyList<Claim>())
                            .plus(Claim(location.blockPosition, range))))

    fun getRange(location: Location<World>): Int? = locations[location.extent]?.firstOrNull { it.position == location.blockPosition }?.range

    fun removePosition(location: Location<World>): DatabaseConfig = copy(locations = locations
            .plus(location.extent to
                    (this.locations[location.extent] ?: emptyList<Claim>())
                            .filterNot { it.position == location.blockPosition }))
}