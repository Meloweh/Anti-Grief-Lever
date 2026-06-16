package github.meloweh.antigrieflever.item;

import github.meloweh.antigrieflever.Antigrieflever;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public final class DesertSandwichItem extends Item {
    private static final String WATER_TICKS = "AntigriefleverDesertSandwichWaterTicks";
    private static final int WATER_LIFETIME_TICKS = 10 * 20;
    private static final int SPONGE_DEPTH = 6;
    private static final int SPONGE_MAX_BLOCKS = 65;
    private static final Direction[] ALL_DIRECTIONS = Direction.values();

    public DesertSandwichItem(Properties properties) {
        super(properties);
    }

    public static FoodProperties foodProperties() {
        return new FoodProperties.Builder()
            .nutrition(1)
            .saturationModifier(0.5F)
            .effect(() -> new MobEffectInstance(MobEffects.CONFUSION, 2 * 20), 1.0F)
            .effect(() -> new MobEffectInstance(Antigrieflever.DEMORALIZATION_BOOST, 60 * 20), 1.0F)
            .build();
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide && entity instanceof Player player) {
            player.hurt(player.damageSources().source(Antigrieflever.DESERT_SANDWICH_DAMAGE), 1.0F);
            player.sendSystemMessage(Component.translatable("message.antigrieflever.desert_sandwich.choked"));
        }
        return result;
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return false;
        }

        CompoundTag data = entity.getPersistentData();
        int waterTicks = data.getInt(WATER_TICKS);
        if (waterTicks <= 0 && !isInWater(entity)) {
            return false;
        }

        waterTicks++;
        data.putInt(WATER_TICKS, waterTicks);
        entity.setNeverPickUp();

        boolean absorbedWater = absorbWater(level, entity.blockPosition());
        if (absorbedWater && waterTicks == 1) {
            level.playSound(null, entity.blockPosition(), SoundEvents.SPONGE_ABSORB, SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        if (waterTicks >= WATER_LIFETIME_TICKS) {
            notifyThrower(entity);
            entity.discard();
            return true;
        }
        return false;
    }

    private static boolean isInWater(ItemEntity entity) {
        return entity.isInWater() && entity.getFluidHeight(FluidTags.WATER) > 0.1F;
    }

    private static boolean absorbWater(ServerLevel level, BlockPos origin) {
        return BlockPos.breadthFirstTraversal(
            origin,
            SPONGE_DEPTH,
            SPONGE_MAX_BLOCKS,
            DesertSandwichItem::visitAdjacentPositions,
            pos -> tryAbsorbWaterAt(level, origin, pos)
        ) > 1;
    }

    private static void visitAdjacentPositions(BlockPos pos, Consumer<BlockPos> visitor) {
        for (Direction direction : ALL_DIRECTIONS) {
            visitor.accept(pos.relative(direction));
        }
    }

    private static boolean tryAbsorbWaterAt(ServerLevel level, BlockPos origin, BlockPos pos) {
        if (pos.equals(origin)) {
            return true;
        }

        BlockState blockState = level.getBlockState(pos);
        FluidState fluidState = level.getFluidState(pos);
        if (!fluidState.is(FluidTags.WATER)) {
            return false;
        }

        if (blockState.getBlock() instanceof BucketPickup bucketPickup
            && !bucketPickup.pickupBlock(null, level, pos, blockState).isEmpty()) {
            return true;
        }

        if (blockState.getBlock() instanceof LiquidBlock) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            return true;
        }

        if (
            !blockState.is(Blocks.KELP)
                && !blockState.is(Blocks.KELP_PLANT)
                && !blockState.is(Blocks.SEAGRASS)
                && !blockState.is(Blocks.TALL_SEAGRASS)
        ) {
            return false;
        }

        BlockEntity blockEntity = blockState.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        Block.dropResources(blockState, level, pos, blockEntity);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        return true;
    }

    private static void notifyThrower(ItemEntity entity) {
        Entity owner = entity.getOwner();
        if (owner instanceof ServerPlayer player) {
            player.sendSystemMessage(
                Component.translatable("message.antigrieflever.desert_sandwich.revitalized")
            );
        }
    }
}
