package de.knito.typestones.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.CustomUnbakedBlockStateModel;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import de.knito.typestones.ModBlocks;
import de.knito.typestones.ModTypewriter;
import de.knito.typestones.Typestones;
import de.knito.typestones.block.TypestoneBlockEntity;
import de.knito.typestones.client.mixin.ItemModelsAccessor;
import de.knito.typestones.client.mixin.ItemTintSourcesAccessor;
import de.knito.typestones.client.model.PencilTint;
import de.knito.typestones.client.model.TypestoneBlockModel;
import de.knito.typestones.client.model.TypestoneItemModel;
import de.knito.typestones.client.network.LetterSync;
import de.knito.typestones.client.picture.PictureStoneRenderer;
import de.knito.typestones.client.picture.PictureSync;
import de.knito.typestones.typewriter.TypewriterActionPayload;
import de.knito.typestones.typewriter.TypewriterMenu;

/**
 * Client entrypoint: registers the models that draw the letters and the renderer of pictures, fetches
 * unknown letters and pictures from the server, and opens the Typewriter screen.
 */
public class TypestonesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		CustomUnbakedBlockStateModel.register(Typestones.id("typestone"), TypestoneBlockModel.Unbaked.CODEC);
		ItemModelsAccessor.typestones$idMapper().put(Typestones.id("typestone"), TypestoneItemModel.Unbaked.CODEC);
		ItemTintSourcesAccessor.typestones$idMapper().put(Typestones.id("pencil"), PencilTint.CODEC);

		LetterSync.initialize();
		PictureSync.initialize();
		BlockEntityRenderers.<TypestoneBlockEntity, PictureStoneRenderer.State>register(ModBlocks.TYPESTONE_ENTITY,
				context -> new PictureStoneRenderer());

		MenuScreens.register(ModTypewriter.MENU, TypewriterScreen::new);
		ClientPlayNetworking.registerGlobalReceiver(TypewriterActionPayload.PencilPictures.TYPE, (payload, context) -> {
			if (context.player().containerMenu instanceof TypewriterMenu menu) {
				menu.setPencilPictures(payload.hashes());
			}
		});
	}
}
