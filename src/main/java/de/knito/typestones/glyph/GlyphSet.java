package de.knito.typestones.glyph;

/**
 * The characters of the built-in classic letters: the same set as Billboard Engine's font. GlyphGen
 * draws one letter for each, and the Typewriter's "All" preset types them.
 */
public final class GlyphSet {
	/** Starts with the space, which is the plain block without a letter. */
	public static final String CHARS = " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ"
			+ "[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~ÄÖÜ"
			+ "äöüß°·€";

	private GlyphSet() {
	}

	public static int size() {
		return CHARS.length();
	}

	public static char charAt(int index) {
		return CHARS.charAt(index);
	}
}
