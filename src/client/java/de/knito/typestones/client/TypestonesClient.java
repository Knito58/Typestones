package de.knito.typestones.client;

import net.minecraft.client.gui.screens.MenuScreens;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.CustomUnbakedBlockStateModel;

import de.knito.typestones.ModTypewriter;
import de.knito.typestones.Typestones;
import de.knito.typestones.client.mixin.ItemModelsAccessor;
import de.knito.typestones.client.mixin.ItemTintSourcesAccessor;
import de.knito.typestones.client.model.PencilTint;
import de.knito.typestones.client.model.TypestoneBlockModel;
import de.knito.typestones.client.model.TypestoneItemModel;
import de.knito.typestones.client.network.LetterSync;

/**
 * Client entrypoint: registers the models that draw the letters, fetches unknown letters from the
 * server, and opens the Typewriter screen.
 */
public class TypestonesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		CustomUnbakedBlockStateModel.register(Typestones.id("typestone"), TypestoneBlockModel.Unbaked.CODEC);
		ItemModelsAccessor.typestones$idMapper().put(Typestones.id("typestone"), TypestoneItemModel.Unbaked.CODEC);
		ItemTintSourcesAccessor.typestones$idMapper().put(Typestones.id("pencil"), PencilTint.CODEC);

		LetterSync.initialize();

		MenuScreens.register(ModTypewriter.MENU, TypewriterScreen::new);
	}
}
