package github.meloweh.antigrieflever.restoration;

import github.meloweh.antigrieflever.Config;
import github.meloweh.antigrieflever.protection.ProtectionRegion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

public final class RestorationSavedData extends SavedData {
    private static final String DATA_NAME = "antigrieflever_restorator_snapshots";
    private static final Factory<RestorationSavedData> FACTORY =
        new Factory<>(RestorationSavedData::new, RestorationSavedData::load);

    private final Map<Long, Snapshot> snapshots = new HashMap<>();

    public static RestorationSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public CaptureResult capture(ServerLevel level, BlockPos source, String definition) {
        ProtectionRegion.ParseResult parsed =
            ProtectionRegion.parse(definition, source, Config.maxProtectionRadius());
        if (!parsed.valid()) {
            remove(source);
            return CaptureResult.error(parsed.error());
        }

        List<StoredBlock> blocks = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        ProtectionRegion region = parsed.region();
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int y = region.minY(); y <= region.maxY(); y++) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    cursor.set(x, y, z);
                    if (level.isOutsideBuildHeight(cursor) || !region.contains(cursor, source)) {
                        continue;
                    }
                    BlockPos storedPos = cursor.immutable();
                    BlockState state = level.getBlockState(storedPos);
                    BlockEntity blockEntity = level.getBlockEntity(storedPos);
                    CompoundTag blockEntityTag = blockEntity == null
                        ? null
                        : blockEntity.saveWithFullMetadata(level.registryAccess());
                    blocks.add(new StoredBlock(storedPos, state, blockEntityTag));
                }
            }
        }

        snapshots.put(source.asLong(), new Snapshot(source.immutable(), parsed.canonicalDefinition(), blocks));
        setDirty();
        return CaptureResult.success(blocks.size());
    }

    public int restore(ServerLevel level, BlockPos source) {
        Snapshot snapshot = snapshots.remove(source.asLong());
        if (snapshot == null) {
            return 0;
        }

        for (StoredBlock block : snapshot.blocks()) {
            level.setBlock(block.pos(), block.state(), Block.UPDATE_ALL);
        }
        for (StoredBlock block : snapshot.blocks()) {
            CompoundTag blockEntityTag = block.blockEntityTag();
            if (blockEntityTag == null) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(block.pos());
            if (blockEntity == null) {
                blockEntity = BlockEntity.loadStatic(block.pos(), block.state(), blockEntityTag.copy(), level.registryAccess());
                if (blockEntity != null) {
                    level.setBlockEntity(blockEntity);
                }
            } else {
                blockEntity.loadWithComponents(blockEntityTag.copy(), level.registryAccess());
            }
            if (blockEntity != null) {
                blockEntity.setChanged();
                level.sendBlockUpdated(block.pos(), block.state(), block.state(), Block.UPDATE_ALL);
            }
        }

        setDirty();
        return snapshot.blocks().size();
    }

    public void remove(BlockPos source) {
        if (snapshots.remove(source.asLong()) != null) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag snapshotList = new ListTag();
        for (Snapshot snapshot : snapshots.values()) {
            CompoundTag snapshotTag = new CompoundTag();
            snapshotTag.putLong("Source", snapshot.source().asLong());
            snapshotTag.putString("Definition", snapshot.definition());

            ListTag blockList = new ListTag();
            for (StoredBlock block : snapshot.blocks()) {
                CompoundTag blockTag = new CompoundTag();
                blockTag.putLong("Pos", block.pos().asLong());
                blockTag.put("State", NbtUtils.writeBlockState(block.state()));
                if (block.blockEntityTag() != null) {
                    blockTag.put("BlockEntity", block.blockEntityTag().copy());
                }
                blockList.add(blockTag);
            }
            snapshotTag.put("Blocks", blockList);
            snapshotList.add(snapshotTag);
        }
        tag.put("Snapshots", snapshotList);
        return tag;
    }

    private static RestorationSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        RestorationSavedData data = new RestorationSavedData();
        HolderGetter<Block> blocks = registries.lookupOrThrow(Registries.BLOCK);
        ListTag snapshotList = tag.getList("Snapshots", Tag.TAG_COMPOUND);
        for (Tag rawSnapshot : snapshotList) {
            CompoundTag snapshotTag = (CompoundTag) rawSnapshot;
            BlockPos source = BlockPos.of(snapshotTag.getLong("Source"));
            String definition = snapshotTag.getString("Definition");

            List<StoredBlock> storedBlocks = new ArrayList<>();
            ListTag blockList = snapshotTag.getList("Blocks", Tag.TAG_COMPOUND);
            for (Tag rawBlock : blockList) {
                CompoundTag blockTag = (CompoundTag) rawBlock;
                BlockPos pos = BlockPos.of(blockTag.getLong("Pos"));
                BlockState state = NbtUtils.readBlockState(blocks, blockTag.getCompound("State"));
                CompoundTag blockEntityTag = blockTag.contains("BlockEntity", Tag.TAG_COMPOUND)
                    ? blockTag.getCompound("BlockEntity")
                    : null;
                storedBlocks.add(new StoredBlock(pos, state, blockEntityTag));
            }
            data.snapshots.put(source.asLong(), new Snapshot(source, definition, storedBlocks));
        }
        return data;
    }

    public record CaptureResult(boolean success, int blocks, @Nullable String error) {
        public static CaptureResult success(int blocks) {
            return new CaptureResult(true, blocks, null);
        }

        public static CaptureResult error(String error) {
            return new CaptureResult(false, 0, error);
        }
    }

    private record Snapshot(BlockPos source, String definition, List<StoredBlock> blocks) {
        private Snapshot {
            blocks = List.copyOf(blocks);
        }
    }

    private record StoredBlock(BlockPos pos, BlockState state, @Nullable CompoundTag blockEntityTag) {
        private StoredBlock {
            pos = pos.immutable();
            blockEntityTag = blockEntityTag == null ? null : blockEntityTag.copy();
        }
    }
}
