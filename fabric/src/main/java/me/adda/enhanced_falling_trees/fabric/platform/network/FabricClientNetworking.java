package me.adda.enhanced_falling_trees.fabric.platform.network;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

// Client-only part of FabricNetworkService, kept separate so the dedicated server never loads client classes
@Environment(EnvType.CLIENT)
public final class FabricClientNetworking {
    private FabricClientNetworking() {
    }

    public static void initializeClientReceivers() {
        FabricNetworkService.pendingClientHandlers().forEach((id, handler) -> {
            CustomPacketPayload.Type<FallingTreesPayload> type = FallingTreesPayload.getType(id);
            ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> {
                FabricPacketContext ctx = new FabricPacketContext(context.player(), context.responseSender());
                handler.accept((FriendlyByteBuf) payload.data(), ctx);
            });
        });
    }

    static void send(FallingTreesPayload payload) {
        ClientPlayNetworking.send(payload);
    }
}