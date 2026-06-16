package github.meloweh.antigrieflever.block.entity;

import java.util.UUID;
import net.minecraft.core.BlockPos;

public interface ConfigurableRegionBlockEntity {
    BlockPos getBlockPos();

    boolean isOwner(UUID player);

    String getDefinition();

    boolean isConfigured();

    void setDefinition(String definition);

    String configTitleKey();

    String regionLabelKey();
}
