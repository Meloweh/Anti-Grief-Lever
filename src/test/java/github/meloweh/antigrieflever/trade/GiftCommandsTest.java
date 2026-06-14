package github.meloweh.antigrieflever.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

class GiftCommandsTest {
    @Test
    void creativeGiftDoesNotRequireSenderInventory() {
        GiftCommands.GiftRequest request =
            new GiftCommands.GiftRequest(new ItemStack(Items.DIAMOND, 32), 32, 10_000L, true);

        assertFalse(request.requiresInventory());
        assertEquals(1, request.item().getCount());
    }

    @Test
    void survivalGiftRequiresSenderInventory() {
        GiftCommands.GiftRequest request =
            new GiftCommands.GiftRequest(new ItemStack(Items.DIAMOND), 2, 10_000L, false);

        assertTrue(request.requiresInventory());
    }

    @Test
    void giftExpiresAtItsDeadline() {
        GiftCommands.GiftRequest request =
            new GiftCommands.GiftRequest(new ItemStack(Items.DIAMOND), 1, 10_000L, false);

        assertFalse(request.isExpired(9_999L));
        assertTrue(request.isExpired(10_000L));
    }
}
