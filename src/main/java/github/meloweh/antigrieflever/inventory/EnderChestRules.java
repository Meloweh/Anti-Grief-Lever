package github.meloweh.antigrieflever.inventory;

import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;

public final class EnderChestRules {
    private static final String SOPHISTICATED_BACKPACKS = "sophisticatedbackpacks";
    private static final Set<ResourceLocation> RESTRICTED_BACKPACKS = Set.of(
        backpackId("backpack"),
        backpackId("copper_backpack"),
        backpackId("iron_backpack"),
        backpackId("gold_backpack"),
        backpackId("diamond_backpack"),
        backpackId("netherite_backpack")
    );

    private EnderChestRules() {
    }

    public static boolean isRestricted(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem
            && blockItem.getBlock() instanceof ShulkerBoxBlock) {
            return true;
        }
        return isRestrictedBackpack(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    static boolean isRestrictedBackpack(ResourceLocation itemId) {
        return RESTRICTED_BACKPACKS.contains(itemId);
    }

    private static ResourceLocation backpackId(String path) {
        return ResourceLocation.fromNamespaceAndPath(SOPHISTICATED_BACKPACKS, path);
    }
}
