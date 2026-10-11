package me.adda.enhanced_falling_trees.trees;

import me.adda.enhanced_falling_trees.api.TreeType;
import me.adda.enhanced_falling_trees.api.platform.PlatformServices;
import me.adda.enhanced_falling_trees.config.FallingTreesConfig;
import me.adda.enhanced_falling_trees.entity.TreeEntity;
import me.adda.enhanced_falling_trees.registry.SoundRegistry;
import me.adda.enhanced_falling_trees.utils.GroundUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

public class CactusTree implements TreeType {
	@Override
	public boolean baseBlockCheck(BlockState blockState) {
		return blockState.is(Blocks.CACTUS);
	}

	@Override
	public Set<BlockPos> blockGatheringAlgorithm(BlockPos blockPos, LevelAccessor level) {
		Set<BlockPos> blocks = new HashSet<>();
		loopBlocks(blockPos, level, blocks);
		return blocks;
	}

	private void loopBlocks(BlockPos pos, LevelAccessor level, Set<BlockPos> blocks) {
		blocks.add(pos);
		if (this.baseBlockCheck(level.getBlockState(pos.above()))) {
			loopBlocks(pos.above(), level, blocks);
		}
	}

	@Override
	public void handleSpecialEffects(TreeEntity entity) {
		if (entity == null) return;

		if (PlatformServices.getPlatform().isClient()) {
			if (entity.getAge() == 1) {
				if (FallingTreesConfig.getClientConfig().soundSettings.enabled) {
					entity.level().playLocalSound(entity.getX(), entity.getY(), entity.getZ(),
							SoundRegistry.CACTUS_FALL.get(),
							SoundSource.BLOCKS,
							FallingTreesConfig.getClientConfig().soundSettings.startVolume,
							1f, true);
				}
			}

			if (entity.getAge() == (int) (this.getFallAnimLength() * 20) - 5) {
				SoundEvent sound = GroundUtils.willBeInLiquid(entity) ?
						SoundEvents.GENERIC_SPLASH : SoundRegistry.CACTUS_IMPACT.get();

				if (FallingTreesConfig.getClientConfig().soundSettings.enabled) {
					entity.level().playLocalSound(entity.getX(), entity.getY(), entity.getZ(),
							sound,
							SoundSource.BLOCKS,
							FallingTreesConfig.getClientConfig().soundSettings.endVolume,
							1f, true);
				}
			}
		}
	}

	@Override
	public float fallAnimationEdgeDistance() {
		return 14f / 16f;
	}

	@Override
	public boolean enabled() {
		return !FallingTreesConfig.getCommonConfig().features.disableCactusTrees;
	}

	@Override
	public float getFallAnimLength() {
		return FallingTreesConfig.getClientConfig().animation.cactusProperties.fallAnimLength;
	}

	@Override
	public float getBounceAngleHeight() {
		return FallingTreesConfig.getClientConfig().animation.cactusProperties.bounceAngleHeight;
	}

	@Override
	public float getBounceAnimLength() {
		return FallingTreesConfig.getClientConfig().animation.cactusProperties.bounceAnimLength;
	}
}