package github.meloweh.antigrieflever.protection;

import github.meloweh.antigrieflever.Config;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

public final class ProtectionSavedData extends SavedData {
    private static final String DATA_NAME = "antigrieflever_claims";
    private static final Factory<ProtectionSavedData> FACTORY =
        new Factory<>(ProtectionSavedData::new, ProtectionSavedData::load);

    private final Map<Long, Claim> claims = new HashMap<>();
    private final Map<Long, Set<Long>> claimsByChunk = new HashMap<>();

    public static ProtectionSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public Collection<Claim> claimsAt(BlockPos pos) {
        Set<Long> candidates = claimsByChunk.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<Claim> result = new ArrayList<>();
        for (long sourceKey : candidates) {
            Claim claim = claims.get(sourceKey);
            if (claim != null && claim.isEffective() && claim.protects(pos)) {
                result.add(claim);
            }
        }
        return result;
    }

    public boolean canDestroy(BlockPos pos, @Nullable UUID actor) {
        for (Claim claim : claimsAt(pos)) {
            if (claim.isBlacklisted(pos)) {
                continue;
            }
            if (actor == null || !claim.owner().equals(actor)) {
                return false;
            }
        }
        return true;
    }

    public boolean canAccessContainer(BlockPos pos, @Nullable UUID actor) {
        return canDestroy(pos, actor);
    }

    public boolean canAutomateContainerAccess(BlockPos containerPos, BlockPos accessorPos) {
        for (Claim claim : claimsAt(containerPos)) {
            if (claim.isBlacklisted(containerPos)) {
                continue;
            }
            if (!claim.protects(accessorPos) || claim.isBlacklisted(accessorPos)) {
                return false;
            }
        }
        return true;
    }

    public boolean isProtected(BlockPos pos) {
        return !claimsAt(pos).isEmpty();
    }

    public Optional<BlockPos> nearestActiveSourceNotOwnedBy(BlockPos origin, UUID excludedOwner) {
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Claim claim : claims.values()) {
            if (!claim.isEffective() || claim.owner().equals(excludedOwner)) {
                continue;
            }

            double distance = claim.source().distSqr(origin);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = claim.source();
            }
        }
        return Optional.ofNullable(nearest);
    }

    public void upsert(BlockPos source, UUID owner, String definition, boolean active) {
        ProtectionRegion.ParseResult parsed =
            ProtectionRegion.parse(definition, source, Config.ABSOLUTE_MAX_PROTECTION_RADIUS);
        if (!parsed.valid()) {
            remove(source);
            return;
        }

        long key = source.asLong();
        Claim existing = claims.get(key);
        removeFromIndex(existing);
        Set<Long> blacklistedPositions = existing != null
            && existing.active()
            && active
            && existing.owner().equals(owner)
            ? existing.blacklistedPositions()
            : Set.of();
        Claim claim = new Claim(
            source.immutable(),
            owner,
            parsed.canonicalDefinition(),
            parsed.region(),
            active,
            blacklistedPositions
        );
        claims.put(key, claim);
        addToIndex(claim);
        setDirty();
    }

    public void setActive(BlockPos source, boolean active) {
        long key = source.asLong();
        Claim old = claims.get(key);
        if (old == null) {
            return;
        }
        if (old.active() == active && (active || old.blacklistedPositions().isEmpty())) {
            return;
        }
        claims.put(key, old.withActiveAndResetBlacklist(active));
        setDirty();
    }

    public void recordPlayerPlacement(BlockPos pos, UUID player) {
        boolean changed = false;
        for (Claim claim : claimsAt(pos)) {
            if (!claim.owner().equals(player) && !claim.isBlacklisted(pos)) {
                claims.put(claim.source().asLong(), claim.withBlacklisted(pos));
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    public void remove(BlockPos source) {
        Claim removed = claims.remove(source.asLong());
        if (removed != null) {
            removeFromIndex(removed);
            setDirty();
        }
    }

    private void addToIndex(Claim claim) {
        int minChunkX = claim.region().minX() >> 4;
        int maxChunkX = claim.region().maxX() >> 4;
        int minChunkZ = claim.region().minZ() >> 4;
        int maxChunkZ = claim.region().maxZ() >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                claimsByChunk.computeIfAbsent(ChunkPos.asLong(chunkX, chunkZ), ignored -> new HashSet<>())
                    .add(claim.source().asLong());
            }
        }
        claimsByChunk.computeIfAbsent(
            ChunkPos.asLong(claim.source().getX() >> 4, claim.source().getZ() >> 4),
            ignored -> new HashSet<>()
        ).add(claim.source().asLong());
    }

    private void removeFromIndex(Claim claim) {
        if (claim == null) {
            return;
        }
        long sourceKey = claim.source().asLong();
        claimsByChunk.values().removeIf(entries -> {
            entries.remove(sourceKey);
            return entries.isEmpty();
        });
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Claim claim : claims.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Source", claim.source().asLong());
            entry.putUUID("Owner", claim.owner());
            entry.putString("Definition", claim.definition());
            entry.putBoolean("Active", claim.active());
            entry.putLongArray(
                "BlacklistedPositions",
                claim.blacklistedPositions().stream().mapToLong(Long::longValue).toArray()
            );
            list.add(entry);
        }
        tag.put("Claims", list);
        return tag;
    }

    private static ProtectionSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ProtectionSavedData data = new ProtectionSavedData();
        ListTag list = tag.getList("Claims", Tag.TAG_COMPOUND);
        for (Tag rawEntry : list) {
            CompoundTag entry = (CompoundTag) rawEntry;
            if (!entry.hasUUID("Owner")) {
                continue;
            }
            BlockPos source = BlockPos.of(entry.getLong("Source"));
            String definition = entry.getString("Definition");
            ProtectionRegion.ParseResult parsed =
                ProtectionRegion.parse(definition, source, Config.ABSOLUTE_MAX_PROTECTION_RADIUS);
            if (!parsed.valid()) {
                continue;
            }
            Claim claim = new Claim(
                source,
                entry.getUUID("Owner"),
                parsed.canonicalDefinition(),
                parsed.region(),
                entry.getBoolean("Active"),
                entry.getBoolean("Active")
                    ? toLongSet(entry.getLongArray("BlacklistedPositions"))
                    : Set.of()
            );
            data.claims.put(source.asLong(), claim);
            data.addToIndex(claim);
        }
        return data;
    }

    private static Set<Long> toLongSet(long[] values) {
        Set<Long> result = new HashSet<>(values.length);
        for (long value : values) {
            result.add(value);
        }
        return result;
    }

    public record Claim(
        BlockPos source,
        UUID owner,
        String definition,
        ProtectionRegion region,
        boolean active,
        Set<Long> blacklistedPositions
    ) {
        public Claim {
            blacklistedPositions = Set.copyOf(blacklistedPositions);
        }

        public boolean isEffective() {
            return active && region.withinLimit(source, Config.maxProtectionRadius());
        }

        public boolean protects(BlockPos pos) {
            return source.equals(pos) || region.contains(pos, source);
        }

        public boolean isBlacklisted(BlockPos pos) {
            return blacklistedPositions.contains(pos.asLong());
        }

        public Claim withBlacklisted(BlockPos pos) {
            Set<Long> updated = new HashSet<>(blacklistedPositions);
            updated.add(pos.asLong());
            return new Claim(source, owner, definition, region, active, updated);
        }

        public Claim withActiveAndResetBlacklist(boolean active) {
            return new Claim(source, owner, definition, region, active, Set.of());
        }
    }
}
