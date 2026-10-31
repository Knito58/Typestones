package de.knito.typestones.client.picture;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import de.knito.typestones.TypestonesConfig;
import de.knito.typestones.block.TypestoneBlock;
import de.knito.typestones.block.TypestoneBlockEntity;
import de.knito.typestones.picture.PictureTile;

/**
 * Draws the picture of a picture stone on its two picture sides, over the plain stone of the block
 * model. Each picture is one texture; a stone draws its own square of it. On the back of a wall, and
 * underneath a floor, the stone draws the opposite stone's square (see {@link PictureTile#backCol()}),
 * so the picture reads right from both sides and joins without seams.
 */
public class PictureStoneRenderer implements BlockEntityRenderer<TypestoneBlockEntity, PictureStoneRenderer.State> {
	/** Like the letters, the picture floats a little in front of the stone, so the two never fight. */
	private static final float LIFT = 0.0025f;

	public static class State extends BlockEntityRenderState {
		@Nullable Identifier texture;
		@Nullable Side front;
		@Nullable Side back;
	}

	/**
	 * One picture side of the stone: where it faces, which square of the texture it shows, and its
	 * light. Null in the state when a neighbor hides it.
	 */
	record Side(Direction face, Direction up, Direction right, float u0, float v0, float u1, float v1, int light) {
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(TypestoneBlockEntity stone, State state, float partialTicks, Vec3 cameraPos,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderState.extractBase(stone, state, breakProgress);
		PictureTile tile = stone.picture();
		Level level = stone.getLevel();
		state.texture = tile == null || level == null ? null : PictureSync.texture(tile.hash());
		if (state.texture == null) {
			return;
		}
		BlockState block = stone.getBlockState();
		Direction facing = block.getValue(TypestoneBlock.FACING);
		Direction front = TypestoneBlock.pictureFront(block);
		boolean flat = block.getValue(TypestoneBlock.FLAT);
		state.front = side(level, stone.getBlockPos(), block, front, facing, tile, tile.col(), tile.row());
		state.back = side(level, stone.getBlockPos(), block, front.getOpposite(), facing, tile,
				flat ? tile.col() : tile.backCol(), flat ? tile.bottomRow() : tile.row());
	}

	/** Same orientation of each side as the letters have (see TypestoneMesh). */
	private static @Nullable Side side(Level level, BlockPos pos, BlockState block, Direction face, Direction facing,
			PictureTile tile, int col, int row) {
		BlockPos next = pos.relative(face);
		if (!Block.shouldRenderFace(block, level.getBlockState(next), face)) {
			return null;
		}
		Direction up = switch (face) {
			case UP -> facing;
			case DOWN -> facing.getOpposite();
			default -> Direction.UP;
		};
		Direction right = face.getAxis().isVertical() ? facing.getClockWise() : face.getOpposite().getClockWise();
		// The block itself is solid and dark inside; the side is lit like the air in front of it.
		return new Side(face, up, right,
				(float) col / tile.cols(), (float) row / tile.rows(),
				(float) (col + 1) / tile.cols(), (float) (row + 1) / tile.rows(),
				LightCoordsUtil.getLightCoords(level, next));
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		Side front = state.front;
		Side back = state.back;
		if (state.texture == null || front == null && back == null) {
			return;
		}
		// Drawn later in the frame: only the sides themselves go along, not the reused state. Cutout:
		// transparent pixels show the plain stone of the block model beneath.
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(state.texture), (pose, consumer) -> {
			if (front != null) {
				quad(pose, consumer, front);
			}
			if (back != null) {
				quad(pose, consumer, back);
			}
		});
	}

	/** Corners in the order top left, bottom left, bottom right, top right, as the side is seen from outside. */
	private static void quad(PoseStack.Pose pose, VertexConsumer consumer, Side side) {
		vertex(pose, consumer, side, 0, 0, side.u0(), side.v0());
		vertex(pose, consumer, side, 0, 1, side.u0(), side.v1());
		vertex(pose, consumer, side, 1, 1, side.u1(), side.v1());
		vertex(pose, consumer, side, 1, 0, side.u1(), side.v0());
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, Side side, float x, float y, float u, float v) {
		float a = x - 0.5f;
		float b = 0.5f - y;
		float c = 0.5f + LIFT;
		Direction face = side.face();
		consumer.addVertex(pose,
						0.5f + a * side.right().getStepX() + b * side.up().getStepX() + c * face.getStepX(),
						0.5f + a * side.right().getStepY() + b * side.up().getStepY() + c * face.getStepY(),
						0.5f + a * side.right().getStepZ() + b * side.up().getStepZ() + c * face.getStepZ())
				.setColor(0xFFFFFFFF)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(side.light())
				.setNormal(pose, face.getStepX(), face.getStepY(), face.getStepZ());
	}

	/** The view distance of the settings, 4 chunks unless changed. */
	@Override
	public int getViewDistance() {
		return TypestonesConfig.get().viewBlocks();
	}
}
