package github.meloweh.antigrieflever.warp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class WarpStoneSavedDataTest {
    @Test
    void linksOnlyUnlinkedTargetsAndSwitchingUnlinksPreviousPartner() {
        WarpStoneSavedData data = new WarpStoneSavedData();
        WarpStoneKey first = key(0);
        WarpStoneKey second = key(10);
        WarpStoneKey third = key(20);

        data.registerIfAbsent(first, "First", null);
        data.registerIfAbsent(second, "Second", null);
        data.registerIfAbsent(third, "Third", null);

        assertTrue(data.link(first, "Renamed First", second));
        assertEquals(second, data.get(first).orElseThrow().linked());
        assertEquals(first, data.get(second).orElseThrow().linked());
        assertEquals(List.of(third), data.availableTargets(first).stream().map(WarpStoneSavedData.Entry::key).toList());

        assertTrue(data.link(first, "First Again", third));
        assertEquals(third, data.get(first).orElseThrow().linked());
        assertNull(data.get(second).orElseThrow().linked());
        assertEquals(first, data.get(third).orElseThrow().linked());
    }

    @Test
    void blankNamesFallBackToDefaultName() {
        assertEquals(WarpStoneSavedData.DEFAULT_NAME, WarpStoneSavedData.sanitizeName("   "));
    }

    private static WarpStoneKey key(int x) {
        return new WarpStoneKey(Level.OVERWORLD, new BlockPos(x, 64, 0));
    }
}
