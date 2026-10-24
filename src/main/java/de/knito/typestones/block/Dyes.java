package de.knito.typestones.block;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.DyeColor;

/**
 * The two dyes of a Typestone: one for the letter, and optionally one for the background. Without a
 * background dye the block shows plain smooth stone.
 */
public record Dyes(DyeColor letter, Optional<DyeColor> background) {
	public static final Dyes DEFAULT = new Dyes(DyeColor.BLACK, Optional.empty());

	public static final Codec<Dyes> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			DyeColor.CODEC.fieldOf("letter").forGetter(Dyes::letter),
			DyeColor.CODEC.optionalFieldOf("background").forGetter(Dyes::background)
	).apply(instance, Dyes::new));

	public static final StreamCodec<ByteBuf, Dyes> STREAM_CODEC = StreamCodec.composite(
			DyeColor.STREAM_CODEC, Dyes::letter,
			ByteBufCodecs.optional(DyeColor.STREAM_CODEC), Dyes::background,
			Dyes::new
	);

	public boolean dyed() {
		return background.isPresent();
	}

	/** Tint for the glyph texture, opaque ARGB. */
	public int letterTint() {
		return 0xFF000000 | letter.getTextureDiffuseColor();
	}

	/** Tint for the white background texture; white when there is no background dye. */
	public int backgroundTint() {
		return background.map(dye -> 0xFF000000 | dye.getTextureDiffuseColor()).orElse(0xFFFFFFFF);
	}
}
