package github.meloweh.antigrieflever.mixin;

import github.meloweh.antigrieflever.Config;
import github.meloweh.antigrieflever.inventory.EnderChestRules;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class SlotMixin {
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void antigrieflever$restrictEnderChestPlacement(
        ItemStack stack,
        CallbackInfoReturnable<Boolean> callback
    ) {
        if (isRestrictedEnderChestSlot(stack)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "getMaxStackSize(Lnet/minecraft/world/item/ItemStack;)I", at = @At("HEAD"), cancellable = true)
    private void antigrieflever$restrictEnderChestMerging(
        ItemStack stack,
        CallbackInfoReturnable<Integer> callback
    ) {
        if (isRestrictedEnderChestSlot(stack)) {
            callback.setReturnValue(0);
        }
    }

    @Inject(method = "allowModification", at = @At("HEAD"), cancellable = true)
    private void antigrieflever$allowExistingItemRemoval(
        Player player,
        CallbackInfoReturnable<Boolean> callback
    ) {
        Slot self = (Slot) (Object) this;
        if (isRestrictedEnderChestSlot(self.getItem())) {
            callback.setReturnValue(self.mayPickup(player));
        }
    }

    private boolean isRestrictedEnderChestSlot(ItemStack stack) {
        Slot self = (Slot) (Object) this;
        return Config.restrictPortableStorageInEnderChests()
            && self.container instanceof PlayerEnderChestContainer
            && EnderChestRules.isRestricted(stack);
    }
}
