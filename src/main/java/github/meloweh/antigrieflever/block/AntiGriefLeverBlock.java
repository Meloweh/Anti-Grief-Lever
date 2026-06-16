package github.meloweh.antigrieflever.block;

import com.mojang.serialization.MapCodec;
import github.meloweh.antigrieflever.block.entity.AntiGriefLeverBlockEntity;
import github.meloweh.antigrieflever.network.ModNetwork;
import github.meloweh.antigrieflever.protection.ProtectionSavedData;
import github.meloweh.antigrieflever.tracking.CopperCompassSavedData;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class AntiGriefLeverBlock extends LeverBlock implements EntityBlock {
    public static final MapCodec<LeverBlock> CODEC = simpleCodec(AntiGriefLeverBlock::new);

    public AntiGriefLeverBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<LeverBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AntiGriefLeverBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player
            && level.getBlockEntity(pos) instanceof AntiGriefLeverBlockEntity lever) {
            lever.assignOwnerFromPlacement(player.getUUID());
        }
    }

    @Override
    protected InteractionResult useWithoutItem(
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        BlockHitResult hitResult
    ) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)
            || !(level.getBlockEntity(pos) instanceof AntiGriefLeverBlockEntity lever)) {
            return InteractionResult.CONSUME;
        }

        if (player.isShiftKeyDown()) {
            if (!lever.isOwner(player.getUUID())) {
                player.displayClientMessage(Component.translatable("message.antigrieflever.not_owner"), true);
                return InteractionResult.CONSUME;
            }
            ModNetwork.openConfiguration(serverPlayer, lever);
            return InteractionResult.CONSUME;
        }

        boolean powered = state.getValue(POWERED);
        boolean owner = lever.isOwner(player.getUUID());
        boolean hasActiveCopperCompass = !owner && hasActiveCopperCompass(serverPlayer);
        if (!canPlayerToggleWithCompass(powered, owner, hasActiveCopperCompass)) {
            player.displayClientMessage(Component.translatable(toggleDeniedMessage(powered, owner)), true);
            return InteractionResult.CONSUME;
        }

        if (!powered && !lever.isConfigured()) {
            player.displayClientMessage(Component.translatable("message.antigrieflever.not_configured"), true);
            return InteractionResult.CONSUME;
        }

        pull(state, level, pos, player);
        return InteractionResult.CONSUME;
    }

    @Override
    public void pull(BlockState state, Level level, BlockPos pos, @Nullable Player player) {
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)
            || !(level.getBlockEntity(pos) instanceof AntiGriefLeverBlockEntity lever)) {
            return;
        }

        boolean powered = state.getValue(POWERED);
        boolean owner = lever.isOwner(player.getUUID());
        boolean hasActiveCopperCompass = !owner && hasActiveCopperCompass(serverPlayer);
        if (!canPlayerToggleWithCompass(powered, owner, hasActiveCopperCompass)
            || (!powered && !lever.isConfigured())) {
            return;
        }

        super.pull(state, level, pos, player);
        if (level instanceof ServerLevel serverLevel) {
            boolean active = level.getBlockState(pos).getValue(POWERED);
            ProtectionSavedData.get(serverLevel).setActive(pos, active);
        }
    }

    static boolean canPlayerToggle(boolean powered, boolean owner) {
        return powered || owner;
    }

    static boolean canPlayerToggleWithCompass(boolean powered, boolean owner, boolean hasActiveCopperCompass) {
        return owner || (powered && hasActiveCopperCompass);
    }

    private static boolean hasActiveCopperCompass(ServerPlayer player) {
        long now = player.getServer().overworld().getGameTime();
        return CopperCompassSavedData.get(player.getServer()).hasActiveSession(player.getUUID(), now);
    }

    private static String toggleDeniedMessage(boolean powered, boolean owner) {
        return powered && !owner
            ? "message.antigrieflever.copper_compass_required"
            : "message.antigrieflever.not_owner";
    }

    @Override
    protected void onRemove(BlockState oldState, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!oldState.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            ProtectionSavedData.get(serverLevel).remove(pos);
        }
        super.onRemove(oldState, level, pos, newState, movedByPiston);
    }
}
