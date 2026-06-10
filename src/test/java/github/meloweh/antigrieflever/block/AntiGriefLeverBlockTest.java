package github.meloweh.antigrieflever.block;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AntiGriefLeverBlockTest {
    @Test
    void ownerCanActivateAnInactiveLever() {
        assertTrue(AntiGriefLeverBlock.canPlayerToggle(false, true));
    }

    @Test
    void nonOwnerCannotActivateAnInactiveLever() {
        assertFalse(AntiGriefLeverBlock.canPlayerToggle(false, false));
    }

    @Test
    void nonOwnerCanDeactivateAnActiveLever() {
        assertTrue(AntiGriefLeverBlock.canPlayerToggle(true, false));
    }
}
