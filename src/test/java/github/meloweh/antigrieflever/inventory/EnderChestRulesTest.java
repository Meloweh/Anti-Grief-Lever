package github.meloweh.antigrieflever.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

class EnderChestRulesTest {
    @Test
    void restrictsAllConfiguredSophisticatedBackpacks() {
        List<String> paths = List.of(
            "backpack",
            "copper_backpack",
            "iron_backpack",
            "gold_backpack",
            "diamond_backpack",
            "netherite_backpack"
        );

        for (String path : paths) {
            assertTrue(EnderChestRules.isRestrictedBackpack(
                ResourceLocation.fromNamespaceAndPath("sophisticatedbackpacks", path)
            ));
        }
        assertFalse(EnderChestRules.isRestrictedBackpack(
            ResourceLocation.fromNamespaceAndPath("sophisticatedbackpacks", "feeding_upgrade")
        ));
    }

    @Test
    void restrictsAllShulkerBoxVariants() {
        assertTrue(EnderChestRules.isRestricted(new ItemStack(Items.SHULKER_BOX)));
        assertTrue(EnderChestRules.isRestricted(new ItemStack(Items.BLUE_SHULKER_BOX)));
        assertFalse(EnderChestRules.isRestricted(new ItemStack(Items.CHEST)));
    }

    @Test
    void enderChestSlotsRejectRestrictedItemsWithoutChangingNormalSlots() {
        ItemStack shulkerBox = new ItemStack(Items.SHULKER_BOX);
        Slot enderChestSlot = new Slot(new PlayerEnderChestContainer(), 0, 0, 0);
        Slot normalSlot = new Slot(new SimpleContainer(1), 0, 0, 0);

        assertFalse(enderChestSlot.mayPlace(shulkerBox));
        assertEquals(0, enderChestSlot.getMaxStackSize(shulkerBox));
        assertTrue(enderChestSlot.mayPlace(new ItemStack(Items.CHEST)));
        assertTrue(normalSlot.mayPlace(shulkerBox));
    }
}
