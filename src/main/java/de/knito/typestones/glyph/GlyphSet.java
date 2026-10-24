package de.knito.typestones.glyph;

/**
 * The characters a Typestone can carry: the same set as Billboard Engine's font. Every font face must
 * have a texture for each of them. Only append to the end, never reorder: the position is what blocks
 * in existing worlds remember.
 */
public final class GlyphSet {
	/** Index 0 is the space, the plain block without a letter. */
	public static final String CHARS = " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ"
			+ "[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u00C4\u00D6\u00DC"
			+ "\u00E4\u00F6\u00FC\u00DF\u00B0\u00B7\u20AC";

	private GlyphSet() {
	}

	public static int size() {
		return CHARS.length();
	}

	public static char charAt(int index) {
		return CHARS.charAt(index);
	}

	/** Position of the character, or -1 when no face can show it. */
	public static int indexOf(char c) {
		return CHARS.indexOf(c);
	}

	/** Texture name of a glyph: its code point, e.g. "u0041" for A, since paths allow no capitals. */
	public static String textureName(char c) {
		return String.format("u%04x", (int) c);
	}
}
