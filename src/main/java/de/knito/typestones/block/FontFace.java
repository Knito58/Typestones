package de.knito.typestones.block;

/**
 * A font face of Typestones. Each face is its own block, with glyph textures generated from the file
 * of the same name in fonts/ (see GlyphGen).
 */
public enum FontFace {
	CLASSIC("classic", "typestone");

	/** Folder of the generated glyph textures and models. */
	public final String folder;
	/** Name of the block and its item. */
	public final String blockName;

	FontFace(String folder, String blockName) {
		this.folder = folder;
		this.blockName = blockName;
	}
}
