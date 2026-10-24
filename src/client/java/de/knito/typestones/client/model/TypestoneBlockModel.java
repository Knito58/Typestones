package de.knito.typestones.client.model;

import java.util.List;
import java.util.function.Predicate;

import com.mojang.serialization.MapCodec;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.fabric.api.client.model.loading.v1.CustomUnbakedBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;

import de.knito.typestones.Typestones;
import de.knito.typestones.block.Look;
import de.knito.typestones.block.TypestoneBlock;
import de.knito.typestones.client.network.LetterSync;
import de.knito.typestones.letter.Letters;

/**
 * The placed Typestone. It has no fixed quads: each time the chunk is meshed it draws the letter and
 * dyes the block entity hands over (see {@link TypestoneMesh}).
 */
public class TypestoneBlockModel implements BlockStateModel {
	static final Identifier STONE = Identifier.withDefaultNamespace("block/smooth_stone");
	/** Plain white, tinted to the dye color: flat color without texture or border. */
	static final Identifier FLAT = Typestones.id("block/flat");

	private final Material.Baked stone;
	private final Material.Baked flat;

	TypestoneBlockModel(Material.Baked stone, Material.Baked flat) {
		this.stone = stone;
		this.flat = flat;
	}

	@Override
	public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
			Predicate<@Nullable Direction> cullTest) {
		Look look = level.getBlockEntityRenderData(pos) instanceof Look l ? l : Look.PLAIN;
		if (!look.letter().equals(Letters.NONE) && Letters.get(look.letter()) == null) {
			// Drawn plain for now; redrawn when the server sent the letter.
			LetterSync.waitFor(look.letter(), pos);
		}
		TypestoneMesh.emit(emitter, look, state.getValue(TypestoneBlock.FACING), cullTest, true, stone, flat);
	}

	@Override
	public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
		// Everything is drawn in emitQuads.
	}

	@Override
	public Material.Baked particleMaterial() {
		return stone;
	}

	@Override
	public int materialFlags() {
		return 0;
	}

	/** "fabric:type": "typestones:typestone" in the blockstate file. */
	public record Unbaked() implements CustomUnbakedBlockStateModel {
		public static final MapCodec<Unbaked> CODEC = MapCodec.unit(new Unbaked());

		@Override
		public MapCodec<Unbaked> codec() {
			return CODEC;
		}

		@Override
		public void resolveDependencies(ResolvableModel.Resolver resolver) {
		}

		@Override
		public BlockStateModel bake(ModelBaker baker) {
			return new TypestoneBlockModel(
					baker.materials().get(new Material(STONE), () -> "typestones:typestone"),
					baker.materials().get(new Material(FLAT), () -> "typestones:typestone"));
		}
	}
}
