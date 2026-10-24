package de.knito.typestones;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

import de.knito.typestones.block.Dyes;

public class ModComponents {
	public static final DataComponentType<Dyes> DYES = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			Typestones.id("dyes"),
			DataComponentType.<Dyes>builder()
					.persistent(Dyes.CODEC)
					.networkSynchronized(Dyes.STREAM_CODEC)
					.build()
	);

	public static void initialize() {
	}
}
