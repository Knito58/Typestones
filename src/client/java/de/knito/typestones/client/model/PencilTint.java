package de.knito.typestones.client.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import de.knito.typestones.ModComponents;
import de.knito.typestones.block.Dyes;

/**
 * "typestones:pencil" in an item model's tints: the pencil's letter dye for its tip, or with
 * "background": true its background dye for the band, grey when it has none.
 */
public record PencilTint(boolean background) implements ItemTintSource {
	private static final int NO_BACKGROUND = 0xFF969696;

	public static final MapCodec<PencilTint> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("background", false).forGetter(PencilTint::background)
	).apply(instance, PencilTint::new));

	@Override
	public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity) {
		Dyes dyes = stack.getOrDefault(ModComponents.DYES, Dyes.DEFAULT);
		if (!background) {
			return dyes.letterTint();
		}
		return dyes.dyed() ? dyes.backgroundTint() : NO_BACKGROUND;
	}

	@Override
	public MapCodec<PencilTint> type() {
		return CODEC;
	}
}
