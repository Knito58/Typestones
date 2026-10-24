package de.knito.typestones.typewriter;

import net.minecraft.network.chat.Component;

/** What the Typewriter is doing, shown in its screen. */
public enum TypewriterStatus {
	IDLE(0xFF404040),
	RUNNING(0xFF207020),
	DONE(0xFF207020),
	NO_TEXT(0xFF802020),
	NO_LETTER_DYE(0xFF802020),
	NO_OUTPUT(0xFF802020),
	OUTPUT_FULL(0xFF806000),
	NEEDS_STONE(0xFF806000),
	NEEDS_LETTER_DYE(0xFF806000),
	NEEDS_BACKGROUND_DYE(0xFF806000);

	public final int color;

	TypewriterStatus(int color) {
		this.color = color;
	}

	public Component describe() {
		return Component.translatable("gui.typestones.status." + name().toLowerCase());
	}

	public static TypewriterStatus of(int ordinal) {
		return values()[Math.clamp(ordinal, 0, values().length - 1)];
	}
}
