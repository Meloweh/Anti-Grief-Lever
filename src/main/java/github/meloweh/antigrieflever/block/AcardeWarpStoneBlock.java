package github.meloweh.antigrieflever.block;

import com.mojang.serialization.MapCodec;
import github.meloweh.antigrieflever.Antigrieflever;
import github.meloweh.antigrieflever.block.entity.AcardeWarpStoneBlockEntity;
import github.meloweh.antigrieflever.network.ModNetwork;
import github.meloweh.antigrieflever.warp.WarpStoneKey;
import github.meloweh.antigrieflever.warp.WarpStoneSavedData;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class AcardeWarpStoneBlock extends Block implements EntityBlock {
    public static final MapCodec<AcardeWarpStoneBlock> CODEC = simpleCodec(AcardeWarpStoneBlock::new);

    public AcardeWarpStoneBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AcardeWarpStoneBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == Antigrieflever.ACARDE_WARP_STONE_BLOCK_ENTITY.get()
            ? (tickerLevel, tickerPos, tickerState, blockEntity) ->
                AcardeWarpStoneBlockEntity.serverTick(
                    tickerLevel,
                    tickerPos,
                    tickerState,
                    (AcardeWarpStoneBlockEntity) blockEntity
                )
            : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel
            && level.getBlockEntity(pos) instanceof AcardeWarpStoneBlockEntity stone) {
            stone.registerAfterPlacement(serverLevel);
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
        if (player instanceof ServerPlayer serverPlayer && player.isCreative()
            && level.getBlockEntity(pos) instanceof AcardeWarpStoneBlockEntity stone) {
            ModNetwork.openWarpStoneConfiguration(serverPlayer, stone);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player
            && level.getBlockEntity(pos) instanceof AcardeWarpStoneBlockEntity stone) {
            stone.tryTeleport(player);
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    protected void onRemove(BlockState oldState, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!oldState.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            WarpStoneSavedData data = WarpStoneSavedData.get(serverLevel);
            WarpStoneKey key = WarpStoneKey.of(serverLevel, pos);
            WarpStoneKey linked = data.get(key).map(WarpStoneSavedData.Entry::linked).orElse(null);
            data.remove(key);
            if (linked != null) {
                ServerLevel linkedLevel = serverLevel.getServer().getLevel(linked.dimension());
                if (linkedLevel != null
                    && linkedLevel.getBlockEntity(linked.pos()) instanceof AcardeWarpStoneBlockEntity linkedStone) {
                    data.get(linked).ifPresent(linkedStone::applySavedEntry);
                }
            }
        }
        super.onRemove(oldState, level, pos, newState, movedByPiston);
    }
}
