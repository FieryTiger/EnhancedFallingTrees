package me.adda.enhanced_falling_trees.fabric;

import me.adda.enhanced_falling_trees.fabric.platform.network.FabricClientNetworking;
import me.adda.enhanced_falling_trees.particles.LeavesParticles;
import me.adda.enhanced_falling_trees.registry.ParticleRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;

@Environment(EnvType.CLIENT)
public class FallingTreesClientFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ParticleProviderRegistry.getInstance().register(ParticleRegistry.LEAVES.get(), LeavesParticles.Factory::new);
        FabricClientNetworking.initializeClientReceivers();
    }
}