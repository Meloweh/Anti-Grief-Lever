package github.meloweh.antigrieflever.block.entity;

import github.meloweh.antigrieflever.Antigrieflever;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class RestoratorLeverBlockEntity extends BlockEntity implements ConfigurableRegionBlockEntity {
    @Nullable
    private UUID owner;
    private String definition = "";

    public RestoratorLeverBlockEntity(BlockPos pos, BlockState state) {
        super(Antigrieflever.RESTORATOR_LEVER_BLOCK_ENTITY.get(), pos, state);
    }

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    @Override
    public boolean isOwner(UUID player) {
        return owner != null && owner.equals(player);
    }

    public void assignOwnerFromPlacement(UUID placer) {
        this.owner = placer;
        setChanged();
    }

    @Override
    public String getDefinition() {
        return definition;
    }

    @Override
    public boolean isConfigured() {
        return !definition.isBlank();
    }

    @Override
    public void setDefinition(String definition) {
        this.definition = definition;
        setChanged();
    }

    @Override
    public String configTitleKey() {
        return "screen.antigrieflever.restorator.title";
    }

    @Override
    public String regionLabelKey() {
        return "screen.antigrieflever.restorator.region";
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("Definition", definition);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        definition = tag.getString("Definition");
    }
}
