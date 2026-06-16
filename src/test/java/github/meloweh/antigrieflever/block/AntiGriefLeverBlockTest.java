package github.meloweh.antigrieflever.block;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AntiGriefLeverBlockTest {
    @Test
    void ownerCanActivateAnInactiveLever() {
        assertTrue(AntiGriefLeverBlock.canPlayerToggleWithCompass(false, true, false));
    }

    @Test
    void nonOwnerCannotActivateAnInactiveLever() {
        assertFalse(AntiGriefLeverBlock.canPlayerToggleWithCompass(false, false, true));
    }

    @Test
    void nonOwnerCannotDeactivateAnActiveLeverWithoutActiveCopperCompass() {
        assertFalse(AntiGriefLeverBlock.canPlayerToggleWithCompass(true, false, false));
    }

    @Test
    void nonOwnerCanDeactivateAnActiveLeverWithActiveCopperCompass() {
        assertTrue(AntiGriefLeverBlock.canPlayerToggleWithCompass(true, false, true));
    }

    @Test
    void restoratorLegacyToggleRuleStillAllowsNonOwnerDeactivation() {
        assertTrue(AntiGriefLeverBlock.canPlayerToggle(true, false));
    }
}
