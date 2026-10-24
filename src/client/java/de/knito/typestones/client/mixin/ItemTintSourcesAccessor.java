package de.knito.typestones.client.mixin;

import com.mojang.serialization.MapCodec;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

/**
 * Minecraft keeps the list of item tint types private and Fabric API has no way to add one, so this
 * opens it to register "typestones:dye".
 */
@Mixin(ItemTintSources.class)
public interface ItemTintSourcesAccessor {
	@Accessor("ID_MAPPER")
	static ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends ItemTintSource>> typestones$idMapper() {
		throw new AssertionError();
	}
}
