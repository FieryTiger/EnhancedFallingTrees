package me.adda.enhanced_falling_trees.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.adda.enhanced_falling_trees.api.TreeType;
import me.adda.enhanced_falling_trees.entity.TreeEntity;
import me.adda.enhanced_falling_trees.utils.GroundUtils;
import me.adda.enhanced_falling_trees.utils.RenderUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.joml.Math;

import java.util.Map;

@Environment(EnvType.CLIENT)
public class TreeRenderer extends EntityRenderer<TreeEntity> {
	private static final float FLOW_SHAKE = 0.02f;

	public TreeRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public void render(TreeEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
					   MultiBufferSource buffer, int packedLight) {
		TreeType treeType = entity.getTreeType();
		if (treeType == null) return;

		Map<BlockPos, BlockState> blocks = entity.getBlocks();
		if (blocks.isEmpty()) return;

		poseStack.pushPose();
		try {
			Vec3 flowOffset = flowOffset(entity, partialTick);
			poseStack.translate(flowOffset.x, 0, flowOffset.z);
			poseStack.mulPose(entity.getPose(partialTick));

			renderTreeBlocks(entity, poseStack, buffer, blocks);
		} finally {
			poseStack.popPose();
		}
	}

	/** A small shake when the tree lies in flowing water; the current itself no longer moves it. */
	private Vec3 flowOffset(TreeEntity entity, float partialTick) {
		if (!entity.isInLiquid()) return Vec3.ZERO;

		Vec3[] line = GroundUtils.getFallBlockLine(entity);
		if (line.length == 0) return Vec3.ZERO;

		BlockPos middle = BlockPos.containing(line[line.length / 2]);
		FluidState fluid = entity.level().getFluidState(middle);
		if (fluid.isEmpty()) fluid = entity.level().getFluidState(middle.below());
		if (fluid.isEmpty()) return Vec3.ZERO;

		Vec3 flow = fluid.getFlow(entity.level(), middle);
		float time = entity.getLifetime(partialTick);
		return flow.scale(FLOW_SHAKE * Math.sin(time * 9f) + FLOW_SHAKE * 0.5f * Math.sin(time * 23f));
	}

	private void renderTreeBlocks(TreeEntity entity, PoseStack poseStack,
								  MultiBufferSource buffer, Map<BlockPos, BlockState> blocks) {
		VertexConsumer consumer = buffer.getBuffer(RenderType.cutout());

		blocks.forEach((blockPos, blockState) -> renderBlock(entity, poseStack, consumer, blockPos, blockState, blocks));
	}

	private void renderBlock(TreeEntity entity, PoseStack poseStack, VertexConsumer consumer,
							 BlockPos blockPos, BlockState blockState, Map<BlockPos, BlockState> blocks) {
		poseStack.pushPose();
		try {
			poseStack.translate(blockPos.getX(), blockPos.getY(), blockPos.getZ());

			RenderUtils.renderBlock(poseStack, blockState, blockPos.offset(entity.getOriginPos()),
					entity.level(), consumer, (state, level, offset, face, pos) ->
							shouldRenderFace(state, blocks, blockPos, face));
		} finally {
			poseStack.popPose();
		}
	}

	private boolean shouldRenderFace(BlockState state, Map<BlockPos, BlockState> blocks,
									 BlockPos pos, Direction face) {
		if (!state.canOcclude()) return true;

		BlockPos facePos = pos.offset(face.getNormal());
		if (!blocks.containsKey(facePos)) return true;

		return !state.is(blocks.get(facePos).getBlock());
	}

	@Override
	public ResourceLocation getTextureLocation(TreeEntity entity) {
		return null;
	}
}
