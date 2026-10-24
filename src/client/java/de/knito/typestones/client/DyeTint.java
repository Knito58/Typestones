package de.knito.typestones.client;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import de.knito.typestones.ModComponents;
import de.knito.typestones.block.Dyes;

/**
 * Item model tint "typestones:dye": the letter or background color from the item's typestones:dyes
 * component, so a Typestone in the hand looks like the placed block.
 */
public record DyeTint(boolean background) implements ItemTintSource {
	public static final MapCodec<DyeTint> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("background", false).forGetter(DyeTint::background)
	).apply(instance, DyeTint::new));

	@Override
	public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity) {
		Dyes dyes = stack.getOrDefault(ModComponents.DYES, Dyes.DEFAULT);
		return background ? dyes.backgroundTint() : dyes.letterTint();
	}

	@Override
	public MapCodec<DyeTint> type() {
		return MAP_CODEC;
	}
}
