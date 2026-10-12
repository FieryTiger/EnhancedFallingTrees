package me.adda.enhanced_falling_trees.utils;

import me.adda.enhanced_falling_trees.api.TreeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Math;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;

/**
 * Where the blocks of a falling tree are, relative to the tree entity. Shared by the renderer and the
 * server side collisions so both agree on the tree's shape at any moment of the fall.
 */
public final class TreePose {
	private static final int MAX_TREE_RADIUS = 3;

	private TreePose() {
	}

	/**
	 * Transform from tree-local block coordinates to positions relative to the entity.
	 *
	 * @param direction       direction the tree rotates towards (the renderer's direction)
	 * @param pivotDistance   see {@link #pivotDistance}
	 * @param rotationDegrees current rotation, negative while falling
	 */
	public static Matrix4f matrix(Direction direction, int pivotDistance, float edgeDistance, float rotationDegrees) {
		Vector3f pivot = new Vector3f(0, 0, (.5f + pivotDistance) * edgeDistance);
		pivot.rotateY(Math.toRadians(-direction.toYRot()));

		Vector3f rotationVector = new Vector3f(Math.toRadians(rotationDegrees), 0, 0);
		rotationVector.rotateY(Math.toRadians(-direction.toYRot()));
		Quaternionf rotation = new Quaternionf().identity()
				.rotateX(rotationVector.x)
				.rotateZ(rotationVector.z);

		return new Matrix4f()
				.translate(-pivot.x, 0, -pivot.z)
				.rotate(rotation)
				.translate(pivot.x, 0, pivot.z)
				.translate(-.5f, 0, -.5f);
	}

	/** Center of a tree block relative to the entity, for the given transform. */
	public static Vector3f blockCenter(Matrix4f matrix, BlockPos pos) {
		return matrix.transformPosition(pos.getX() + .5f, pos.getY() + .5f, pos.getZ() + .5f, new Vector3f());
	}

	/** How far from the trunk the tree pivots: thick trunks tip over their edge, not their center. */
	public static int pivotDistance(Map<BlockPos, BlockState> blocks, Direction direction, TreeType treeType) {
		int distance = 0;
		while (shouldContinueDistance(blocks, distance, direction, treeType)) {
			distance++;
		}
		return distance;
	}

	private static boolean shouldContinueDistance(Map<BlockPos, BlockState> blocks, int distance, Direction direction, TreeType treeType) {
		int currentWidth = getWidthAtDistance(blocks, distance, direction, treeType);
		int nextWidth = getWidthAtDistance(blocks, distance + 1, direction, treeType);

		return nextWidth >= currentWidth * 0.7 &&
				blocks.containsKey(new BlockPos(direction.getUnitVec3i().multiply(distance + 1)));
	}

	private static int getWidthAtDistance(Map<BlockPos, BlockState> blocks, int distance, Direction direction, TreeType treeType) {
		BlockPos center = new BlockPos(direction.getUnitVec3i().multiply(distance));
		int width = 0;

		for (int x = -MAX_TREE_RADIUS; x <= MAX_TREE_RADIUS; x++) {
			for (int y = -MAX_TREE_RADIUS; y <= MAX_TREE_RADIUS; y++) {
				BlockPos checkPos = center.offset(direction.getClockWise().getUnitVec3i().multiply(x)).above(y);
				if (blocks.containsKey(checkPos) && treeType.baseBlockCheck(blocks.get(checkPos))) {
					width++;
				}
			}
		}

		return width;
	}
}
