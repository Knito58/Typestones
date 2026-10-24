package de.knito.typestones.client.model;

import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import de.knito.typestones.block.Look;
import de.knito.typestones.block.TypestoneItem;

/**
 * The Typestone item: the same cube as the placed block, drawn for the letter and dyes on the stack.
 * The base model only gives the display transforms and the particle texture.
 */
public class TypestoneItemModel implements ItemModel {
	private static final Vector3fc[] CUBE = {
			new Vector3f(0, 0, 0), new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(1, 1, 0),
			new Vector3f(0, 0, 1), new Vector3f(1, 0, 1), new Vector3f(0, 1, 1), new Vector3f(1, 1, 1)
	};
	private static final Supplier<Vector3fc[]> EXTENTS = () -> CUBE;

	private final ModelRenderProperties properties;
	private final Matrix4fc transformation;
	private final Material.Baked stone;
	private final Material.Baked flat;

	TypestoneItemModel(ModelRenderProperties properties, Matrix4fc transformation, Material.Baked stone, Material.Baked flat) {
		this.properties = properties;
		this.transformation = transformation;
		this.stone = stone;
		this.flat = flat;
	}

	@Override
	public void update(ItemStackRenderState output, ItemStack stack, ItemModelResolver resolver, ItemDisplayContext context,
			ClientLevel level, ItemOwner owner, int seed) {
		Look look = new Look(TypestoneItem.letter(stack), TypestoneItem.dyes(stack));
		// Stacks that look alike may share a cached picture, e.g. in the inventory.
		output.appendModelIdentityElement(this);
		output.appendModelIdentityElement(look);
		ItemStackRenderState.LayerRenderState layer = output.newLayer();
		layer.setExtents(EXTENTS);
		layer.setLocalTransform(transformation);
		properties.applyToLayer(layer, context);
		TypestoneMesh.emit(layer.emitter(), look, Direction.NORTH, face -> false, false, stone, flat);
	}

	/** {"type": "typestones:typestone", "base": "typestones:block/typestone"} in items/typestone.json. */
	public record Unbaked(Identifier base) implements ItemModel.Unbaked {
		public static final MapCodec<Unbaked> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				Identifier.CODEC.fieldOf("base").forGetter(Unbaked::base)
		).apply(instance, Unbaked::new));

		@Override
		public MapCodec<Unbaked> type() {
			return CODEC;
		}

		@Override
		public void resolveDependencies(ResolvableModel.Resolver resolver) {
			resolver.markDependency(base);
		}

		@Override
		public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transformation) {
			ModelBaker baker = context.blockModelBaker();
			ResolvedModel model = baker.getModel(base);
			ModelRenderProperties properties = ModelRenderProperties.fromResolvedModel(baker, model, model.getTopTextureSlots());
			return new TypestoneItemModel(properties, transformation,
					baker.materials().get(new Material(TypestoneBlockModel.STONE), model),
					baker.materials().get(new Material(TypestoneBlockModel.FLAT), model));
		}
	}
}
