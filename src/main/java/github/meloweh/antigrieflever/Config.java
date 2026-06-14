package github.meloweh.antigrieflever;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    public static final int ABSOLUTE_MAX_PROTECTION_RADIUS = 1024;
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MAX_PROTECTION_RADIUS = BUILDER
        .comment("Maximum distance in blocks from a lever to any edge of its protection region.")
        .defineInRange("maxProtectionRadius", 96, 1, ABSOLUTE_MAX_PROTECTION_RADIUS);

    public static final ModConfigSpec.BooleanValue RESTRICT_PORTABLE_STORAGE_IN_ENDER_CHESTS = BUILDER
        .comment(
            "Prevents shulker boxes and supported Sophisticated Backpacks items from being inserted into ender chests."
        )
        .define("restrictPortableStorageInEnderChests", true);

    public static final ModConfigSpec.IntValue PLAYER_FINDER_ACTIVE_MINUTES = BUILDER
        .comment("How many minutes a player finder compass tracks its target after activation.")
        .defineInRange("playerFinderActiveMinutes", 3, 1, 1440);

    public static final ModConfigSpec.IntValue PLAYER_FINDER_UPDATE_SECONDS = BUILDER
        .comment("How often, in seconds, an active player finder reports its remaining time and plays a tick sound.")
        .defineInRange("playerFinderUpdateSeconds", 60, 1, 3600);

    public static final ModConfigSpec.IntValue PLAYER_FINDER_COOLDOWN_DAYS = BUILDER
        .comment("Player-wide cooldown in Minecraft days after a player finder tracking cycle ends.")
        .defineInRange("playerFinderCooldownDays", 10, 0, 3650);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }

    public static int maxProtectionRadius() {
        return MAX_PROTECTION_RADIUS.get();
    }

    public static boolean restrictPortableStorageInEnderChests() {
        return RESTRICT_PORTABLE_STORAGE_IN_ENDER_CHESTS.get();
    }

    public static long playerFinderActiveTicks() {
        return PLAYER_FINDER_ACTIVE_MINUTES.get() * 60L * 20L;
    }

    public static long playerFinderUpdateTicks() {
        return PLAYER_FINDER_UPDATE_SECONDS.get() * 20L;
    }

    public static long playerFinderCooldownTicks() {
        return PLAYER_FINDER_COOLDOWN_DAYS.get() * 24_000L;
    }
}
