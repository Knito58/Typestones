package de.knito.typestones.typewriter;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import de.knito.typestones.letter.LetterInfo;

/**
 * How the Typewriter bakes letters. The player's game bakes them with a font installed on its computer;
 * "classic" is the built-in face and needs no baking.
 *
 * @param font     font family name, e.g. "DejaVu Sans", or {@link #CLASSIC}
 * @param size     letter size in pixels: 16, 32 or 64
 * @param scale    font size in percent; at 100 the font's ascent and descent fill the cell height
 * @param baseline moves the letters up (positive) or down, in percent of the cell height
 */
public record FontSettings(String font, int size, int scale, int baseline) {
	public static final String CLASSIC = "classic";
	public static final int[] SIZES = {16, 32, 64};
	public static final int MIN_SCALE = 25;
	public static final int MAX_SCALE = 200;
	public static final int MAX_BASELINE = 50;

	public static final FontSettings DEFAULT = new FontSettings(CLASSIC, 32, 100, 0);

	public static final StreamCodec<ByteBuf, FontSettings> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(LetterInfo.MAX_FONT * 4), FontSettings::font,
			ByteBufCodecs.VAR_INT, FontSettings::size,
			ByteBufCodecs.VAR_INT, FontSettings::scale,
			ByteBufCodecs.VAR_INT, FontSettings::baseline,
			FontSettings::new
	);

	/** Brings values from a save or a packet into range. */
	public FontSettings {
		font = font.isBlank() ? CLASSIC : font.length() > LetterInfo.MAX_FONT ? font.substring(0, LetterInfo.MAX_FONT) : font;
		size = size <= 16 ? 16 : size <= 32 ? 32 : 64;
		scale = Math.clamp(scale, MIN_SCALE, MAX_SCALE);
		baseline = Math.clamp(baseline, -MAX_BASELINE, MAX_BASELINE);
	}

	public boolean isClassic() {
		return font.equals(CLASSIC);
	}

	public FontSettings withFont(String font) {
		return new FontSettings(font, size, scale, baseline);
	}

	public FontSettings withSize(int size) {
		return new FontSettings(font, size, scale, baseline);
	}

	public FontSettings withScale(int scale) {
		return new FontSettings(font, size, scale, baseline);
	}

	public FontSettings withBaseline(int baseline) {
		return new FontSettings(font, size, scale, baseline);
	}
}
