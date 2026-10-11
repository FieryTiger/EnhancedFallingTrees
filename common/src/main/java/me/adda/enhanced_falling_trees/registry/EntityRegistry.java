// common/src/main/java/me/adda/enhanced_falling_trees/registry/EntityRegistry.java
package me.adda.enhanced_falling_trees.registry;

import me.adda.enhanced_falling_trees.FallingTrees;
import me.adda.enhanced_falling_trees.api.platform.registry.DeferredObject;
import me.adda.enhanced_falling_trees.api.platform.registry.DeferredRegister;
import me.adda.enhanced_falling_trees.entity.TreeEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class EntityRegistry {
	public static final DeferredRegister<EntityType<?>> ENTITIES =
			DeferredRegister.create(FallingTrees.MOD_ID, Registries.ENTITY_TYPE);

	public static final DeferredObject<EntityType<TreeEntity>> TREE = ENTITIES.register("tree", () ->
			EntityType.Builder.of(TreeEntity::new, MobCategory.MISC)
					.sized(0.5f, 0.5f)
					.fireImmune()
					.noSummon()
					.build(("tree")));

	public static void initialize() {
		ENTITIES.register();
	}
}