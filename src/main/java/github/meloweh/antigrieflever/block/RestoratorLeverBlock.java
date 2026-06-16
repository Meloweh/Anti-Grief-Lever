package github.meloweh.antigrieflever.block;

import com.mojang.serialization.MapCodec;
import github.meloweh.antigrieflever.block.entity.RestoratorLeverBlockEntity;
import github.meloweh.antigrieflever.network.ModNetwork;
import github.meloweh.antigrieflever.restoration.RestorationSavedData;
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

public final class RestoratorLeverBlock extends LeverBlock implements EntityBlock {
    public static final MapCodec<LeverBlock> CODEC = simpleCodec(RestoratorLeverBlock::new);

    public RestoratorLeverBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<LeverBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RestoratorLeverBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player
            && level.getBlockEntity(pos) instanceof RestoratorLeverBlockEntity lever) {
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
            || !(level.getBlockEntity(pos) instanceof RestoratorLeverBlockEntity lever)) {
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
        if (!AntiGriefLeverBlock.canPlayerToggle(powered, lever.isOwner(player.getUUID()))) {
            player.displayClientMessage(Component.translatable("message.antigrieflever.not_owner"), true);
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
        if (level.isClientSide || player == null
            || !(level.getBlockEntity(pos) instanceof RestoratorLeverBlockEntity lever)
            || !AntiGriefLeverBlock.canPlayerToggle(state.getValue(POWERED), lever.isOwner(player.getUUID()))
            || (!state.getValue(POWERED) && !lever.isConfigured())) {
            return;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        boolean powered = state.getValue(POWERED);
        RestorationSavedData data = RestorationSavedData.get(serverLevel);
        if (!powered) {
            RestorationSavedData.CaptureResult result = data.capture(serverLevel, pos, lever.getDefinition());
            if (!result.success()) {
                player.displayClientMessage(
                    Component.translatable("message.antigrieflever.invalid_region", result.error()),
                    true
                );
                return;
            }
            super.pull(state, level, pos, player);
            player.displayClientMessage(
                Component.translatable("message.antigrieflever.restorator.captured", result.blocks()),
                true
            );
            return;
        }

        super.pull(state, level, pos, player);
        int restored = data.restore(serverLevel, pos);
        player.displayClientMessage(
            Component.translatable("message.antigrieflever.restorator.restored", restored),
            true
        );
    }

    @Override
    protected void onRemove(BlockState oldState, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!oldState.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            RestorationSavedData.get(serverLevel).remove(pos);
        }
        super.onRemove(oldState, level, pos, newState, movedByPiston);
    }
}
