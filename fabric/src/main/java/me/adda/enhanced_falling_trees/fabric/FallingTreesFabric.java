package me.adda.enhanced_falling_trees.fabric;

import me.adda.enhanced_falling_trees.FallingTrees;
import me.adda.enhanced_falling_trees.fabric.platform.network.FabricNetworkService;
import me.adda.enhanced_falling_trees.fabric.platform.network.FallingTreesPayload;
import me.adda.enhanced_falling_trees.registry.ParticleRegistry;
import me.adda.enhanced_falling_trees.fabric.compat.TreeChopFabric;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.resources.ResourceLocation;

public class FallingTreesFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ParticleRegistry.PARTICLE_TYPES.register();

        registerPayloadTypes();

        ServerLifecycleEvents.SERVER_STARTING.register(FabricNetworkService::setServer);

        FallingTrees.init();

        if (FabricLoader.getInstance().isModLoaded("treechop")) {
            TreeChopFabric.register();
        }
    }

    private void registerPayloadTypes() {
        // Регистрируем все типы пакетов, которые будут использоваться
        registerPayloadType(ResourceLocation.fromNamespaceAndPath(FallingTrees.MOD_ID, "config_packet"));
        // Добавьте здесь другие типы пакетов по мере необходимости
    }

    private void registerPayloadType(ResourceLocation id) {
        PayloadTypeRegistry.playS2C().register(
                FallingTreesPayload.getType(id),
                FallingTreesPayload.codec(id)
        );

        PayloadTypeRegistry.playC2S().register(
                FallingTreesPayload.getType(id),
                FallingTreesPayload.codec(id)
        );
    }
}