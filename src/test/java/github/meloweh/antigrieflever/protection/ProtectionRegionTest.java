package github.meloweh.antigrieflever.protection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class ProtectionRegionTest {
    private static final BlockPos CENTER = new BlockPos(10, 64, -10);

    @Test
    void parsesSphericalRadius() {
        ProtectionRegion.ParseResult result = ProtectionRegion.parse("[4]", CENTER, 96);

        assertTrue(result.valid());
        assertEquals("[4]", result.canonicalDefinition());
        assertEquals(ProtectionRegion.Type.SPHERE, result.region().type());
        assertTrue(result.region().contains(CENTER.offset(4, 0, 0), CENTER));
        assertFalse(result.region().contains(CENTER.offset(4, 1, 0), CENTER));
    }

    @Test
    void parsesCenteredCuboidWithExactDimensions() {
        ProtectionRegion.ParseResult result = ProtectionRegion.parse("[4,2]", CENTER, 96);

        assertTrue(result.valid());
        ProtectionRegion region = result.region();
        assertEquals(4, region.maxX() - region.minX() + 1);
        assertEquals(2, region.maxY() - region.minY() + 1);
        assertEquals(4, region.maxZ() - region.minZ() + 1);
        assertTrue(region.contains(CENTER, CENTER));
    }

    @Test
    void normalizesAbsoluteCuboidCoordinates() {
        ProtectionRegion.ParseResult result =
            ProtectionRegion.parse("[12,66,-8,8,62,-12]", CENTER, 96);

        assertTrue(result.valid());
        assertEquals(8, result.region().minX());
        assertEquals(62, result.region().minY());
        assertEquals(-12, result.region().minZ());
        assertEquals(12, result.region().maxX());
        assertEquals(66, result.region().maxY());
        assertEquals(-8, result.region().maxZ());
    }

    @Test
    void rejectsRegionsBeyondConfiguredMaximum() {
        assertFalse(ProtectionRegion.parse("[97]", CENTER, 96).valid());
        assertFalse(ProtectionRegion.parse("[10,64,-10,107,64,-10]", CENTER, 96).valid());
        assertTrue(ProtectionRegion.parse("[96]", CENTER, 96).valid());
    }

    @Test
    void nonOwnerPlacementsAreUnprotectedUntilTheClaimIsReactivated() {
        ProtectionSavedData data = new ProtectionSavedData();
        UUID owner = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        BlockPos placedBlock = CENTER.offset(1, 0, 0);

        data.upsert(CENTER, owner, "[5]", true);
        assertFalse(data.canDestroy(placedBlock, guest));

        data.recordPlayerPlacement(placedBlock, guest);
        assertTrue(data.canDestroy(placedBlock, guest));
        assertTrue(data.canDestroy(placedBlock, null));

        data.upsert(CENTER, owner, "[5]", true);
        assertTrue(data.canDestroy(placedBlock, guest));

        data.setActive(CENTER, false);
        data.setActive(CENTER, true);
        assertFalse(data.canDestroy(placedBlock, guest));
    }

    @Test
    void ownerPlacementsRemainProtected() {
        ProtectionSavedData data = new ProtectionSavedData();
        UUID owner = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        BlockPos placedBlock = CENTER.offset(1, 0, 0);

        data.upsert(CENTER, owner, "[5]", true);
        data.recordPlayerPlacement(placedBlock, owner);

        assertFalse(data.canDestroy(placedBlock, guest));
        assertTrue(data.canDestroy(placedBlock, owner));
    }

    @Test
    void activationNeverChangesThePlacementOwner() {
        ProtectionSavedData data = new ProtectionSavedData();
        UUID placer = UUID.randomUUID();
        UUID otherPlayer = UUID.randomUUID();
        BlockPos protectedBlock = CENTER.offset(1, 0, 0);

        data.upsert(CENTER, placer, "[5]", false);
        data.setActive(CENTER, true);

        assertTrue(data.canDestroy(protectedBlock, placer));
        assertFalse(data.canDestroy(protectedBlock, otherPlayer));

        data.setActive(CENTER, false);
        data.setActive(CENTER, true);

        assertTrue(data.canDestroy(protectedBlock, placer));
        assertFalse(data.canDestroy(protectedBlock, otherPlayer));
    }
}
