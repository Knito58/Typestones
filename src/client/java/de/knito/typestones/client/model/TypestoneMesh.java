package de.knito.typestones.client.model;

import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.client.resources.model.sprite.Material;

import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;

import de.knito.typestones.block.Look;
import de.knito.typestones.letter.Letter;
import de.knito.typestones.letter.Letters;

/**
 * Draws a Typestone: on each of the 6 faces a background square (smooth stone, or flat color when the
 * background is dyed), and the letter in flat color on top of it as one
 * flat rectangle per {@link Letter#rectangles()}. Shared by the placed block and the item.
 */
final class TypestoneMesh {
	/** How far the letter floats in front of the background, so the two never fight for the same pixels. */
	private static final float LIFT = 0.0025f;

	private TypestoneMesh() {
	}

	/**
	 * @param facing the direction the player looked when placing the block: the top of the letter on
	 *               the top face points that way
	 * @param culled true for faces that are hidden by a neighbor; such faces are left out
	 * @param cull   whether to mark quads with their face for culling (blocks only)
	 */
	static void emit(QuadEmitter emitter, Look look, Direction facing, Predicate<@Nullable Direction> culled, boolean cull,
			Material.Baked stone, Material.Baked flat) {
		Letter letter = look.letter().equals(Letters.NONE) ? null : Letters.get(look.letter());
		int letterColor = look.dyes().letterTint();
		for (Direction face : Direction.values()) {
			if (culled.test(face)) {
				continue;
			}
			Direction up = switch (face) {
				case UP -> facing;
				case DOWN -> facing.getOpposite();
				default -> Direction.UP;
			};
			Direction right = face.getAxis().isVertical() ? facing.getClockWise() : face.getOpposite().getClockWise();

			if (look.dyes().dyed()) {
				quad(emitter, face, up, right, 0, 0, 1, 1, 0, cull, flat, look.dyes().backgroundTint());
			} else {
				quad(emitter, face, up, right, 0, 0, 1, 1, 0, cull, stone, 0xFFFFFFFF);
			}
			if (letter != null) {
				float pixel = 1f / letter.size();
				for (Letter.Rect r : letter.rectangles()) {
					quad(emitter, face, up, right, r.x0() * pixel, r.y0() * pixel, r.x1() * pixel, r.y1() * pixel,
							LIFT, cull, flat, letterColor);
				}
			}
		}
	}

	/**
	 * One rectangle on a face, from (left, top) to (right, bottom) as the face is seen from outside:
	 * 0..1 from the face's left edge and from its top edge.
	 */
	private static void quad(QuadEmitter emitter, Direction face, Direction up, Direction right,
			float left, float top, float rightEdge, float bottom, float lift, boolean cull,
			Material.Baked material, int color) {
		// Vertex order as in QuadEmitter.square: top left, bottom left, bottom right, top right.
		vertex(emitter, 0, face, up, right, left, top, lift);
		vertex(emitter, 1, face, up, right, left, bottom, lift);
		vertex(emitter, 2, face, up, right, rightEdge, bottom, lift);
		vertex(emitter, 3, face, up, right, rightEdge, top, lift);
		if (cull) {
			emitter.cullFace(face);
		} else {
			emitter.nominalFace(face);
		}
		emitter.materialBake(material, MutableQuadView.BAKE_LOCK_UV);
		emitter.color(color, color, color, color);
		emitter.emit();
	}

	private static void vertex(QuadEmitter emitter, int index, Direction face, Direction up, Direction right,
			float u, float v, float lift) {
		float a = u - 0.5f;
		float b = 0.5f - v;
		float c = 0.5f + lift;
		emitter.pos(index,
				0.5f + a * right.getStepX() + b * up.getStepX() + c * face.getStepX(),
				0.5f + a * right.getStepY() + b * up.getStepY() + c * face.getStepY(),
				0.5f + a * right.getStepZ() + b * up.getStepZ() + c * face.getStepZ());
	}
}
