package me.adda.enhanced_falling_trees.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import me.adda.enhanced_falling_trees.api.TreeType;
import me.adda.enhanced_falling_trees.entity.TreeEntity;
import me.adda.enhanced_falling_trees.utils.GroundUtils;
import me.adda.enhanced_falling_trees.utils.RenderUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.joml.Math;

@Environment(EnvType.CLIENT)
public class TreeRenderer extends EntityRenderer<TreeEntity, TreeRenderState> {
	private static final float FLOW_SHAKE = 0.02f;

	public TreeRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public TreeRenderState createRenderState() {
		return new TreeRenderState();
	}

	@Override
	public void extractRenderState(TreeEntity entity, TreeRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);

		TreeType treeType = entity.getTreeType();
		state.treeType = treeType;
		state.blocks = entity.getBlocks();
		state.originPos = entity.getOriginPos();
		state.level = entity.level();

		if (treeType != null && !state.blocks.isEmpty()) {
			state.pose = entity.getPose(partialTick);
			state.flowOffset = flowOffset(entity, partialTick);
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

	@Override
	protected boolean affectedByCulling(TreeEntity entity) {
		return false;
	}

	@Override
	public void submit(TreeRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraRenderState) {
		if (state.treeType == null || state.blocks == null || state.blocks.isEmpty()) return;

		poseStack.pushPose();
		try {
			renderTree(state, poseStack, collector);
		} finally {
			poseStack.popPose();
		}
	}

	private void renderTree(TreeRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
		poseStack.translate(state.flowOffset.x, 0, state.flowOffset.z);
		poseStack.mulPose(state.pose);

		renderTreeBlocks(state, poseStack, collector);
	}

	private void renderTreeBlocks(TreeRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
		state.blocks.forEach((blockPos, blockState) -> renderBlock(state, poseStack, collector, blockPos, blockState));
	}

	private void renderBlock(TreeRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
							 BlockPos blockPos, BlockState blockState) {
		poseStack.pushPose();
		try {
			poseStack.translate(blockPos.getX(), blockPos.getY(), blockPos.getZ());

			BlockPos worldBlockPos = blockPos.offset(state.originPos);
			int color = Minecraft.getInstance().getBlockColors().getColor(blockState, state.level, worldBlockPos, 0);
			float r = ((color >> 16) & 0xFF) / 255.0f;
			float g = ((color >> 8) & 0xFF) / 255.0f;
			float b = (color & 0xFF) / 255.0f;

			BlockStateModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(blockState);
			int light = RenderUtils.scaleLight(state.lightCoords);

			collector.submitBlockModel(poseStack, ItemBlockRenderTypes.getRenderType(blockState), model,
					r, g, b, light, OverlayTexture.NO_OVERLAY, 0);
		} finally {
			poseStack.popPose();
		}
	}

}
