package me.adda.enhanced_falling_trees.entity;

import me.adda.enhanced_falling_trees.api.TreeType;
import me.adda.enhanced_falling_trees.config.FallingTreesConfig;
import me.adda.enhanced_falling_trees.config.common.PhysicsConfig;
import me.adda.enhanced_falling_trees.utils.TreePose;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Server side contact between a falling tree and living entities. The trunk (and anything solid) hits once,
 * with damage growing with how far that part of the tree has fallen, and knocks back along its motion; leaves
 * only push softly. Once the tree lies on the ground the trunk keeps entities out.
 */
final class TreeCollisions {
	// A part must move at least this much per tick to count as a hit
	private static final double MIN_HIT_SPEED_SQR = 0.05 * 0.05;
	// Rotation per tick above which the tree counts as still falling
	private static final float FALLING_ROTATION_STEP = 0.25f;
	private static final double LEAVES_PUSH = 0.05;
	private static final double LEAVES_BRUSH = 0.15;
	private static final double TRUNK_PUSH = 0.15;

	private record Part(BlockPos pos, Vec3 center, Vec3 velocity, boolean solid) {
	}

	private TreeCollisions() {
	}

	static void tick(TreeEntity tree, TreeType type) {
		PhysicsConfig config = FallingTreesConfig.getCommonConfig().physics;
		if (!config.treeDamage && !config.leavesPush && !config.solidTrunk) return;
		if (!(tree.level() instanceof ServerLevel level)) return;

		Map<BlockPos, BlockState> blocks = tree.getBlocks();
		Matrix4f pose = tree.getPose(1);
		float rotation = tree.getRotation(1);
		boolean falling = Math.abs(rotation - tree.lastRotation) > FALLING_ROTATION_STEP;
		tree.lastRotation = rotation;

		Vec3 origin = tree.position();
		List<Part> parts = new ArrayList<>(blocks.size());
		AABB bounds = null;
		for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
			Vector3f local = TreePose.blockCenter(pose, entry.getKey());
			Vec3 center = origin.add(local.x, local.y, local.z);
			Vec3 last = tree.lastCenters.put(entry.getKey(), center);
			Vec3 velocity = last == null ? Vec3.ZERO : center.subtract(last);
			boolean solid = type.baseBlockCheck(entry.getValue()) || !entry.getValue().is(BlockTags.LEAVES);
			parts.add(new Part(entry.getKey(), center, velocity, solid));

			AABB cube = cube(center);
			bounds = bounds == null ? cube : bounds.minmax(cube);
		}
		if (bounds == null) return;

		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, bounds.inflate(0.5), e -> e.isAlive() && !e.isSpectator())) {
			AABB box = entity.getBoundingBox();
			Part solid = null;
			Part leaves = null;

			for (Part part : parts) {
				if (!cube(part.center()).intersects(box)) continue;

				if (part.solid()) {
					// The highest touching part decides the hit: it fell from the furthest up
					if (solid == null || part.pos().getY() > solid.pos().getY()) solid = part;
				} else if (leaves == null) {
					leaves = part;
				}
			}

			if (solid != null) {
				if (falling && config.treeDamage && solid.velocity().lengthSqr() > MIN_HIT_SPEED_SQR
						&& tree.hitEntities.add(entity.getUUID())) {
					hit(tree, level, entity, solid, config);
				} else if (!falling && config.solidTrunk) {
					pushOut(entity, box, cube(solid.center()));
				}
			} else if (leaves != null && config.leavesPush) {
				if (falling) {
					// A falling crown brushes past once instead of shoving every tick, so it doesn't sweep
					// entities out from under the trunk
					Vec3 along = leaves.velocity().multiply(1, 0, 1);
					if (along.lengthSqr() > 1.0E-6 && tree.brushedEntities.add(entity.getUUID())) {
						along = along.normalize().scale(LEAVES_BRUSH * config.knockback);
						entity.push(along.x, 0, along.z);
						markMoved(entity);
					}
				} else {
					Vec3 away = entity.position().subtract(leaves.center()).multiply(1, 0, 1);
					if (away.lengthSqr() > 1.0E-4) {
						away = away.normalize().scale(LEAVES_PUSH * config.knockback);
						entity.push(away.x, 0, away.z);
						markMoved(entity);
					}
				}
			}
		}
	}

	private static void hit(TreeEntity tree, ServerLevel level, LivingEntity entity, Part part, PhysicsConfig config) {
		// The height this part falls from: the top of a tall tree hits much harder than its base
		float damage = (float) Mth.clamp(part.pos().getY() * config.damagePerBlock, 1, config.maxDamage);
		entity.hurtServer(level, level.damageSources().fallingBlock(tree), damage);

		Vec3 velocity = part.velocity();
		Vec3 horizontal = velocity.multiply(1, 0, 1);
		if (horizontal.lengthSqr() > 1.0E-6) {
			double strength = (0.4 + velocity.length()) * config.knockback;
			horizontal = horizontal.normalize().scale(strength);
			entity.push(horizontal.x, 0.3 * config.knockback, horizontal.z);
			markMoved(entity);
		}
	}

	/** Moves an entity out of a lying trunk block along the shallower horizontal side. */
	private static void pushOut(LivingEntity entity, AABB box, AABB cube) {
		double pushPlusX = cube.maxX - box.minX;
		double pushMinusX = box.maxX - cube.minX;
		double pushPlusZ = cube.maxZ - box.minZ;
		double pushMinusZ = box.maxZ - cube.minZ;
		double x = pushPlusX < pushMinusX ? pushPlusX : -pushMinusX;
		double z = pushPlusZ < pushMinusZ ? pushPlusZ : -pushMinusZ;

		if (Math.abs(x) < Math.abs(z)) {
			entity.push(Math.signum(x) * Math.min(Math.abs(x), TRUNK_PUSH), 0, 0);
		} else {
			entity.push(0, 0, Math.signum(z) * Math.min(Math.abs(z), TRUNK_PUSH));
		}
		markMoved(entity);
	}

	private static void markMoved(LivingEntity entity) {
		// Players move themselves, so the server has to send them the new velocity
		if (entity instanceof Player) {
			entity.hurtMarked = true;
		}
	}

	private static AABB cube(Vec3 center) {
		return new AABB(center.x - .5, center.y - .5, center.z - .5, center.x + .5, center.y + .5, center.z + .5);
	}
}
