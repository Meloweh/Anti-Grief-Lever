package github.meloweh.antigrieflever.block.entity;

import github.meloweh.antigrieflever.Antigrieflever;
import github.meloweh.antigrieflever.block.AntiGriefLeverBlock;
import github.meloweh.antigrieflever.protection.ProtectionSavedData;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class AntiGriefLeverBlockEntity extends BlockEntity {
    @Nullable
    private UUID owner;
    private String definition = "";

    public AntiGriefLeverBlockEntity(BlockPos pos, BlockState state) {
        super(Antigrieflever.ANTI_GRIEF_LEVER_BLOCK_ENTITY.get(), pos, state);
    }

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    public boolean isOwner(UUID player) {
        return owner != null && owner.equals(player);
    }

    public void assignOwnerFromPlacement(UUID placer) {
        this.owner = placer;
        setChanged();
        syncClaim();
    }

    public String getDefinition() {
        return definition;
    }

    public boolean isConfigured() {
        return !definition.isBlank();
    }

    public void setDefinition(String definition) {
        this.definition = definition;
        setChanged();
        syncClaim();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        syncClaim();
    }

    private void syncClaim() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ProtectionSavedData data = ProtectionSavedData.get(serverLevel);
        if (owner == null || definition.isBlank()) {
            data.remove(worldPosition);
            return;
        }
        boolean active = getBlockState().hasProperty(AntiGriefLeverBlock.POWERED)
            && getBlockState().getValue(AntiGriefLeverBlock.POWERED);
        data.upsert(worldPosition, owner, definition, active);
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
