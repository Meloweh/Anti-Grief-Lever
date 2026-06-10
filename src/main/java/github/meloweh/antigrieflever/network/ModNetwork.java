package github.meloweh.antigrieflever.network;

import github.meloweh.antigrieflever.Antigrieflever;
import github.meloweh.antigrieflever.Config;
import github.meloweh.antigrieflever.block.entity.AntiGriefLeverBlockEntity;
import github.meloweh.antigrieflever.client.ClientPayloadHandler;
import github.meloweh.antigrieflever.protection.ProtectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
    }

    public static void openConfiguration(ServerPlayer player, AntiGriefLeverBlockEntity lever) {
        PacketDistributor.sendToPlayer(
            player,
            new OpenConfigPayload(lever.getBlockPos(), lever.getDefinition(), Config.maxProtectionRadius())
        );
    }

    private static void handleOpen(OpenConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPayloadHandler.open(payload));
    }

    private static void handleSave(SaveConfigPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }

        BlockPos pos = payload.pos();
        if (player.distanceToSqr(pos.getCenter()) > 64.0
            || !(player.serverLevel().getBlockEntity(pos) instanceof AntiGriefLeverBlockEntity lever)
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

    public record OpenConfigPayload(BlockPos pos, String definition, int maxRadius) implements CustomPacketPayload {
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
}
