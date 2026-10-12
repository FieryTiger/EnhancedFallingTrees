// fabric/src/main/java/me/adda/enhanced_falling_trees/fabric/platform/RegistrationHelperImpl.java
package me.adda.enhanced_falling_trees.fabric.platform;

import me.adda.enhanced_falling_trees.api.platform.registry.DeferredObject;
import me.adda.enhanced_falling_trees.api.platform.RegistrationHelper;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricTrackedDataRegistry;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public class FabricRegistrationHelper implements RegistrationHelper {
    @Override
    @SuppressWarnings("unchecked")
    public <T> void register(DeferredObject<? extends T> deferredObject, Supplier<? extends T> supplier) {
        Registry.register((Registry<T>) deferredObject.getRegistry(), deferredObject.getId(), supplier.get());
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> void registerNewRegistry(Registry<T> registry) {
        ResourceLocation registryName = registry.key().location();
        if (BuiltInRegistries.REGISTRY.containsKey(registryName))
            throw new IllegalStateException("Attempted duplicate registration of registry " + registryName);

        ((WritableRegistry) BuiltInRegistries.REGISTRY).register(registry.key(), registry, RegistrationInfo.BUILT_IN);
    }

    @Override
    public void registerEntityDataSerializer(ResourceLocation id, EntityDataSerializer<?> serializer) {
        FabricTrackedDataRegistry.register(id, serializer);
    }

    @Override
    public void registerReloadListener(PackType packType, PreparableReloadListener listener, ResourceLocation id, List<ResourceLocation> dependencies) {
        ResourceManagerHelper.get(packType).registerReloadListener(new IdentifiableResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return id;
            }

            @Override
            public CompletableFuture<Void> reload(PreparationBarrier preparationBarrier, ResourceManager resourceManager, Executor backgroundExecutor, Executor gameExecutor) {
                return listener.reload(preparationBarrier, resourceManager, backgroundExecutor, gameExecutor);
            }

            @Override
            public Collection<ResourceLocation> getFabricDependencies() {
                return dependencies;
            }

            @Override
            public String getName() {
                return listener.getName();
            }
        });
    }
}