package de.knito.typestones.letter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What the library knows about a letter besides its pixels. The Unicode name and the script are not
 * stored: Java derives them from the text (see {@link #unicodeName()}).
 *
 * @param text   the character(s) the letter stands for, e.g. "A" or "中"
 * @param font   the font it was baked from, e.g. "Noto Sans CJK TC"; "classic" for the built-in letters
 * @param locale the baker's game language when baking, e.g. "zh_tw"; empty for the built-in letters
 * @param baker  the name of the player who baked it; empty for the built-in letters
 * @param baked  the day it entered the library, e.g. "2026-10-24"; empty for the built-in letters
 */
public record LetterInfo(String text, String font, String locale, String baker, String baked) {
	public static final int MAX_TEXT = 16;
	public static final int MAX_FONT = 64;
	public static final int MAX_LOCALE = 16;
	public static final int MAX_BAKER = 32;

	public static final MapCodec<LetterInfo> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.STRING.fieldOf("text").forGetter(LetterInfo::text),
			Codec.STRING.optionalFieldOf("font", "").forGetter(LetterInfo::font),
			Codec.STRING.optionalFieldOf("locale", "").forGetter(LetterInfo::locale),
			Codec.STRING.optionalFieldOf("baker", "").forGetter(LetterInfo::baker),
			Codec.STRING.optionalFieldOf("baked", "").forGetter(LetterInfo::baked)
	).apply(instance, LetterInfo::new));

	public static final StreamCodec<ByteBuf, LetterInfo> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(MAX_TEXT * 4), LetterInfo::text,
			ByteBufCodecs.stringUtf8(MAX_FONT * 4), LetterInfo::font,
			ByteBufCodecs.stringUtf8(MAX_LOCALE * 4), LetterInfo::locale,
			ByteBufCodecs.stringUtf8(MAX_BAKER * 4), LetterInfo::baker,
			ByteBufCodecs.stringUtf8(16), LetterInfo::baked,
			LetterInfo::new
	);

	public static LetterInfo classic(String text) {
		return new LetterInfo(text, "classic", "", "", "");
	}

	/** E.g. "LATIN CAPITAL LETTER A"; names joined by " + " when the text has several characters. */
	public String unicodeName() {
		StringBuilder name = new StringBuilder();
		text.codePoints().forEach(c -> {
			if (!name.isEmpty()) {
				name.append(" + ");
			}
			String n = Character.getName(c);
			name.append(n != null ? n : String.format("U+%04X", c));
		});
		return name.toString();
	}

	/** E.g. "LATIN" or "HAN", from the first character; "UNKNOWN" for an empty text. */
	public String script() {
		return text.isEmpty() ? "UNKNOWN" : Character.UnicodeScript.of(text.codePointAt(0)).name();
	}

	/** True when the text could be a typed character: not empty, not too long, no control characters. */
	public static boolean isValidText(String text) {
		return !text.isEmpty() && text.length() <= MAX_TEXT && text.codePoints().noneMatch(Character::isISOControl);
	}
}
