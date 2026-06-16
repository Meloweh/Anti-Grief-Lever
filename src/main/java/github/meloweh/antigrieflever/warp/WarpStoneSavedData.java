package github.meloweh.antigrieflever.warp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class WarpStoneSavedData extends SavedData {
    public static final String DEFAULT_NAME = "Acarde Warp Stone";
    private static final int MAX_NAME_LENGTH = 64;
    private static final String DATA_NAME = "antigrieflever_acarde_warp_stones";
    private static final Factory<WarpStoneSavedData> FACTORY =
        new Factory<>(WarpStoneSavedData::new, WarpStoneSavedData::load);

    private final Map<WarpStoneKey, Entry> stones = new HashMap<>();

    public static WarpStoneSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public Optional<Entry> get(WarpStoneKey key) {
        return Optional.ofNullable(stones.get(key));
    }

    public Entry registerIfAbsent(WarpStoneKey key, String name, @Nullable WarpStoneKey linked) {
        Entry existing = stones.get(key);
        if (existing != null) {
            return existing;
        }
        Entry entry = new Entry(key, sanitizeName(name), linked);
        stones.put(key, entry);
        setDirty();
        return entry;
    }

    public Entry updateName(WarpStoneKey key, String name) {
        Entry old = stones.get(key);
        Entry updated = new Entry(key, sanitizeName(name), old == null ? null : old.linked());
        stones.put(key, updated);
        setDirty();
        return updated;
    }

    public List<Entry> availableTargets(WarpStoneKey source) {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : stones.values()) {
            if (!entry.key().equals(source) && entry.linked() == null) {
                result.add(entry);
            }
        }
        result.sort(Comparator
            .comparing(Entry::name)
            .thenComparing(entry -> entry.key().dimensionName())
            .thenComparingInt(entry -> entry.key().pos().getX())
            .thenComparingInt(entry -> entry.key().pos().getY())
            .thenComparingInt(entry -> entry.key().pos().getZ()));
        return result;
    }

    public boolean link(WarpStoneKey source, String sourceName, WarpStoneKey target) {
        if (source.equals(target)) {
            return false;
        }
        Entry targetEntry = stones.get(target);
        if (targetEntry == null || (targetEntry.linked() != null && !source.equals(targetEntry.linked()))) {
            return false;
        }

        registerIfAbsent(source, sourceName, null);
        unlink(source);
        unlink(target);

        Entry currentSource = stones.get(source);
        Entry currentTarget = stones.get(target);
        if (currentTarget == null) {
            return false;
        }
        stones.put(source, new Entry(source, sanitizeName(sourceName), target));
        stones.put(target, new Entry(target, currentTarget.name(), source));
        setDirty();
        return currentSource != null;
    }

    public void unlink(WarpStoneKey key) {
        Entry entry = stones.get(key);
        if (entry == null || entry.linked() == null) {
            return;
        }

        WarpStoneKey partnerKey = entry.linked();
        Entry partner = stones.get(partnerKey);
        stones.put(key, new Entry(key, entry.name(), null));
        if (partner != null && key.equals(partner.linked())) {
            stones.put(partnerKey, new Entry(partnerKey, partner.name(), null));
        }
        setDirty();
    }

    public void remove(WarpStoneKey key) {
        Entry removed = stones.remove(key);
        if (removed == null) {
            return;
        }
        if (removed.linked() != null) {
            Entry partner = stones.get(removed.linked());
            if (partner != null && key.equals(partner.linked())) {
                stones.put(partner.key(), new Entry(partner.key(), partner.name(), null));
            }
        }
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry entry : stones.values()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("Dimension", entry.key().dimensionName());
            entryTag.putLong("Pos", entry.key().pos().asLong());
            entryTag.putString("Name", entry.name());
            if (entry.linked() != null) {
                entryTag.putString("LinkedDimension", entry.linked().dimensionName());
                entryTag.putLong("LinkedPos", entry.linked().pos().asLong());
            }
            list.add(entryTag);
        }
        tag.put("Stones", list);
        return tag;
    }

    private static WarpStoneSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        WarpStoneSavedData data = new WarpStoneSavedData();
        ListTag list = tag.getList("Stones", Tag.TAG_COMPOUND);
        for (Tag rawEntry : list) {
            CompoundTag entryTag = (CompoundTag) rawEntry;
            try {
                WarpStoneKey key = new WarpStoneKey(
                    WarpStoneKey.dimensionFromString(entryTag.getString("Dimension")),
                    BlockPos.of(entryTag.getLong("Pos"))
                );
                WarpStoneKey linked = entryTag.contains("LinkedDimension", Tag.TAG_STRING)
                    ? new WarpStoneKey(
                        WarpStoneKey.dimensionFromString(entryTag.getString("LinkedDimension")),
                        BlockPos.of(entryTag.getLong("LinkedPos"))
                    )
                    : null;
                data.stones.put(key, new Entry(key, sanitizeName(entryTag.getString("Name")), linked));
            } catch (IllegalArgumentException ignored) {
                // Ignore corrupt or obsolete dimension ids instead of preventing world load.
            }
        }
        data.normalizeLinks();
        return data;
    }

    private void normalizeLinks() {
        for (Entry entry : List.copyOf(stones.values())) {
            WarpStoneKey linked = entry.linked();
            if (linked == null) {
                continue;
            }
            Entry partner = stones.get(linked);
            if (partner == null) {
                stones.put(entry.key(), new Entry(entry.key(), entry.name(), null));
            } else if (partner.linked() == null) {
                stones.put(linked, new Entry(linked, partner.name(), entry.key()));
            } else if (!entry.key().equals(partner.linked())) {
                stones.put(entry.key(), new Entry(entry.key(), entry.name(), null));
            }
        }
    }

    public static String sanitizeName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            return DEFAULT_NAME;
        }
        return trimmed.length() <= MAX_NAME_LENGTH ? trimmed : trimmed.substring(0, MAX_NAME_LENGTH);
    }

    public record Entry(WarpStoneKey key, String name, @Nullable WarpStoneKey linked) {
    }
}
