package de.knito.typestones.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.level.block.Block;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;

import de.knito.typestones.ModBlocks;
import de.knito.typestones.ModStation;
import de.knito.typestones.Typestones;
import de.knito.typestones.block.Dyes;
import de.knito.typestones.client.mixin.ItemTintSourcesAccessor;

/**
 * Client entrypoint: colors the placed blocks and the items, and opens the Alpha Station screen.
 */
public class TypestonesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Tint 0 is the glyph, tint 1 the white background (only the dyed models use it).
		BlockColorRegistry.register((state, level, pos, tints) -> {
			Dyes dyes = level.getBlockEntityRenderData(pos) instanceof Dyes d ? d : Dyes.DEFAULT;
			tints.size(2);
			tints.set(0, dyes.letterTint());
			tints.set(1, dyes.backgroundTint());
		}, ModBlocks.TYPESTONES.values().toArray(Block[]::new));

		ItemTintSourcesAccessor.typestones$idMapper().put(Typestones.id("dye"), DyeTint.MAP_CODEC);

		MenuScreens.register(ModStation.MENU, AlphaStationScreen::new);
	}
}
