package me.adda.enhanced_falling_trees.trees;

import me.adda.enhanced_falling_trees.api.TreeType;
import me.adda.enhanced_falling_trees.api.platform.PlatformServices;
import me.adda.enhanced_falling_trees.client.TreeEffects;
import me.adda.enhanced_falling_trees.config.FallingTreesConfig;
import me.adda.enhanced_falling_trees.entity.TreeEntity;
import me.adda.enhanced_falling_trees.utils.GroundUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class DefaultTree implements TreeType {
	private static final int MAX_TREE_HEIGHT = 32;
	private static final Direction[] DIRECTIONS = Direction.values();

	public record TreeStructure(Set<BlockPos> logBlocks, Set<BlockPos> mainTrunk) {}
	public record SearchNode(BlockPos pos, int verticalDistance) {}

	private record LeafSearchNode(BlockPos pos, int distance) {
		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			LeafSearchNode that = (LeafSearchNode) o;
			return pos.equals(that.pos);
		}

		@Override
		public int hashCode() {
			return pos.hashCode();
		}
	}

	@Override
	public boolean baseBlockCheck(BlockState blockState) {
		return FallingTreesConfig.getCommonConfig().filter.log.isValid(blockState.getBlock());
	}

	@Override
	public boolean extraRequiredBlockCheck(BlockState blockState) {
		if (blockState.hasProperty(BlockStateProperties.PERSISTENT) && blockState.getValue(BlockStateProperties.PERSISTENT))
			return false;
		return FallingTreesConfig.getCommonConfig().filter.leaves.isValid(blockState.getBlock());
	}

	@Override
	public boolean allowedTool(ItemStack itemStack) {
		return FallingTreesConfig.getCommonConfig().filter.tool.isValid(itemStack.getItem());
	}

	@Override
	public void handleSpecialEffects(TreeEntity entity) {
		if (entity == null ) return;

		if (PlatformServices.getPlatform().isClient()) {
			if (entity.getAge() == 1) {
				TreeEffects.playTreeFallSound(entity);
			}

			if (entity.getAge() == (int) (this.getFallAnimLength() * 20) - 5) {
				TreeEffects.playTreeImpactSound(entity);
			}

			if (entity.getAge() >= (int) (FallingTreesConfig.getCommonConfig().treeLifetimeLength * 20) - 1) {
				handleParticles(entity);
			}
		}
	}

	private void handleParticles(TreeEntity entity) {
		if (!(PlatformServices.getPlatform().isClient() && entity.level().isClientSide)) return;

		BlockState leavesState = getParticleBlockState(entity);
		if (leavesState == null) return;

		BlockPos leavesPos = getParticleBlockPos(entity);
		if (leavesPos == null) return;

		Vec3[] lineBlocks = GroundUtils.getFallBlockLine(entity);

		TreeEffects.spawnLeafParticles(lineBlocks, leavesState, leavesPos, entity.level());
	}

	@Override
	public boolean supportsParticles() {
		return true;
	}

	@Override
	public BlockState getParticleBlockState(TreeEntity entity) {
		return entity.getBlocks().values().stream()
				.filter(this::extraRequiredBlockCheck)
				.findFirst()
				.orElse(null);
	}

	@Override
	public BlockPos getParticleBlockPos(TreeEntity entity) {
		BlockState leavesState = getParticleBlockState(entity);
		if (leavesState == null) return null;

		return entity.getBlocks().entrySet().stream()
				.filter(entry -> entry.getValue().equals(leavesState))
				.map(Map.Entry::getKey)
				.findFirst()
				.orElse(null);
	}

	@Override
	public Set<BlockPos> blockGatheringAlgorithm(BlockPos startPos, LevelAccessor level) {
		Set<BlockPos> allBlocks = new HashSet<>();
		BlockState initialState = level.getBlockState(startPos);

		TreeStructure treeStructure = gatherTreeStructure(startPos, level, initialState);
		allBlocks.addAll(treeStructure.logBlocks());

		Set<BlockPos> leaves = gatherLeaves(treeStructure, level);
		allBlocks.addAll(leaves);

		return allBlocks;
	}

	private TreeStructure gatherTreeStructure(BlockPos startPos, LevelAccessor level, BlockState initialState) {
		Set<BlockPos> logBlocks = new HashSet<>();
		Set<BlockPos> mainTrunk = new HashSet<>();
		Set<BlockPos> processed = new HashSet<>();
		Queue<SearchNode> toProcess = new LinkedList<>();

		toProcess.offer(new SearchNode(startPos, 0));
		processed.add(startPos);

		while (!toProcess.isEmpty()) {
			SearchNode current = toProcess.poll();
			BlockPos currentPos = current.pos;
			int verticalDistance = current.verticalDistance;

			boolean isMainTrunk = currentPos.getX() == startPos.getX() && currentPos.getZ() == startPos.getZ();

			if (isMatchingLog(level.getBlockState(currentPos), initialState)) {
				logBlocks.add(currentPos);
				if (isMainTrunk) {
					mainTrunk.add(currentPos);
				}

				for (int x = -1; x <= 1; x++) {
					for (int y = -1; y <= 1; y++) {
						for (int z = -1; z <= 1; z++) {
							if (x == 0 && y == 0 && z == 0) continue;

							BlockPos neighborPos = currentPos.offset(x, y, z);

							if (neighborPos.getY() < startPos.getY()) continue;

							int newVerticalDistance = verticalDistance;
							if (y < 0) continue;
							else if (y > 0) newVerticalDistance++;

							if (!processed.contains(neighborPos) &&
									isWithinDistance(neighborPos, currentPos, startPos,
											FallingTreesConfig.getCommonConfig().limitations.maxTreeDistance,
											newVerticalDistance)) {

								processed.add(neighborPos);
								toProcess.offer(new SearchNode(neighborPos, newVerticalDistance));
							}
						}
					}
				}
			}
		}

		return new TreeStructure(logBlocks, mainTrunk);
	}

	private boolean isWithinDistance(BlockPos pos, BlockPos currentPos, BlockPos startPos,
									 int maxDistance, int verticalDistance) {
		if (pos.getX() == startPos.getX() && pos.getZ() == startPos.getZ()) {
			return verticalDistance <= MAX_TREE_HEIGHT;
		}

		int horizontalDistance = Math.max(
				Math.abs(startPos.getX() - currentPos.getX()),
				Math.abs(startPos.getZ() - currentPos.getZ())
		);

		return horizontalDistance <= maxDistance && verticalDistance <= MAX_TREE_HEIGHT;
	}

	private boolean isMatchingLog(BlockState state, BlockState initialState) {
		return baseBlockCheck(state) && state.getBlock() == initialState.getBlock();
	}

	private Set<BlockPos> gatherLeaves(TreeStructure treeStructure, LevelAccessor level) {
		Set<BlockPos> leaves = new HashSet<>();
		Set<BlockPos> processed = new HashSet<>();
		Queue<LeafSearchNode> queue = new LinkedList<>();

		Set<BlockPos> logBlocks = treeStructure.logBlocks();
		BlockState correctLeafType = initializeLeafSearch(logBlocks, level, queue);
		if (correctLeafType == null) return leaves;

		processLeafQueue(queue, processed, leaves, level, correctLeafType);

		return leaves;
	}

	private BlockState initializeLeafSearch(Set<BlockPos> logBlocks, LevelAccessor level, Queue<LeafSearchNode> queue) {
		BlockState correctLeafType = null;

		for (BlockPos logPos : logBlocks) {
			for (Direction dir : DIRECTIONS) {
				BlockPos leafPos = logPos.relative(dir);
				BlockState state = level.getBlockState(leafPos);

				if (extraRequiredBlockCheck(state)) {
					queue.offer(new LeafSearchNode(leafPos, 1));
					if (correctLeafType == null) {
						correctLeafType = state;
					}
				}
			}
		}

		return correctLeafType;
	}

	private void processLeafQueue(Queue<LeafSearchNode> queue, Set<BlockPos> processed,
								  Set<BlockPos> leaves, LevelAccessor level, BlockState correctLeafType) {
		while (!queue.isEmpty()) {
			LeafSearchNode node = queue.poll();
			BlockPos pos = node.pos();
			int distance = node.distance();

			if (!canProcessLeafNode(node, processed)) continue;
			processed.add(pos);

			if (isValidLeaf(level.getBlockState(pos), distance, correctLeafType)) {
				leaves.add(pos);
				addNeighborLeaves(pos, distance, queue, processed, level, correctLeafType);
			}
		}
	}

	private boolean canProcessLeafNode(LeafSearchNode node, Set<BlockPos> processed) {
		return !processed.contains(node.pos()) &&
				node.distance() <= FallingTreesConfig.getCommonConfig().limitations.maxLeavesDistance;
	}

	private boolean isValidLeaf(BlockState state, int distance, BlockState correctLeafType) {
		if (state.getBlock() != correctLeafType.getBlock()) return false;

		if (state.hasProperty(BlockStateProperties.DISTANCE)) {
			int leafDistance = state.getValue(BlockStateProperties.DISTANCE);
			return leafDistance >= distance;
		}

		return true;
	}

	private void addNeighborLeaves(BlockPos pos, int distance, Queue<LeafSearchNode> queue,
								   Set<BlockPos> processed, LevelAccessor level, BlockState correctLeafType) {
		int nextDistance = distance + 1;
		if (nextDistance > FallingTreesConfig.getCommonConfig().limitations.maxLeavesDistance) return;

		for (Direction dir : DIRECTIONS) {
			BlockPos nextPos = pos.relative(dir);
			if (processed.contains(nextPos)) continue;

			BlockState nextState = level.getBlockState(nextPos);
			if (extraRequiredBlockCheck(nextState) && nextState.getBlock() == correctLeafType.getBlock()) {
				queue.offer(new LeafSearchNode(nextPos, nextDistance));
			}
		}
	}

	@Override
	public float getFallAnimLength() {
		return FallingTreesConfig.getClientConfig().animation.treeProperties.fallAnimLength;
	}

	@Override
	public float getBounceAngleHeight() {
		return FallingTreesConfig.getClientConfig().animation.treeProperties.bounceAngleHeight;
	}

	@Override
	public float getBounceAnimLength() {
		return FallingTreesConfig.getClientConfig().animation.treeProperties.bounceAnimLength;
	}
}
