package de.knito.typestones.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu's "Configure" button opens the settings. Only Mod Menu loads this class. */
public class ModMenuEntry implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return TypestonesConfigScreen::new;
	}
}
