package de.knito.typestones.client.mixin;

import com.mojang.serialization.MapCodec;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

/** Like {@link ItemModelsAccessor}, for item tint types: registers "typestones:pencil". */
@Mixin(ItemTintSources.class)
public interface ItemTintSourcesAccessor {
	@Accessor("ID_MAPPER")
	static ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends ItemTintSource>> typestones$idMapper() {
		throw new AssertionError();
	}
}
