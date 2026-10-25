package de.knito.typestones;

import com.mojang.serialization.Codec;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

import de.knito.typestones.block.Dyes;
import de.knito.typestones.pencil.PencilLine;

public class ModComponents {
	/** Hash of the letter (see Letters); absent on a plain block. */
	public static final DataComponentType<String> LETTER = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			Typestones.id("letter"),
			DataComponentType.<String>builder()
					.persistent(Codec.STRING)
					.networkSynchronized(ByteBufCodecs.STRING_UTF8)
					.build()
	);

	/** Absent for the default: black letter, no background. */
	public static final DataComponentType<Dyes> DYES = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			Typestones.id("dyes"),
			DataComponentType.<Dyes>builder()
					.persistent(Dyes.CODEC)
					.networkSynchronized(Dyes.STREAM_CODEC)
					.build()
	);

	/** The line a pencil writes; absent on an empty pencil. */
	public static final DataComponentType<PencilLine> PENCIL_LINE = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			Typestones.id("pencil_line"),
			DataComponentType.<PencilLine>builder()
					.persistent(PencilLine.CODEC)
					.networkSynchronized(PencilLine.STREAM_CODEC)
					.build()
	);

	public static void initialize() {
	}
}
