package de.knito.typestones;

import net.minecraft.resources.Identifier;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint (runs on client and server): registers the components, the Typestone blocks, the Typewriter, the pencil and the letter library.
 */
public class Typestones implements ModInitializer {
	public static final String MOD_ID = "typestones";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModComponents.initialize();
		ModBlocks.initialize();
		ModTypewriter.initialize();
		ModPencil.initialize();
		ModLetters.initialize();
	}
}
