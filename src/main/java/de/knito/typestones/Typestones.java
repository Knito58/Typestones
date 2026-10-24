package de.knito.typestones;

import net.minecraft.resources.Identifier;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.knito.typestones.glyph.GlyphSet;

/**
 * Common entrypoint (runs on client and server). The blocks and the Alpha Station register here.
 */
public class Typestones implements ModInitializer {
	public static final String MOD_ID = "typestones";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		LOGGER.info("Typestones: {} glyphs", GlyphSet.size());
	}
}
