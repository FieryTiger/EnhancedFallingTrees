package me.adda.enhanced_falling_trees.compat;

import me.adda.enhanced_falling_trees.FallingTrees;
import me.adda.enhanced_falling_trees.api.TreeRegistry;
import me.adda.enhanced_falling_trees.api.TreeType;
import me.adda.enhanced_falling_trees.config.FallingTreesConfig;
import me.adda.enhanced_falling_trees.entity.TreeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

// HT's TreeChop fells trees instantly once they are chopped through; this plays our fall animation instead
public final class TreeChop {
	private static boolean loaded;

	private TreeChop() {
	}

	/** Called by the loader module when TreeChop is installed. */
	public static void markLoaded() {
		loaded = true;
	}

	/** With TreeChop installed, regular trees are chopped by TreeChop and only their fall is animated here. */
	public static boolean handlesChopping() {
		return loaded && FallingTreesConfig.getCommonConfig().features.treeChopCompat;
	}

	/** Returns true when the tree now falls with our animation and TreeChop should not break it itself. */
	public static boolean fell(Level level, Player player, BlockPos choppedPos, Stream<BlockPos> logs, Stream<BlockPos> leaves) {
		if (!FallingTreesConfig.getCommonConfig().features.treeChopCompat || player == null) return false;

		Set<BlockPos> blocks = new HashSet<>();
		logs.forEach(pos -> blocks.add(pos.immutable()));
		leaves.forEach(pos -> blocks.add(pos.immutable()));
		blocks.add(choppedPos.immutable());
		if (blocks.size() < 2) return false;

		// The registry keeps its own instance, the supplier would make a new one it doesn't know
		TreeType type = TreeRegistry.getTreeType(ResourceLocation.fromNamespaceAndPath(FallingTrees.MOD_ID, "default")).orElse(null);
		if (type == null) return false;

		// TreeChop's chopped log has a thin model; fall with a whole log there so the trunk has no gap
		BlockState above = level.getBlockState(choppedPos.above());
		if (above.is(BlockTags.LOGS) && !level.getBlockState(choppedPos).is(above.getBlock())) {
			level.setBlock(choppedPos, above, Block.UPDATE_INVISIBLE);
		}

		TreeEntity.destroyTree(blocks, choppedPos, level, type, player);
		return true;
	}
}