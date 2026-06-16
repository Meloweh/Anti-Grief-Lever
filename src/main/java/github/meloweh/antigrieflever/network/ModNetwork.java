package github.meloweh.antigrieflever.network;

import github.meloweh.antigrieflever.Antigrieflever;
import github.meloweh.antigrieflever.Config;
import github.meloweh.antigrieflever.block.entity.AcardeWarpStoneBlockEntity;
import github.meloweh.antigrieflever.block.entity.ConfigurableRegionBlockEntity;
import github.meloweh.antigrieflever.client.ClientPayloadHandler;
import github.meloweh.antigrieflever.protection.ProtectionRegion;
import github.meloweh.antigrieflever.warp.WarpStoneKey;
import github.meloweh.antigrieflever.warp.WarpStoneSavedData;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(OpenConfigPayload.TYPE, OpenConfigPayload.STREAM_CODEC, ModNetwork::handleOpen);
        registrar.playToServer(SaveConfigPayload.TYPE, SaveConfigPayload.STREAM_CODEC, ModNetwork::handleSave);
        registrar.playToClient(OpenWarpStonePayload.TYPE, OpenWarpStonePayload.STREAM_CODEC, ModNetwork::handleOpenWarpStone);
        registrar.playToServer(SaveWarpStonePayload.TYPE, SaveWarpStonePayload.STREAM_CODEC, ModNetwork::handleSaveWarpStone);
    }

    public static void openConfiguration(ServerPlayer player, ConfigurableRegionBlockEntity lever) {
        PacketDistributor.sendToPlayer(
            player,
            new OpenConfigPayload(
                lever.getBlockPos(),
                lever.getDefinition(),
                Config.maxProtectionRadius(),
                lever.configTitleKey(),
                lever.regionLabelKey()
            )
        );
    }

    public static void openWarpStoneConfiguration(ServerPlayer player, AcardeWarpStoneBlockEntity stone) {
        ServerLevel level = player.serverLevel();
        WarpStoneKey source = WarpStoneKey.of(level, stone.getBlockPos());
        WarpStoneSavedData data = WarpStoneSavedData.get(level);
        WarpStoneSavedData.Entry sourceEntry = data.get(source)
            .orElseGet(() -> data.registerIfAbsent(source, stone.getCustomName(), stone.getLinkedTarget()));
        stone.applySavedEntry(sourceEntry);
        List<WarpStoneOption> options = data.availableTargets(source).stream()
            .map(entry -> new WarpStoneOption(
                entry.key().dimensionName(),
                entry.key().pos(),
                entry.name()
            ))
            .toList();
        PacketDistributor.sendToPlayer(player, new OpenWarpStonePayload(source.pos(), sourceEntry.name(), options));
    }

    private static void handleOpen(OpenConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadHandler.open(payload));
    }

    private static void handleSave(SaveConfigPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }

        BlockPos pos = payload.pos();
        BlockEntity blockEntity = player.serverLevel().getBlockEntity(pos);
        if (player.distanceToSqr(pos.getCenter()) > 64.0
            || !(blockEntity instanceof ConfigurableRegionBlockEntity lever)
            || !lever.isOwner(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.antigrieflever.not_owner"), true);
            return;
        }

        ProtectionRegion.ParseResult parsed =
            ProtectionRegion.parse(payload.definition(), pos, Config.maxProtectionRadius());
        if (!parsed.valid()) {
            player.displayClientMessage(
                Component.translatable("message.antigrieflever.invalid_region", parsed.error()),
                true
            );
            return;
        }

        lever.setDefinition(parsed.canonicalDefinition());
        player.displayClientMessage(Component.translatable("message.antigrieflever.saved"), true);
    }

    private static void handleOpenWarpStone(OpenWarpStonePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadHandler.openWarpStone(payload));
    }

    private static void handleSaveWarpStone(SaveWarpStonePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        BlockPos pos = payload.pos();
        if (!player.isCreative()
            || player.distanceToSqr(pos.getCenter()) > 64.0
            || !(player.serverLevel().getBlockEntity(pos) instanceof AcardeWarpStoneBlockEntity stone)) {
            player.displayClientMessage(Component.translatable("message.antigrieflever.warp_stone.creative_only"), true);
            return;
        }

        ServerLevel level = player.serverLevel();
        WarpStoneSavedData data = WarpStoneSavedData.get(level);
        WarpStoneKey source = WarpStoneKey.of(level, pos);
        String name = WarpStoneSavedData.sanitizeName(payload.name());
        if (!payload.link()) {
            stone.applySavedEntry(data.updateName(source, name));
            player.displayClientMessage(Component.translatable("message.antigrieflever.warp_stone.saved"), true);
            return;
        }

        WarpStoneKey target;
        try {
            target = new WarpStoneKey(WarpStoneKey.dimensionFromString(payload.targetDimension()), payload.targetPos());
        } catch (IllegalArgumentException exception) {
            player.displayClientMessage(Component.translatable("message.antigrieflever.warp_stone.unavailable"), true);
            return;
        }

        ServerLevel targetLevel = level.getServer().getLevel(target.dimension());
        if (targetLevel == null
            || !(targetLevel.getBlockEntity(target.pos()) instanceof AcardeWarpStoneBlockEntity targetStone)
            || !targetLevel.getBlockState(target.pos()).is(Antigrieflever.ACARDE_WARP_STONE.get())) {
            data.remove(target);
            player.displayClientMessage(Component.translatable("message.antigrieflever.warp_stone.unavailable"), true);
            return;
        }

        if (!data.link(source, name, target)) {
            player.displayClientMessage(Component.translatable("message.antigrieflever.warp_stone.unavailable"), true);
            return;
        }

        data.get(source).ifPresent(stone::applySavedEntry);
        data.get(target).ifPresent(targetStone::applySavedEntry);
        player.displayClientMessage(Component.translatable("message.antigrieflever.warp_stone.linked"), true);
    }

    public record OpenConfigPayload(
        BlockPos pos,
        String definition,
        int maxRadius,
        String titleKey,
        String regionLabelKey
    ) implements CustomPacketPayload {
        public static final Type<OpenConfigPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Antigrieflever.MODID, "open_config")
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenConfigPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            OpenConfigPayload::pos,
            ByteBufCodecs.STRING_UTF8,
            OpenConfigPayload::definition,
            ByteBufCodecs.VAR_INT,
            OpenConfigPayload::maxRadius,
            ByteBufCodecs.STRING_UTF8,
            OpenConfigPayload::titleKey,
            ByteBufCodecs.STRING_UTF8,
            OpenConfigPayload::regionLabelKey,
            OpenConfigPayload::new
        );

        @Override
        public Type<OpenConfigPayload> type() {
            return TYPE;
        }
    }

    public record SaveConfigPayload(BlockPos pos, String definition) implements CustomPacketPayload {
        public static final Type<SaveConfigPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Antigrieflever.MODID, "save_config")
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, SaveConfigPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            SaveConfigPayload::pos,
            ByteBufCodecs.STRING_UTF8,
            SaveConfigPayload::definition,
            SaveConfigPayload::new
        );

        @Override
        public Type<SaveConfigPayload> type() {
            return TYPE;
        }
    }

    public record WarpStoneOption(String dimension, BlockPos pos, String name) {
        public static final StreamCodec<RegistryFriendlyByteBuf, WarpStoneOption> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            WarpStoneOption::dimension,
            BlockPos.STREAM_CODEC,
            WarpStoneOption::pos,
            ByteBufCodecs.STRING_UTF8,
            WarpStoneOption::name,
            WarpStoneOption::new
        );
    }

    public record OpenWarpStonePayload(BlockPos pos, String name, List<WarpStoneOption> options)
        implements CustomPacketPayload {
        public static final Type<OpenWarpStonePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Antigrieflever.MODID, "open_warp_stone")
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenWarpStonePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            OpenWarpStonePayload::pos,
            ByteBufCodecs.STRING_UTF8,
            OpenWarpStonePayload::name,
            WarpStoneOption.STREAM_CODEC.apply(ByteBufCodecs.list()),
            OpenWarpStonePayload::options,
            OpenWarpStonePayload::new
        );

        @Override
        public Type<OpenWarpStonePayload> type() {
            return TYPE;
        }
    }

    public record SaveWarpStonePayload(
        BlockPos pos,
        String name,
        String targetDimension,
        BlockPos targetPos,
        boolean link
    ) implements CustomPacketPayload {
        public static final Type<SaveWarpStonePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Antigrieflever.MODID, "save_warp_stone")
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, SaveWarpStonePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            SaveWarpStonePayload::pos,
            ByteBufCodecs.STRING_UTF8,
            SaveWarpStonePayload::name,
            ByteBufCodecs.STRING_UTF8,
            SaveWarpStonePayload::targetDimension,
            BlockPos.STREAM_CODEC,
            SaveWarpStonePayload::targetPos,
            ByteBufCodecs.BOOL,
            SaveWarpStonePayload::link,
            SaveWarpStonePayload::new
        );

        @Override
        public Type<SaveWarpStonePayload> type() {
            return TYPE;
        }
    }
}
