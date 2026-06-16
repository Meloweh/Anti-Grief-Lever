package github.meloweh.antigrieflever.mixin;

import github.meloweh.antigrieflever.protection.ProtectionSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {
    @Inject(method = "suckInItems", at = @At("HEAD"), cancellable = true)
    private static void antigrieflever$preventProtectedContainerExtraction(
        Level level,
        Hopper hopper,
        CallbackInfoReturnable<Boolean> callback
    ) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockPos sourcePos = BlockPos.containing(hopper.getLevelX(), hopper.getLevelY() + 1.0, hopper.getLevelZ());
        ProtectionSavedData data = ProtectionSavedData.get(serverLevel);
        boolean allowed = hopper instanceof BlockEntity blockEntity
            ? data.canAutomateContainerAccess(sourcePos, blockEntity.getBlockPos())
            : data.canAccessContainer(sourcePos, null);
        if (!allowed) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "ejectItems", at = @At("HEAD"), cancellable = true)
    private static void antigrieflever$preventProtectedContainerInsertion(
        Level level,
        BlockPos hopperPos,
        HopperBlockEntity hopper,
        CallbackInfoReturnable<Boolean> callback
    ) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockPos targetPos = hopperPos.relative(level.getBlockState(hopperPos).getValue(HopperBlock.FACING));
        if (!ProtectionSavedData.get(serverLevel).canAutomateContainerAccess(targetPos, hopperPos)) {
            callback.setReturnValue(false);
        }
    }
}
