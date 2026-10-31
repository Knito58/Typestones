package de.knito.typestones;

import net.minecraft.resources.Identifier;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.knito.typestones.script.billboard.BillboardSupport;

/**
 * Common entrypoint (runs on client and server): registers the components, the Typestone blocks, the Typewriter, the pencil, the letter library and the pictures.
 */
public class Typestones implements ModInitializer {
	public static final String MOD_ID = "typestones";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		TypestonesConfig.load();
		ModComponents.initialize();
		ModBlocks.initialize();
		ModTypewriter.initialize();
		ModPencil.initialize();
		ModLetters.initialize();
		ModPictures.initialize();
		// Soft dependencies: their classes are only touched when the mod is there.
		if (FabricLoader.getInstance().isModLoaded("billboard_api")) {
			BillboardSupport.register();
		}
	}
}
