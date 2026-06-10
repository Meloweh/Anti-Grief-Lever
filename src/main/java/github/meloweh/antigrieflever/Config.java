package github.meloweh.antigrieflever;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    public static final int ABSOLUTE_MAX_PROTECTION_RADIUS = 1024;
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MAX_PROTECTION_RADIUS = BUILDER
        .comment("Maximum distance in blocks from a lever to any edge of its protection region.")
        .defineInRange("maxProtectionRadius", 96, 1, ABSOLUTE_MAX_PROTECTION_RADIUS);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }

    public static int maxProtectionRadius() {
        return MAX_PROTECTION_RADIUS.get();
    }
}
