package me.adda.enhanced_falling_trees.entity;

import me.adda.enhanced_falling_trees.api.TreeRegistry;
import me.adda.enhanced_falling_trees.api.TreeType;
import me.adda.enhanced_falling_trees.config.FallingTreesConfig;
import me.adda.enhanced_falling_trees.registry.EntityRegistry;
import me.adda.enhanced_falling_trees.registry.TreeTypeRegistry;
import me.adda.enhanced_falling_trees.utils.BlockMapEntityData;
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
			treeEntity.setPos(blockPos.getCenter().add(0, -.5, 0));
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

		type.entityTick(this);
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