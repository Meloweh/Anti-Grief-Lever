package github.meloweh.antigrieflever.block.entity;

import github.meloweh.antigrieflever.Antigrieflever;
import github.meloweh.antigrieflever.warp.WarpStoneKey;
import github.meloweh.antigrieflever.warp.WarpStoneSavedData;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class AcardeWarpStoneBlockEntity extends BlockEntity {
    private String customName = WarpStoneSavedData.DEFAULT_NAME;
    @Nullable
    private WarpStoneKey linkedTarget;
    private final Set<UUID> standingPlayers = new HashSet<>();

    public AcardeWarpStoneBlockEntity(BlockPos pos, BlockState state) {
        super(Antigrieflever.ACARDE_WARP_STONE_BLOCK_ENTITY.get(), pos, state);
    }

    public String getCustomName() {
        return customName;
    }

    @Nullable
    public WarpStoneKey getLinkedTarget() {
        return linkedTarget;
    }

    public void registerAfterPlacement(ServerLevel level) {
        WarpStoneKey key = WarpStoneKey.of(level, worldPosition);
        WarpStoneSavedData.Entry entry = WarpStoneSavedData.get(level).registerIfAbsent(key, defaultName(worldPosition), null);
        applySavedEntry(entry);
    }

    public void applySavedEntry(WarpStoneSavedData.Entry entry) {
        this.customName = entry.name();
        this.linkedTarget = entry.linked();
        setChanged();
    }

    public void markPlayerStanding(UUID player) {
        standingPlayers.add(player);
    }

    public void tryTeleport(ServerPlayer player) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        UUID playerId = player.getUUID();
        if (!standingPlayers.add(playerId)) {
            return;
        }

        WarpStoneKey source = WarpStoneKey.of(serverLevel, worldPosition);
        WarpStoneSavedData data = WarpStoneSavedData.get(serverLevel);
        WarpStoneSavedData.Entry sourceEntry = data.get(source).orElseGet(() -> data.registerIfAbsent(source, customName, linkedTarget));
        applySavedEntry(sourceEntry);
        if (sourceEntry.linked() == null) {
            return;
        }

        WarpStoneKey target = sourceEntry.linked();
        ServerLevel targetLevel = serverLevel.getServer().getLevel(target.dimension());
        if (targetLevel == null) {
            data.unlink(source);
            applySavedEntry(data.get(source).orElseThrow());
            player.displayClientMessage(Component.translatable("message.antigrieflever.warp_stone.missing_target"), true);
            return;
        }

        if (!(targetLevel.getBlockEntity(target.pos()) instanceof AcardeWarpStoneBlockEntity targetStone)
            || !targetLevel.getBlockState(target.pos()).is(Antigrieflever.ACARDE_WARP_STONE.get())) {
            data.remove(target);
            applySavedEntry(data.get(source).orElseThrow());
            player.displayClientMessage(Component.translatable("message.antigrieflever.warp_stone.missing_target"), true);
            return;
        }

        WarpStoneSavedData.Entry targetEntry = data.get(target).orElse(null);
        if (targetEntry == null || !source.equals(targetEntry.linked())) {
            data.unlink(source);
            applySavedEntry(data.get(source).orElseThrow());
            player.displayClientMessage(Component.translatable("message.antigrieflever.warp_stone.missing_target"), true);
            return;
        }

        markPlayerStanding(playerId);
        targetStone.markPlayerStanding(playerId);
        targetStone.applySavedEntry(targetEntry);
        player.teleportTo(
            targetLevel,
            target.pos().getX() + 0.5D,
            target.pos().getY() + 1.0D,
            target.pos().getZ() + 0.5D,
            Set.of(),
            player.getYRot(),
            player.getXRot()
        );
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            WarpStoneKey key = WarpStoneKey.of(serverLevel, worldPosition);
            WarpStoneSavedData data = WarpStoneSavedData.get(serverLevel);
            WarpStoneSavedData.Entry entry = data.get(key).orElseGet(() -> data.registerIfAbsent(key, customName, linkedTarget));
            applySavedEntry(entry);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AcardeWarpStoneBlockEntity stone) {
        if (level.isClientSide || stone.standingPlayers.isEmpty()) {
            return;
        }

        AABB standingBox = new AABB(
            pos.getX(),
            pos.getY() + 1.0D,
            pos.getZ(),
            pos.getX() + 1.0D,
            pos.getY() + 2.25D,
            pos.getZ() + 1.0D
        );
        Set<UUID> stillStanding = new HashSet<>();
        for (Player player : level.getEntitiesOfClass(Player.class, standingBox)) {
            stillStanding.add(player.getUUID());
        }
        stone.standingPlayers.retainAll(stillStanding);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("CustomName", customName);
        if (linkedTarget != null) {
            tag.putString("LinkedDimension", linkedTarget.dimensionName());
            tag.putLong("LinkedPos", linkedTarget.pos().asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        customName = WarpStoneSavedData.sanitizeName(tag.getString("CustomName"));
        if (tag.contains("LinkedDimension", Tag.TAG_STRING)) {
            try {
                linkedTarget = new WarpStoneKey(
                    WarpStoneKey.dimensionFromString(tag.getString("LinkedDimension")),
                    BlockPos.of(tag.getLong("LinkedPos"))
                );
            } catch (IllegalArgumentException ignored) {
                linkedTarget = null;
            }
        } else {
            linkedTarget = null;
        }
    }

    private static String defaultName(BlockPos pos) {
        return WarpStoneSavedData.DEFAULT_NAME + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}
