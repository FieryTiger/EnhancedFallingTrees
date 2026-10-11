// fabric/src/main/java/me/adda/enhanced_falling_trees/fabric/platform/network/FabricNetworkService.java
package me.adda.enhanced_falling_trees.fabric.platform.network;

import me.adda.enhanced_falling_trees.api.platform.network.NetworkService;
import me.adda.enhanced_falling_trees.api.platform.network.PacketContext;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class FabricNetworkService implements NetworkService {
    private final Map<Identifier, BiConsumer<FriendlyByteBuf, PacketContext>> serverHandlers = new ConcurrentHashMap<>();

    // S2C handlers stored here during init, registered in onInitializeClient
    private static final Map<Identifier, BiConsumer<FriendlyByteBuf, PacketContext>> PENDING_S2C = new LinkedHashMap<>();

    private static MinecraftServer SERVER;

    public static void setServer(MinecraftServer server) {
        SERVER = server;
    }

    // Получение сервера
    private MinecraftServer getServer() {
        return SERVER;
    }

    @Override
    public void registerClientToServerPacket(Identifier id, BiConsumer<FriendlyByteBuf, PacketContext> handler) {
        CustomPacketPayload.Type<FallingTreesPayload> type = FallingTreesPayload.getType(id);

        ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> {
            serverHandlers.put(id, handler);
            FabricPacketContext packetContext = new FabricPacketContext(context.player(), context.responseSender());
            handler.accept((FriendlyByteBuf) payload.data(), packetContext);
        });
    }

    @Override
    public void registerServerToClientPacket(Identifier id, BiConsumer<FriendlyByteBuf, PacketContext> handler) {
        // Defer actual registration to onInitializeClient — Fabric 0.97+ requires
        // ClientPlayNetworking.registerGlobalReceiver to be called from the client entrypoint
        PENDING_S2C.put(id, handler);
    }

    static Map<Identifier, BiConsumer<FriendlyByteBuf, PacketContext>> pendingClientHandlers() {
        return PENDING_S2C;
    }

    @Override
    public void sendToPlayer(ServerPlayer player, Identifier id, FriendlyByteBuf buf) {
        CustomPacketPayload.Type<FallingTreesPayload> type = FallingTreesPayload.getType(id);
        FallingTreesPayload payload = new FallingTreesPayload(id, buf.copy());
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void sendToServer(Identifier id, FriendlyByteBuf buf) {
        CustomPacketPayload.Type<FallingTreesPayload> type = FallingTreesPayload.getType(id);
        FallingTreesPayload payload = new FallingTreesPayload(id, buf.copy());
        FabricClientNetworking.send(payload);
    }

    @Override
    public void sendToAll(Identifier id, FriendlyByteBuf buf) {
        CustomPacketPayload.Type<FallingTreesPayload> type = FallingTreesPayload.getType(id);
        FallingTreesPayload payload = new FallingTreesPayload(id, buf.copy());

        for (ServerPlayer player : PlayerLookup.all(getServer())) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}