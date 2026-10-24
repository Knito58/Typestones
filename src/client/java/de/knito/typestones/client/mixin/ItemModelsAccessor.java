package de.knito.typestones.client.mixin;

import com.mojang.serialization.MapCodec;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

/**
 * Minecraft keeps the list of item model types private and Fabric API has no way to add one, so this
 * opens it to register "typestones:typestone".
 */
@Mixin(ItemModels.class)
public interface ItemModelsAccessor {
	@Accessor("ID_MAPPER")
	static ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends ItemModel.Unbaked>> typestones$idMapper() {
		throw new AssertionError();
	}
}
