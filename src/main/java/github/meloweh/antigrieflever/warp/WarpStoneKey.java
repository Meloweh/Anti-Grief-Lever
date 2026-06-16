package github.meloweh.antigrieflever.warp;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public record WarpStoneKey(ResourceKey<Level> dimension, BlockPos pos) {
    public WarpStoneKey {
        pos = pos.immutable();
    }

    public static WarpStoneKey of(ServerLevel level, BlockPos pos) {
        return new WarpStoneKey(level.dimension(), pos);
    }

    public static ResourceKey<Level> dimensionFromString(String value) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(value));
    }

    public String dimensionName() {
        return dimension.location().toString();
    }
}
