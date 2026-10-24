package de.knito.typestones.typewriter;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import de.knito.typestones.Typestones;

/** A button press in the Typewriter screen. Always carries the current text and repeat count. */
public record TypewriterActionPayload(Action action, String text, int repeat) implements CustomPacketPayload {
	public enum Action {
		SAVE, START, STOP
	}

	public static final Type<TypewriterActionPayload> TYPE = new Type<>(Typestones.id("typewriter_action"));

	public static final StreamCodec<ByteBuf, TypewriterActionPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.idMapper(i -> Action.values()[Math.clamp(i, 0, Action.values().length - 1)], Action::ordinal), TypewriterActionPayload::action,
			ByteBufCodecs.stringUtf8(TypewriterBlockEntity.MAX_TEXT_LENGTH * 4), TypewriterActionPayload::text,
			ByteBufCodecs.VAR_INT, TypewriterActionPayload::repeat,
			TypewriterActionPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
