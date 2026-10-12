package me.adda.enhanced_falling_trees.entity;

import me.adda.enhanced_falling_trees.api.TreeRegistry;
import me.adda.enhanced_falling_trees.api.TreeType;
import me.adda.enhanced_falling_trees.config.FallingTreesConfig;
import me.adda.enhanced_falling_trees.registry.EntityRegistry;
import me.adda.enhanced_falling_trees.registry.TreeTypeRegistry;
import me.adda.enhanced_falling_trees.utils.BlockMapEntityData;
import me.adda.enhanced_falling_trees.utils.GroundUtils;
import me.adda.enhanced_falling_trees.utils.TreePose;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Math;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class TreeEntity extends Entity {
	public static final EntityDataAccessor<Map<BlockPos, BlockState>> BLOCKS = SynchedEntityData.defineId(TreeEntity.class, BlockMapEntityData.BLOCK_MAP);
	public static final EntityDataAccessor<BlockPos> ORIGIN_POS = SynchedEntityData.defineId(TreeEntity.class, EntityDataSerializers.BLOCK_POS);
	public static final EntityDataAccessor<Float> ANGLE = SynchedEntityData.defineId(TreeEntity.class, EntityDataSerializers.FLOAT);
	public static final EntityDataAccessor<Float> TARGET_ANGLE = SynchedEntityData.defineId(TreeEntity.class, EntityDataSerializers.FLOAT);
	public static final EntityDataAccessor<Boolean> LEAVES_DROPPED = SynchedEntityData.defineId(TreeEntity.class, EntityDataSerializers.BOOLEAN);
	public static final EntityDataAccessor<ItemStack> USED_TOOL = SynchedEntityData.defineId(TreeEntity.class, EntityDataSerializers.ITEM_STACK);
	public static final EntityDataAccessor<Direction> FALL_DIRECTION = SynchedEntityData.defineId(TreeEntity.class, EntityDataSerializers.DIRECTION);
	public static final EntityDataAccessor<String> TREE_TYPE_LOCATION = SynchedEntityData.defineId(TreeEntity.class, EntityDataSerializers.STRING);
	// Age the tree had when it was loaded from disk, so clients don't replay the fall
	public static final EntityDataAccessor<Integer> START_AGE = SynchedEntityData.defineId(TreeEntity.class, EntityDataSerializers.INT);

	public Entity owner = null;
	public TreeType treeType = null;

	private static final float HALF_PI = (float) java.lang.Math.PI / 2;
	// How fast the fall angle follows the ground, per tick (the renderer used to do this per frame)
	private static final float WATER_LERP_FACTOR = 0.03f;
	private static final float NORMAL_LERP_FACTOR = 0.14f;
	private static final float MIN_BOUNCE_TRIGGER_ANGLE = 10f;

	private float fallAngle;
	private float prevFallAngle;
	private boolean fallAngleStarted;
	private boolean inLiquid;
	private int pivotDistance = -1;

	public TreeEntity(EntityType<?> entityType, Level level) {
		super(entityType, level);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
		return false;
	}

	public static void destroyTree(Set<BlockPos> blockPosList, BlockPos blockPos, LevelAccessor levelAccessor, TreeType treeType, Player player) {
		if (levelAccessor instanceof Level level) {
			TreeEntity treeEntity = new TreeEntity(EntityRegistry.TREE.get(), level);
			treeEntity.setPos(Vec3.atCenterOf(blockPos).add(0, -.5, 0));
			treeEntity.setData(blockPosList, blockPos, treeType, player, player.getItemBySlot(EquipmentSlot.MAINHAND));
			level.addFreshEntity(treeEntity);


			// Remove every tree block first (deferring shape updates via updateLimit=0), then
			// recompute neighbor shapes (e.g. fences) in a second pass once the whole tree is
			// actually gone — otherwise a fence touching multiple tree blocks can recompute its
			// connection against a still-partially-felled tree and end up stuck "connected".
			BlockState airState = Blocks.AIR.defaultBlockState();
			List<BlockPos> removedPositions = new ArrayList<>(blockPosList.size());
			List<BlockState> removedOldStates = new ArrayList<>(blockPosList.size());

			for (BlockPos pos : blockPosList) {
				removedOldStates.add(level.getBlockState(pos));
				removedPositions.add(pos);
				level.setBlock(pos, airState, Block.UPDATE_ALL, 0);
			}

			int neighborUpdateFlags = Block.UPDATE_ALL & -34;
			for (int i = 0; i < removedPositions.size(); i++) {
				BlockPos pos = removedPositions.get(i);
				BlockState oldState = removedOldStates.get(i);
				oldState.updateIndirectNeighbourShapes(level, pos, neighborUpdateFlags, 511);
				airState.updateNeighbourShapes(level, pos, neighborUpdateFlags, 511);
				airState.updateIndirectNeighbourShapes(level, pos, neighborUpdateFlags, 511);
			}

			for (Map.Entry<BlockPos, BlockState> entry : treeEntity.getBlocks().entrySet()) {
				level.sendBlockUpdated(entry.getKey().offset(blockPos), entry.getValue(), Blocks.AIR.defaultBlockState(), 3);
			}
		}
	}

	public void setData(Set<BlockPos> blockPosList, BlockPos originBlock, TreeType treeType, Entity owner, ItemStack itemStack) {
		this.owner = owner;
		this.treeType = treeType;

		Map<BlockPos, BlockState> blockPosMap = new ConcurrentHashMap<>();

		for (BlockPos pos : blockPosList) {
			blockPosMap.put(pos.immutable().subtract(originBlock), level().getBlockState(pos));
		}

		this.getEntityData().set(ORIGIN_POS, originBlock);
		this.getEntityData().set(BLOCKS, blockPosMap);
		this.getEntityData().set(USED_TOOL, itemStack);
		this.getEntityData().set(ANGLE, 0f);
		this.getEntityData().set(TARGET_ANGLE, 0f);
		Identifier treeTypeLocation = TreeRegistry.getTreeTypeLocation(treeType);
		if (treeTypeLocation != null)
			this.getEntityData().set(TREE_TYPE_LOCATION, treeTypeLocation.toString());
		this.getEntityData().set(FALL_DIRECTION, Direction.fromYRot(
				-Math.toDegrees(Math.atan2(owner.getX() - originBlock.getX(), owner.getZ() - originBlock.getZ()))
		).getOpposite());
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(BLOCKS, new ConcurrentHashMap<>());
		builder.define(ANGLE, 0f);
		builder.define(TARGET_ANGLE, 0f);
		builder.define(LEAVES_DROPPED, false);
		builder.define(ORIGIN_POS, new BlockPos(0, 0, 0));
		builder.define(USED_TOOL, ItemStack.EMPTY);
		builder.define(FALL_DIRECTION, Direction.NORTH);
		builder.define(TREE_TYPE_LOCATION, "");
		builder.define(START_AGE, 0);
	}

	private record SavedBlock(BlockPos pos, BlockState state) {
		static final Codec<SavedBlock> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				BlockPos.CODEC.fieldOf("pos").forGetter(SavedBlock::pos),
				BlockState.CODEC.fieldOf("state").forGetter(SavedBlock::state)
		).apply(instance, SavedBlock::new));
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		Map<BlockPos, BlockState> blocks = new ConcurrentHashMap<>();
		input.listOrEmpty("Blocks", SavedBlock.CODEC).forEach(block -> blocks.put(block.pos(), block.state()));
		this.getEntityData().set(BLOCKS, blocks);
		input.read("OriginPos", BlockPos.CODEC).ifPresent(pos -> this.getEntityData().set(ORIGIN_POS, pos));
		input.read("FallDirection", Direction.CODEC).ifPresent(direction -> this.getEntityData().set(FALL_DIRECTION, direction));
		input.read("UsedTool", ItemStack.OPTIONAL_CODEC).ifPresent(tool -> this.getEntityData().set(USED_TOOL, tool));
		this.getEntityData().set(TREE_TYPE_LOCATION, input.getStringOr("TreeType", ""));
		this.tickCount = input.getIntOr("TreeAge", 0);
		this.getEntityData().set(START_AGE, this.tickCount);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		ValueOutput.TypedOutputList<SavedBlock> blocks = output.list("Blocks", SavedBlock.CODEC);
		this.getBlocks().forEach((pos, state) -> blocks.add(new SavedBlock(pos, state)));
		output.store("OriginPos", BlockPos.CODEC, this.getOriginPos());
		output.store("FallDirection", Direction.CODEC, this.getDirection());
		output.store("UsedTool", ItemStack.OPTIONAL_CODEC, this.getUsedTool());
		output.putString("TreeType", this.getEntityData().get(TREE_TYPE_LOCATION));
		output.putInt("TreeAge", this.tickCount);
	}

	@Override
	public void tick() {
		super.tick();

		if (!this.isNoGravity()) {
			this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.04, 0.0));
		}
		// The tree stays where it was cut: only gravity, no sideways drift (water currents etc.)
		this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
		this.move(MoverType.SELF, this.getDeltaMovement());
		if (this.onGround()) {
			this.setDeltaMovement(this.getDeltaMovement().multiply(1, -0.5, 1));
		}

		TreeType type = this.getTreeType();
		if (type == null || this.getBlocks().isEmpty()) {
			// Broken or empty tree data, nothing to fall
			if (!this.level().isClientSide()) this.discard();
			return;
		}

		this.updateFallAngle(type);

		type.entityTick(this);
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	private void updateFallAngle(TreeType type) {
		this.prevFallAngle = this.fallAngle;
		float time = this.getAge() * HALF_PI / type.getFallAnimLength();
		if (!this.fallAngleStarted || this.tickCount % 5 == 0) {
			this.inLiquid = GroundUtils.willBeInLiquid(this);
		}

		float target;
		if (bumpCos(time) * this.fallAngle <= MIN_BOUNCE_TRIGGER_ANGLE && this.inLiquid) {
			Integer[] waterGroundIndexes = GroundUtils.getGroundIndexes(this, true);
			float waterHeight = GroundUtils.calculateAverageWaterHeight(this, waterGroundIndexes);
			float waterAngle = GroundUtils.calculateFallAngle(waterGroundIndexes);
			target = waterAngle + (waterAngle - waterAngle * waterHeight);
		} else {
			target = GroundUtils.calculateFallAngle(GroundUtils.getGroundIndexes(this, false));
		}

		if (!this.fallAngleStarted && this.getStartAge() > 0) {
			// Loaded mid-fall or already lying: don't fall again
			this.fallAngle = this.prevFallAngle = target;
		} else {
			float lerpFactor = (bumpCos(time) * this.fallAngle <= 1 && this.inLiquid) ? WATER_LERP_FACTOR : NORMAL_LERP_FACTOR;
			this.fallAngle = Math.lerp(this.fallAngle, target, lerpFactor);
		}
		this.fallAngleStarted = true;
	}

	/** Rotation of the tree in degrees (negative while it tips over), smooth between ticks. */
	public float getRotation(float partialTick) {
		TreeType type = this.getTreeType();
		if (type == null) return 0;

		float targetAngle = Math.lerp(this.prevFallAngle, this.fallAngle, partialTick);
		float time = this.getLifetime(partialTick) * HALF_PI / type.getFallAnimLength();
		float fallAnim = bumpCos(time) * targetAngle;

		float bounceAnim;
		if (this.inLiquid) {
			bounceAnim = Math.sin((time + type.getBounceAnimLength()) * type.getBounceAngleHeight());
		} else {
			float bounceHeight = Math.clamp(0, type.getBounceAngleHeight(), Math.round(targetAngle / 10));
			float adjustedTime = (time - HALF_PI) / (type.getBounceAnimLength() / (type.getFallAnimLength() * 2));
			bounceAnim = bumpSin(adjustedTime) * bounceHeight;
		}

		return (fallAnim + bounceAnim) - targetAngle;
	}

	/** Transform from tree-local block coordinates to positions relative to this entity. */
	public org.joml.Matrix4f getPose(float partialTick) {
		TreeType type = this.getTreeType();
		if (this.pivotDistance < 0) {
			this.pivotDistance = TreePose.pivotDistance(this.getBlocks(), this.getDirection(), type);
		}
		return TreePose.matrix(this.getDirection().getOpposite(), this.pivotDistance, type.fallAnimationEdgeDistance(), this.getRotation(partialTick));
	}

	public boolean isInLiquid() {
		return this.inLiquid;
	}

	private static float bumpCos(float time) {
		return Math.max(0, Math.cos(Math.clamp(-(float) java.lang.Math.PI, (float) java.lang.Math.PI, time)));
	}

	private static float bumpSin(float time) {
		return Math.max(0, Math.sin(Math.clamp(-(float) java.lang.Math.PI, (float) java.lang.Math.PI, time)));
	}

	public Map<BlockPos, BlockState> getBlocks() {
		return this.getEntityData().get(BLOCKS);
	}

	public int getMaxLifeTimeTick() {
		TreeType treeType = this.getTreeType();

		if (treeType.getClass().equals(TreeTypeRegistry.CACTUS.get().getClass())) {
			return (int) (FallingTreesConfig.getCommonConfig().cactusLifetimeLength * 20);
		} else if (treeType.getClass().equals(TreeTypeRegistry.BAMBOO.get().getClass())) {
			return (int) (FallingTreesConfig.getCommonConfig().bambooLifetimeLength * 20);
		} else if (treeType.getClass().equals(TreeTypeRegistry.CHORUS.get().getClass())) {
			return (int) (FallingTreesConfig.getCommonConfig().chorusLifetimeLength * 20);
		}

		return (int) (FallingTreesConfig.getCommonConfig().treeLifetimeLength * 20);
	}

	public float getLifetime(float partialTick) {
		return (this.getAge() + partialTick) / 20;
	}

	/** Ticks since the tree was felled; on clients this includes the age it was loaded with. */
	public int getAge() {
		return this.level().isClientSide() ? this.tickCount + this.getStartAge() : this.tickCount;
	}

	public int getStartAge() {
		return this.getEntityData().get(START_AGE);
	}

	public boolean isLarge() {
		return this.getTreeHeight() > 10;
	}

	public BlockPos getOriginPos() {
		return this.getEntityData().get(ORIGIN_POS);
	}

	public ItemStack getUsedTool() {
		return this.getEntityData().get(USED_TOOL);
	}

	public void setTargetAngle(float angle) {
		this.getEntityData().set(TARGET_ANGLE, angle);
	}

	public float getTargetAngle() {
		return this.getEntityData().get(TARGET_ANGLE);
	}

	public void setAngle(float angle) {
		this.getEntityData().set(ANGLE, angle);
	}

	public float getAngle() {
		return this.getEntityData().get(ANGLE);
	}

	public void setLeavesDropped() {
		this.getEntityData().set(LEAVES_DROPPED, true);
	}

	public boolean getLeavesDropped() {
		return this.getEntityData().get(LEAVES_DROPPED);
	}

	public @NotNull Direction getDirection() {
		return this.getEntityData().get(FALL_DIRECTION);
	}

	public TreeType getTreeType() {
		Optional<TreeType> treeTypeOptional = TreeRegistry.getTreeType(Identifier.parse(this.getEntityData().get(TREE_TYPE_LOCATION)));
		return treeTypeOptional.orElse(null);
	}

	public int getTreeHeight() {
		Map<BlockPos, BlockState> blocks = this.getBlocks();

		return (int) blocks.entrySet().stream()
			.filter(entry -> {
				BlockPos pos = entry.getKey();
				BlockState state = entry.getValue().getBlock().defaultBlockState();
				return pos.getX() == 0 && pos.getZ() == 0 && this.getTreeType().baseBlockCheck(state);
			})
			.count();
	}
}