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
 * @param style    {@link #REGULAR}, {@link #BOLD}, {@link #ITALIC} or both; the same values as java.awt.Font
 * @param joined   the whole line is shaped and cut into stones, so letters that belong together (Arabic,
 *                 punctuation next to a word) stay together; otherwise each character gets its own stone
 * @param align    where a joined line lies within width stones: {@link #LEFT}, {@link #CENTER} or {@link #RIGHT}
 * @param width    the width of the line in stones that align refers to; 0 for exactly as wide as the line
 */
public record FontSettings(String font, int size, int scale, int baseline, int style, boolean joined, int align, int width) {
	public static final String CLASSIC = "classic";
	public static final int[] SIZES = {16, 32, 64};
	public static final int MIN_SCALE = 25;
	public static final int MAX_SCALE = 200;
	public static final int MAX_BASELINE = 50;
	public static final int REGULAR = 0;
	public static final int BOLD = 1;
	public static final int ITALIC = 2;
	public static final int LEFT = 0;
	public static final int CENTER = 1;
	public static final int RIGHT = 2;
	public static final int MAX_WIDTH = 128;
	private static final String[] STYLE_NAMES = {"", " Bold", " Italic", " Bold Italic"};

	public static final FontSettings DEFAULT = new FontSettings(CLASSIC, 64, 100, 0, REGULAR, false, LEFT, 0);

	public static final StreamCodec<ByteBuf, FontSettings> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(LetterInfo.MAX_FONT * 4), FontSettings::font,
			ByteBufCodecs.VAR_INT, FontSettings::size,
			ByteBufCodecs.VAR_INT, FontSettings::scale,
			ByteBufCodecs.VAR_INT, FontSettings::baseline,
			ByteBufCodecs.VAR_INT, FontSettings::style,
			ByteBufCodecs.BOOL, FontSettings::joined,
			ByteBufCodecs.VAR_INT, FontSettings::align,
			ByteBufCodecs.VAR_INT, FontSettings::width,
			FontSettings::new
	);

	/** Brings values from a save or a packet into range. */
	public FontSettings {
		font = font.isBlank() ? CLASSIC : font.length() > LetterInfo.MAX_FONT ? font.substring(0, LetterInfo.MAX_FONT) : font;
		size = size <= 16 ? 16 : size <= 32 ? 32 : 64;
		scale = Math.clamp(scale, MIN_SCALE, MAX_SCALE);
		baseline = Math.clamp(baseline, -MAX_BASELINE, MAX_BASELINE);
		style = Math.clamp(style, REGULAR, BOLD | ITALIC);
		align = Math.clamp(align, LEFT, RIGHT);
		width = Math.clamp(width, 0, MAX_WIDTH);
	}

	public boolean isClassic() {
		return font.equals(CLASSIC);
	}

	/** Joined lines need a real font; the classic face always sets one stone per character. */
	public boolean isJoined() {
		return joined && !isClassic();
	}

	/** The font as the letter library names it, e.g. "Ubuntu Sans Bold". */
	public String fontName() {
		String name = font + STYLE_NAMES[style];
		return name.length() > LetterInfo.MAX_FONT ? name.substring(0, LetterInfo.MAX_FONT) : name;
	}

	public FontSettings withFont(String font) {
		return new FontSettings(font, size, scale, baseline, style, joined, align, width);
	}

	public FontSettings withSize(int size) {
		return new FontSettings(font, size, scale, baseline, style, joined, align, width);
	}

	public FontSettings withScale(int scale) {
		return new FontSettings(font, size, scale, baseline, style, joined, align, width);
	}

	public FontSettings withBaseline(int baseline) {
		return new FontSettings(font, size, scale, baseline, style, joined, align, width);
	}

	public FontSettings withStyle(int style) {
		return new FontSettings(font, size, scale, baseline, style, joined, align, width);
	}

	public FontSettings withJoined(boolean joined) {
		return new FontSettings(font, size, scale, baseline, style, joined, align, width);
	}

	public FontSettings withAlign(int align) {
		return new FontSettings(font, size, scale, baseline, style, joined, align, width);
	}

	public FontSettings withWidth(int width) {
		return new FontSettings(font, size, scale, baseline, style, joined, align, width);
	}
}
